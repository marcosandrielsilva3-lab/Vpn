package app.directional.controller;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.ArrayDeque;

public class GestureAccessibilityService extends AccessibilityService {
    private static volatile GestureAccessibilityService instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<GestureRequest> queue = new ArrayDeque<>();
    private boolean dispatching = false;

    public static boolean isRunning() {
        return instance != null;
    }

    public static boolean sendSwipe(int sx, int sy, int ex, int ey, int durationMs) {
        GestureAccessibilityService service = instance;
        if (service == null) return false;
        GestureRequest request = new GestureRequest(sx, sy, ex, ey,
            Math.max(35, Math.min(90, durationMs)));
        service.mainHandler.post(() -> {
            service.queue.addLast(request);
            service.dispatchNext();
        });
        return true;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    private void dispatchNext() {
        if (dispatching || queue.isEmpty()) return;

        GestureRequest request = queue.peekFirst();
        Path path = new Path();
        path.moveTo(request.sx, request.sy);
        path.lineTo(request.ex, request.ey);

        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 0, request.durationMs))
            .build();

        dispatching = true;
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription description) {
                finishCurrent();
            }

            @Override
            public void onCancelled(GestureDescription description) {
                finishCurrent();
            }
        }, mainHandler);

        if (!accepted) {
            dispatching = false;
            request.retries++;
            if (request.retries <= 2) {
                mainHandler.postDelayed(this::dispatchNext, 24);
            } else {
                queue.pollFirst();
                Toast.makeText(this, "O Android recusou o gesto. Confira a Acessibilidade.", Toast.LENGTH_SHORT).show();
                dispatchNext();
            }
        }
    }

    private void finishCurrent() {
        if (!queue.isEmpty()) queue.pollFirst();
        dispatching = false;
        dispatchNext();
    }

    @Override
    public void onInterrupt() {
        mainHandler.post(() -> {
            queue.clear();
            dispatching = false;
        });
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        mainHandler.post(() -> {
            queue.clear();
            dispatching = false;
        });
        super.onDestroy();
    }

    private static final class GestureRequest {
        final int sx, sy, ex, ey, durationMs;
        int retries;
        GestureRequest(int sx, int sy, int ex, int ey, int durationMs) {
            this.sx = sx; this.sy = sy; this.ex = ex; this.ey = ey;
            this.durationMs = durationMs;
        }
    }
}
