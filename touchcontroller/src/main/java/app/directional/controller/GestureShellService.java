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

    @Override
    public String ping() throws RemoteException {
        try {
            ensureInjector();
            return "EXIT:0\nDirect touch injector ready";
        } catch (Throwable e) {
            return "EXIT:0\nDirect injector unavailable; shell input fallback enabled: " + e.getMessage();
        }
    }

    @Override
    public String swipe(int sx, int sy, int ex, int ey, int durationMs) throws RemoteException {
        if (!valid(sx, sy) || !valid(ex, ey) || durationMs < 20 || durationMs > 300)
            return "EXIT:2\nInvalid gesture coordinates or duration.";
        try {
            ensureInjector();
            injectSwipe(sx, sy, ex, ey, durationMs);
            return "EXIT:0\nDIRECT";
        } catch (Throwable directError) {
            CommandResult fallback = run(new String[]{
                "/system/bin/input", "touchscreen", "swipe",
                Integer.toString(sx), Integer.toString(sy),
                Integer.toString(ex), Integer.toString(ey),
                Integer.toString(Math.max(30, Math.min(70, durationMs)))
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
        MotionEvent down = MotionEvent.obtain(
            downTime, downTime, MotionEvent.ACTION_DOWN, sx, sy, 0);
        try {
            configure(down);
            invokeInject(down);

            // Several small MOVE samples are more reliable for games than one large jump.
            final int samples = 4;
            int interval = Math.max(4, durationMs / samples);
            for (int i = 1; i <= samples; i++) {
                Thread.sleep(interval);
                float fraction = i / (float) samples;
                float x = sx + (ex - sx) * fraction;
                float y = sy + (ey - sy) * fraction;
                long eventTime = SystemClock.uptimeMillis();
                MotionEvent move = MotionEvent.obtain(
                    downTime, eventTime, MotionEvent.ACTION_MOVE, x, y, 0);
                try {
                    configure(move);
                    invokeInject(move);
                } finally {
                    move.recycle();
                }
            }

            long upTime = SystemClock.uptimeMillis();
            MotionEvent up = MotionEvent.obtain(
                downTime, upTime, MotionEvent.ACTION_UP, ex, ey, 0);
            try {
                configure(up);
                invokeInject(up);
            } finally {
                up.recycle();
            }
        } finally {
            down.recycle();
        }
    }

    private static void configure(MotionEvent event) {
        try {
            if (setSourceMethod == null) {
                setSourceMethod = InputEvent.class.getDeclaredMethod("setSource", int.class);
                setSourceMethod.setAccessible(true);
            }
            setSourceMethod.invoke(event, InputDevice.SOURCE_TOUCHSCREEN);
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                if (setDisplayIdMethod == null) {
                    setDisplayIdMethod = InputEvent.class.getDeclaredMethod("setDisplayId", int.class);
                    setDisplayIdMethod.setAccessible(true);
                }
                setDisplayIdMethod.invoke(event, 0);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot configure touchscreen event", e);
        }
    }

    private static void invokeInject(InputEvent event) throws Exception {
        Object accepted = injectMethod.invoke(inputManager, event, 0);
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

    @Override
    public void destroy() {
        System.exit(0);
    }

    private static final class CommandResult {
        final int exitCode;
        final String output;
        CommandResult(int code, String output) {
            this.exitCode = code;
            this.output = output == null ? "" : output;
        }
    }
}
