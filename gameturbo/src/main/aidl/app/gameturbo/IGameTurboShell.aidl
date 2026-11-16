package app.gameturbo;

interface IGameTurboShell {
    void destroy() = 16777114;
    String trimAllCaches() = 1;
    String getDisplaySize() = 2;
    String applyDisplayScale(float scale) = 3;
    String restoreDisplaySize(String originalOutput) = 4;
    String getGameOverlayConfig(String packageName) = 5;
    String applyGameDownscale(String packageName, float scale) = 6;
    String restoreGameDownscale(String packageName, String originalConfig) = 7;
}