package app.directional.controller;

import android.os.RemoteException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class GestureShellService extends IGestureShell.Stub {
    @Override public String ping() throws RemoteException {
        CommandResult r = run(new String[]{"/system/bin/id"});
        return "EXIT:" + r.exitCode + "\n" + r.output;
    }

    @Override public String swipe(int sx, int sy, int ex, int ey, int durationMs) throws RemoteException {
        if (!valid(sx, sy) || !valid(ex, ey) || durationMs < 35 || durationMs > 300)
            return "EXIT:2\nParâmetros do gesto inválidos.";
        CommandResult r = run(new String[]{
            "/system/bin/input", "swipe",
            Integer.toString(sx), Integer.toString(sy),
            Integer.toString(ex), Integer.toString(ey),
            Integer.toString(durationMs)
        });
        return "EXIT:" + r.exitCode + "\n" + r.output;
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