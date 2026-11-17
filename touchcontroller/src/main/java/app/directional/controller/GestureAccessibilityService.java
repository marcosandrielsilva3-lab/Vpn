package app.directional.controller;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import java.util.ArrayDeque;

public class GestureAccessibilityService extends AccessibilityService {
    private static volatile GestureAccessibilityService instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<GestureRequest> queue = new ArrayDeque<>();
    private GestureRequest activeRequest;
    private long lastAcceptedAt;
    private String lastDirection = "";

    public static boolean isRunning() {
        return instance != null;
    }

    public static boolean sendSwipe(String direction, int sx, int sy, int ex, int ey, int durationMs) {
        GestureAccessibilityService service = instance;
        if (service == null) return false;

        GestureRequest request = new GestureRequest(direction, sx, sy, ex, ey,
            Math.max(45, Math.min(75, durationMs)));
        service.mainHandler.post(() -> service.enqueue(request));
        return true;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Gesture-only service. It intentionally does not inspect screen content or event data.
    }

    private void enqueue(GestureRequest request) {
        long now = SystemClock.uptimeMillis();

        // Suppress accidental duplicate ACTION_DOWN deliveries for the same arrow.
        if (request.direction.equals(lastDirection) && now - lastAcceptedAt < 180) {
            return;
        }
        lastDirection = request.direction;
        lastAcceptedAt = now;

        // Keep at most two waiting gestures so stale taps cannot replay long after the user taps.
        while (queue.size() >= 2) {
            queue.removeFirst();
        }
        queue.addLast(request);
        dispatchNext();
    }

    private void dispatchNext() {
        if (activeRequest != null || queue.isEmpty()) return;

        GestureRequest request = queue.removeFirst();
        activeRequest = request;

        Path path = new Path();
        path.moveTo(request.sx, request.sy);
        path.lineTo(request.ex, request.ey);

        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 0, request.durationMs))
            .build();

        boolean accepted;
        try {
            accepted = dispatchGesture(gesture, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription description) {
                    finishRequest(request);
                }

                @Override
                public void onCancelled(GestureDescription description) {
                    // Do not replay a cancelled gesture: replaying can cause repeated jumps.
                    finishRequest(request);
                }
            }, mainHandler);
        } catch (Throwable ignored) {
            accepted = false;
        }

        if (!accepted) {
            finishRequest(request);
        }
    }

    private void finishRequest(GestureRequest request) {
        if (activeRequest != request) return;
        activeRequest = null;
        dispatchNext();
    }

    @Override
    public void onInterrupt() {
        mainHandler.post(() -> {
            queue.clear();
            activeRequest = null;
        });
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        mainHandler.post(() -> {
            queue.clear();
            activeRequest = null;
        });
        super.onDestroy();
    }

    private static final class GestureRequest {
        final String direction;
        final int sx, sy, ex, ey, durationMs;

        GestureRequest(String direction, int sx, int sy, int ex, int ey, int durationMs) {
            this.direction = direction;
            this.sx = sx;
            this.sy = sy;
            this.ex = ex;
            this.ey = ey;
            this.durationMs = durationMs;
        }
    }
}
