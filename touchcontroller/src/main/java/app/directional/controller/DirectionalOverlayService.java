package app.directional.controller;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class DirectionalOverlayService extends Service {
    private static final String START = "app.directional.controller.START";
    private static final String STOP = "app.directional.controller.STOP";
    private static final String CHANNEL = "directional_controls";
    private static final int NOTIFICATION_ID = 8101;

    private WindowManager wm;
    private View root;
    private WindowManager.LayoutParams params;

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (START.equals(action) || STOP.equals(action)) {
            startForeground(NOTIFICATION_ID, notification("Preparando controles."));
        }

        if (START.equals(action)) {
            if (!GestureAccessibilityService.isRunning()) {
                Toast.makeText(this, "Ative o serviço de Acessibilidade do Controle Direcional.", Toast.LENGTH_LONG).show();
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }
            showOverlay();
            updateNotification("Setas translúcidas prontas.");
        } else if (STOP.equals(action)) {
            removeOverlay();
            stopForeground(true);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void showOverlay() {
        if (root != null) return;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Permita a sobreposição nas configurações.", Toast.LENGTH_LONG).show();
            return;
        }

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(5), dp(4), dp(5), dp(5));
        // Visually translucent, but still a touchable overlay.
        panel.setBackground(background(0x55162237, 18));

        TextView drag = new TextView(this);
        drag.setText("SEGURE AQUI PARA MOVER");
        drag.setTextColor(0xFFE2EAF6);
        drag.setTextSize(10);
        drag.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        drag.setGravity(Gravity.CENTER);
        drag.setPadding(dp(5), dp(2), dp(5), dp(3));
        panel.addView(drag, new LinearLayout.LayoutParams(-1, dp(25)));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER);
        top.addView(spacer());
        top.addView(makeArrow("↑", "up"));
        top.addView(spacer());
        panel.addView(top, new LinearLayout.LayoutParams(-1, dp(94)));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        bottom.addView(makeArrow("←", "left"));
        bottom.addView(makeArrow("↓", "down"));
        bottom.addView(makeArrow("→", "right"));
        panel.addView(bottom, new LinearLayout.LayoutParams(-1, dp(94)));

        int type = Build.VERSION.SDK_INT >= 26
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.START;
        params.x = dp(8);
        params.y = dp(54);

        final float[] down = new float[2];
        final int[] original = new int[2];
        drag.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = e.getRawX();
                    down[1] = e.getRawY();
                    original[0] = params.x;
                    original[1] = params.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - down[0];
                    float dy = e.getRawY() - down[1];
                    if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) {
                        params.x = Math.max(0, original[0] + (int) dx);
                        params.y = Math.max(0, original[1] - (int) dy);
                        try { wm.updateViewLayout(root, params); } catch (Throwable ignored) {}
                    }
                    return true;
                default:
                    return true;
            }
        });

        root = panel;
        try {
            wm.addView(root, params);
        } catch (Throwable e) {
            root = null;
            params = null;
            Toast.makeText(this, "Não foi possível mostrar as setas: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private View spacer() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }

    private TextView makeArrow(String symbol, String direction) {
        TextView button = new TextView(this);
        button.setText(symbol);
        button.setTextColor(Color.WHITE);
        button.setTextSize(48);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(background(0x77344F70, 16));
        button.setSoundEffectsEnabled(false);
        button.setHapticFeedbackEnabled(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(92), dp(88));
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        button.setLayoutParams(lp);

        final boolean[] fingerDown = {false};
        final long[] lastDown = {0L};

        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    if (!fingerDown[0]) {
                        fingerDown[0] = true;
                        long now = SystemClock.uptimeMillis();
                        // One command per physical press, with a small guard against duplicate downs.
                        if (now - lastDown[0] >= 150L) {
                            lastDown[0] = now;
                            view.setAlpha(0.86f);
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                            sendGesture(direction);
                        }
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    fingerDown[0] = false;
                    view.setAlpha(1f);
                    return true;
                default:
                    // Ignore move and additional pointer events: holding a button never repeats it.
                    return true;
            }
        });
        return button;
    }

    private void sendGesture(String direction) {
        DisplayMetrics metrics = new DisplayMetrics();
        Display display = wm.getDefaultDisplay();
        display.getRealMetrics(metrics);
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;

        // Inject the swipe in the central play area, not at the overlay button's location.
        int centerX = width / 2;
        int centerY = (int) (height * 0.56f);
        int sx, sy, ex, ey;

        if ("up".equals(direction)) {
            sx = centerX;
            sy = (int) (height * 0.70f);
            ex = centerX;
            ey = (int) (height * 0.46f);
        } else if ("down".equals(direction)) {
            sx = centerX;
            sy = (int) (height * 0.46f);
            ex = centerX;
            ey = (int) (height * 0.70f);
        } else if ("left".equals(direction)) {
            sx = (int) (width * 0.67f);
            sy = centerY;
            ex = (int) (width * 0.33f);
            ey = centerY;
        } else if ("right".equals(direction)) {
            sx = (int) (width * 0.33f);
            sy = centerY;
            ex = (int) (width * 0.67f);
            ey = centerY;
        } else {
            return;
        }

        if (!GestureAccessibilityService.sendSwipe(direction, sx, sy, ex, ey, 50)) {
            Toast.makeText(this, "O serviço de Acessibilidade não está ativo.", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeOverlay() {
        if (root != null) {
            try { wm.removeView(root); } catch (Throwable ignored) {}
        }
        root = null;
        params = null;
    }

    private Notification notification(String message) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Controle Direcional")
            .setContentText(message)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build();
    }

    private void updateNotification(String message) {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
            .notify(NOTIFICATION_ID, notification(message));
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL, "Controles na tela", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Mantém as setas direcionais translúcidas visíveis durante o jogo.");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), 0x805E789A);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        removeOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
