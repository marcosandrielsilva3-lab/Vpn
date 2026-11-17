package app.directional.controller;
interface IGestureShell {
    String ping() = 1;
    String swipe(int startX, int startY, int endX, int endY, int durationMs) = 2;
    void destroy() = 16777114;
}