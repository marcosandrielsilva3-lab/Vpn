package app.gameturbo;

import android.os.Build;
import android.os.StatFs;
import android.os.RemoteException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GameTurboShellService extends IGameTurboShell.Stub {
    @Override
    public void destroy() {
        System.exit(0);
    }

    private static final String[] ALLOWED_GAMES = {
        "com.roblox.client",
        "com.dts.freefireth",
        "com.dts.freefiremax"
    };

    @Override
    public String trimAllCaches() throws RemoteException {
        long before = availableBytes();
        CommandResult result = run("pm trim-caches 999G internal");
        long after = availableBytes();
        if (result.exitCode != 0) {
            return "EXIT:" + result.exitCode + "\n" + result.output;
        }
        return "EXIT:0\nLimpeza solicitada ao Package Manager.\nEspaço livre antes: " +
            formatBytes(before) + "\nEspaço livre depois: " + formatBytes(after) +
            "\nO Android remove somente arquivos de cache descartáveis; o comando pode não esvaziar cada cache se o sistema não o considerar seguro.";
    }

    @Override
    public String getDisplaySize() throws RemoteException {
        CommandResult result = run("wm size");
        return "EXIT:" + result.exitCode + "\n" + result.output;
    }

    @Override
    public String applyDisplayScale(float scale) throws RemoteException {
        if (!isAllowedScale(scale)) return "EXIT:2\nEscala inválida.";
        CommandResult query = run("wm size");
        Matcher matcher = Pattern.compile("Physical size:\\s*(\\d+)x(\\d+)").matcher(query.output);
        if (query.exitCode != 0 || !matcher.find()) {
            return "EXIT:2\nNão foi possível identificar a resolução física.\n" + query.output;
        }
        int width = Integer.parseInt(matcher.group(1));
        int height = Integer.parseInt(matcher.group(2));
        int scaledWidth = even((int) Math.round(width * scale));
        int scaledHeight = even((int) Math.round(height * scale));
        CommandResult apply = run("wm size " + scaledWidth + "x" + scaledHeight);
        if (apply.exitCode != 0) {
            return "EXIT:" + apply.exitCode + "\nFalha ao alterar a resolução.\n" + apply.output;
        }
        CommandResult verify = run("wm size");
        Matcher confirmed = Pattern.compile("Override size:\\s*(\\d+)x(\\d+)").matcher(verify.output);
        if (verify.exitCode != 0 || !confirmed.find() ||
            Integer.parseInt(confirmed.group(1)) != scaledWidth ||
            Integer.parseInt(confirmed.group(2)) != scaledHeight) {
            return "EXIT:1\nO Android não confirmou a nova resolução.\n" + verify.output;
        }
        return "EXIT:0\nResolução aplicada e confirmada: " +
            scaledWidth + "x" + scaledHeight + "\n" + verify.output;
    }

    @Override
    public String restoreDisplaySize(String originalOutput) throws RemoteException {
        if (originalOutput == null) originalOutput = "";
        Matcher override = Pattern.compile("Override size:\\s*(\\d+)x(\\d+)").matcher(originalOutput);
        String command = override.find()
            ? "wm size " + override.group(1) + "x" + override.group(2)
            : "wm size reset";
        CommandResult result = run(command);
        return "EXIT:" + result.exitCode + "\n" + result.output;
    }

    @Override
    public String getGameOverlayConfig(String packageName) throws RemoteException {
        if (!isAllowedGame(packageName)) return "EXIT:2\nJogo não permitido.";
        CommandResult result = run("device_config get game_overlay " + packageName);
        return "EXIT:" + result.exitCode + "\n" + result.output.trim();
    }

    @Override
    public String applyGameDownscale(String packageName, float scale) throws RemoteException {
        if (Build.VERSION.SDK_INT < 33) {
            return "EXIT:2\nA intervenção de resolução por jogo exige Android 13 ou superior.";
        }
        if (!isAllowedGame(packageName) || !isAllowedScale(scale)) {
            return "EXIT:2\nJogo ou escala não permitidos.";
        }
        String value = "mode=2,downscaleFactor=" + String.format(java.util.Locale.US, "%.2f", scale);
        String command = "device_config put game_overlay " + packageName + " '" + value +
            "' && cmd game mode performance " + packageName;
        CommandResult result = run(command);
        return "EXIT:" + result.exitCode + "\nIntervenção solicitada para " + packageName +
            " em " + Math.round(scale * 100) + "%.\n" + result.output +
            "\nFeche e abra o jogo novamente. O fabricante ou o próprio jogo pode não oferecer suporte à intervenção.";
    }

    @Override
    public String restoreGameDownscale(String packageName, String originalConfig) throws RemoteException {
        if (!isAllowedGame(packageName)) return "EXIT:2\nJogo não permitido.";
        String config = originalConfig == null ? "__NONE__" : originalConfig;
        String command;
        if ("__NONE__".equals(config) || "null".equals(config) || config.isEmpty()) {
            command = "device_config delete game_overlay " + packageName;
        } else {
            command = "device_config put game_overlay " + packageName + " " + shellQuote(config);
        }
        command += " && cmd game mode standard " + packageName;
        CommandResult result = run(command);
        return "EXIT:" + result.exitCode + "\nConfiguração anterior restaurada para " +
            packageName + ".\n" + result.output;
    }

    private boolean isAllowedGame(String packageName) {
        if (packageName == null) return false;
        for (String allowed : ALLOWED_GAMES) {
            if (allowed.equals(packageName)) return true;
        }
        return false;
    }

    private boolean isAllowedScale(float scale) {
        return Math.abs(scale - 0.60f) < 0.001f ||
            Math.abs(scale - 0.70f) < 0.001f ||
            Math.abs(scale - 0.80f) < 0.001f;
    }

    private int even(int value) {
        return Math.max(2, value - (value % 2));
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private CommandResult run(String command) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command + " 2>&1"});
            InputStream input = process.getInputStream();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (output.size() < 8192) output.write(buffer, 0, Math.min(read, 8192 - output.size()));
            }
            int exit = process.waitFor();
            return new CommandResult(exit, output.toString("UTF-8").trim());
        } catch (Throwable error) {
            if (process != null) process.destroy();
            return new CommandResult(1, error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private long availableBytes() {
        try {
            return new StatFs("/data").getAvailableBytes();
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) return "indisponível";
        return String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private static final class CommandResult {
        final int exitCode;
        final String output;
        CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
        }
    }
}