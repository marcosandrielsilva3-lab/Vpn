package app.directional.controller;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
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
            updateNotification("Gestos rápidos por Acessibilidade ativos.");
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
        panel.setPadding(dp(7), dp(6), dp(7), dp(7));
        panel.setBackground(background(0xD9162237, 18));

        TextView drag = new TextView(this);
        drag.setText("SEGURE AQUI PARA MOVER");
        drag.setTextColor(0xFFE2EAF6);
        drag.setTextSize(11);
        drag.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        drag.setGravity(Gravity.CENTER);
        drag.setPadding(dp(5), dp(3), dp(5), dp(5));
        panel.addView(drag, new LinearLayout.LayoutParams(-1, dp(27)));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER);
        top.addView(spacer());
        top.addView(makeArrow("↑", "up"));
        top.addView(spacer());
        panel.addView(top, new LinearLayout.LayoutParams(-1, dp(96)));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        bottom.addView(makeArrow("←", "left"));
        bottom.addView(makeArrow("↓", "down"));
        bottom.addView(makeArrow("→", "right"));
        panel.addView(bottom, new LinearLayout.LayoutParams(-1, dp(96)));

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
            switch (e.getAction()) {
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
        TextView b = new TextView(this);
        b.setText(symbol);
        b.setTextColor(Color.WHITE);
        b.setTextSize(48);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setBackground(background(0xEE29415F, 16));
        b.setSoundEffectsEnabled(false);
        b.setHapticFeedbackEnabled(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(92), dp(88));
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        b.setLayoutParams(lp);
        b.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                v.setAlpha(0.72f);
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                sendGesture(direction);
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                v.setAlpha(1f);
                return true;
            }
            return true;
        });
        return b;
    }

    private void sendGesture(String direction) {
        DisplayMetrics metrics = new DisplayMetrics();
        Display display = wm.getDefaultDisplay();
        display.getRealMetrics(metrics);
        int w = metrics.widthPixels;
        int h = metrics.heightPixels;

        int centerX = w / 2;
        int centerY = (int) (h * 0.59f);
        int sx, sy, ex, ey;

        if ("up".equals(direction)) {
            sx = centerX;
            sy = (int) (h * 0.71f);
            ex = centerX;
            ey = (int) (h * 0.48f);
        } else if ("down".equals(direction)) {
            sx = centerX;
            sy = (int) (h * 0.48f);
            ex = centerX;
            ey = (int) (h * 0.71f);
        } else if ("left".equals(direction)) {
            sx = (int) (w * 0.64f);
            sy = centerY;
            ex = (int) (w * 0.36f);
            ey = centerY;
        } else if ("right".equals(direction)) {
            sx = (int) (w * 0.36f);
            sy = centerY;
            ex = (int) (w * 0.64f);
            ey = centerY;
        } else {
            return;
        }

        if (!GestureAccessibilityService.sendSwipe(sx, sy, ex, ey, 55)) {
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

    private Notification notification(String msg) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent p = PendingIntent.getActivity(this, 1, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Controle Direcional")
            .setContentText(msg)
            .setContentIntent(p)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build();
    }

    private void updateNotification(String msg) {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
            .notify(NOTIFICATION_ID, notification(msg));
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL, "Controles na tela", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Mantém as setas direcionais visíveis durante o jogo.");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), 0x805E789A);
        return d;
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
