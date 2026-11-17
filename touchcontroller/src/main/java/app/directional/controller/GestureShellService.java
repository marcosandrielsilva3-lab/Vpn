package app.directional.controller;

import android.os.RemoteException;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;

public class GestureShellService extends IGestureShell.Stub {
    private static Object inputManager;
    private static Method injectMethod;
    private static Method setSourceMethod;
    private static Method setDisplayIdMethod;

    @Override public String ping() throws RemoteException {
        try {
            ensureInjector();
            return "EXIT:0\nDirect touch injector ready";
        } catch (Throwable e) {
            return "EXIT:0\nDirect injector unavailable; shell input fallback enabled: " + e.getMessage();
        }
    }

    @Override public String swipe(int sx, int sy, int ex, int ey, int durationMs) throws RemoteException {
        if (!valid(sx, sy) || !valid(ex, ey) || durationMs < 20 || durationMs > 300)
            return "EXIT:2\nInvalid gesture coordinates or duration.";
        try {
            ensureInjector();
            injectSwipe(sx, sy, ex, ey, durationMs);
            return "EXIT:0\nDIRECT";
        } catch (Throwable directError) {
            // Keep a compatibility fallback for Android versions that block hidden InputManager APIs.
            CommandResult fallback = run(new String[]{
                "/system/bin/input", "touchscreen", "swipe",
                Integer.toString(sx), Integer.toString(sy),
                Integer.toString(ex), Integer.toString(ey),
                Integer.toString(Math.max(35, Math.min(90, durationMs)))
            });
            if (fallback.exitCode == 0) return "EXIT:0\nFALLBACK";
            return "EXIT:" + fallback.exitCode + "\nDirect: " + directError.getClass().getSimpleName()
                + ": " + directError.getMessage() + "\n" + fallback.output;
        }
    }

    private static synchronized void ensureInjector() throws Exception {
        if (inputManager != null && injectMethod != null) return;
        Class<?> cls = Class.forName("android.hardware.input.InputManager");
        Method getter = cls.getDeclaredMethod("getInstance");
        getter.setAccessible(true);
        inputManager = getter.invoke(null);
        injectMethod = cls.getDeclaredMethod("injectInputEvent", InputEvent.class, int.class);
        injectMethod.setAccessible(true);
    }

    private static void injectSwipe(int sx, int sy, int ex, int ey, int durationMs) throws Exception {
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, sx, sy, 0);
        MotionEvent move = null;
        MotionEvent up = null;
        try {
            configure(down);
            invokeInject(down);
            // Start the gesture immediately; this short interval is only the swipe's travel time.
            Thread.sleep(Math.max(20, Math.min(45, durationMs)));
            long moveTime = SystemClock.uptimeMillis();
            move = MotionEvent.obtain(downTime, moveTime, MotionEvent.ACTION_MOVE, ex, ey, 0);
            configure(move);
            invokeInject(move);
            long upTime = SystemClock.uptimeMillis();
            up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, ex, ey, 0);
            configure(up);
            invokeInject(up);
        } finally {
            down.recycle();
            if (move != null) move.recycle();
            if (up != null) up.recycle();
        }
    }

    private static void configure(MotionEvent event) {
        try {
            if (setSourceMethod == null) {
                setSourceMethod = android.view.InputEvent.class.getDeclaredMethod("setSource", int.class);
                setSourceMethod.setAccessible(true);
            }
            setSourceMethod.invoke(event, InputDevice.SOURCE_TOUCHSCREEN);
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                if (setDisplayIdMethod == null) {
                    setDisplayIdMethod = android.view.InputEvent.class.getDeclaredMethod("setDisplayId", int.class);
                    setDisplayIdMethod.setAccessible(true);
                }
                setDisplayIdMethod.invoke(event, 0);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot configure touchscreen event", e);
        }
    }

    private static void invokeInject(InputEvent event) throws Exception {
        Object accepted = injectMethod.invoke(inputManager, event, 0 /* async */);
        if (accepted instanceof Boolean && !((Boolean) accepted))
            throw new IllegalStateException("Android rejected the touch event");
    }

    private boolean valid(int x, int y) {
        return x >= 0 && y >= 0 && x <= 20000 && y <= 20000;
    }

    private CommandResult run(String[] command) {
        Process p = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            p = builder.start();
            InputStream in = p.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[512];
            int n;
            while ((n = in.read(b)) != -1) {
                if (out.size() < 4096) out.write(b, 0, Math.min(n, 4096 - out.size()));
            }
            int code = p.waitFor();
            return new CommandResult(code, out.toString("UTF-8").trim());
        } catch (Throwable e) {
            if (p != null) p.destroy();
            return new CommandResult(1, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Override public void destroy() { System.exit(0); }

    private static final class CommandResult {
        final int exitCode;
        final String output;
        CommandResult(int c, String o) {
            exitCode = c;
            output = o == null ? "" : o;
        }
    }
}