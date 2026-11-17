package app.directional.controller;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
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
    private static final int MAX_PENDING_GESTURES = 4;
    private static final long MAX_QUEUE_AGE_MS = 450L;
    private static final int MIN_GESTURE_MS = 60;
    private static final int MAX_GESTURE_MS = 110;

    private GestureRequest activeRequest;

    public static boolean isRunning() {
        return instance != null;
    }

    public static boolean sendSwipe(String direction, int sx, int sy, int ex, int ey, int durationMs) {
        GestureAccessibilityService service = instance;
        if (service == null) return false;

        GestureRequest request = new GestureRequest(
            direction, sx, sy, ex, ey,
            Math.max(MIN_GESTURE_MS, Math.min(MAX_GESTURE_MS, durationMs)),
            SystemClock.uptimeMillis()
        );
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
        // This service only sends requested gestures; it does not inspect screen content.
    }

    private void enqueue(GestureRequest request) {
        long now = SystemClock.uptimeMillis();

        // Remove expired commands instead of replaying taps long after they were made.
        while (!queue.isEmpty() &&
            now - queue.peekFirst().createdAt > MAX_QUEUE_AGE_MS) {
            queue.removeFirst();
        }

        // Keep the most recent user inputs if the system cannot process an unusually
        // large burst. There is deliberately no same-direction debounce.
        while (queue.size() >= MAX_PENDING_GESTURES) {
            queue.removeFirst();
        }

        queue.addLast(request);
        dispatchNext();
    }

    private void dispatchNext() {
        if (activeRequest != null) return;

        long now = SystemClock.uptimeMillis();
        while (!queue.isEmpty() &&
            now - queue.peekFirst().createdAt > MAX_QUEUE_AGE_MS) {
            queue.removeFirst();
        }
        if (queue.isEmpty()) return;

        GestureRequest request = queue.removeFirst();
        activeRequest = request;

        Path path = new Path();
        path.moveTo(request.startX, request.startY);
        path.lineTo(request.endX, request.endY);

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
                    // Never replay a cancelled command; replay could cause an unexpected move.
                    finishRequest(request);
                }
            }, mainHandler);
        } catch (RuntimeException error) {
            accepted = false;
        }

        if (!accepted) finishRequest(request);
    }

    private void finishRequest(GestureRequest request) {
        if (activeRequest != request) return;
        activeRequest = null;
        mainHandler.post(this::dispatchNext);
    }

    @Override
    public void onInterrupt() {
        mainHandler.post(() -> {
            queue.clear();
            activeRequest = null;
        });
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (instance == this) instance = null;
        queue.clear();
        activeRequest = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        queue.clear();
        activeRequest = null;
        super.onDestroy();
    }

    private static final class GestureRequest {
        final String direction;
        final int startX, startY, endX, endY, durationMs;
        final long createdAt;

        GestureRequest(String direction, int startX, int startY, int endX, int endY,
                       int durationMs, long createdAt) {
            this.direction = direction;
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.durationMs = durationMs;
            this.createdAt = createdAt;
        }
    }
}
