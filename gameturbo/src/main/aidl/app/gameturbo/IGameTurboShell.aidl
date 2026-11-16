package app.gameturbo;

interface IGameTurboShell {
    void destroy() = 16777114;
    String trimAllCaches();
    String getDisplaySize();
    String applyDisplayScale(float scale);
    String restoreDisplaySize(String originalOutput);
    String getGameOverlayConfig(String packageName);
    String applyGameDownscale(String packageName, float scale);
    String restoreGameDownscale(String packageName, String originalConfig);
}