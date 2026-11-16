package com.example.vpnlocal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.IOException;
import java.util.Locale;

public class LocalVpnService extends VpnService {
    private static final String CHANNEL = "local_vpn_status";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ParcelFileDescriptor tunnel;
    private WindowManager windowManager;
    private TextView bubble;
    private WindowManager.LayoutParams bubbleParams;
    private boolean lagMode = false;
    private long lagStartedAt = 0L;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateBubble();
            if (lagMode) handler.postDelayed(this, 1000);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification;
        if (Build.VERSION.SDK_INT >= 26) {
            notification = new Notification.Builder(this, CHANNEL)
                .setContentTitle("VPN Local Demo")
                .setContentText("VPN local ativa — bolinha flutuante disponível")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .build();
        } else {
            notification = new Notification.Builder(this)
                .setContentTitle("VPN Local Demo")
                .setContentText("VPN local ativa — bolinha flutuante disponível")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .build();
        }
        startForeground(1, notification);

        try {
            establishTunnel(false);
            showBubble();
        } catch (Exception e) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void establishTunnel(boolean blockAllTraffic) throws Exception {
        Builder builder = new Builder()
            .setSession("VPN Local Demo")
            .addAddress("10.0.0.2", 32);

        if (blockAllTraffic) {
            // This is an internet-blocking simulation, not a packet-delay tunnel.
            builder.addRoute("0.0.0.0", 0)
                   .addAddress("fd00:1234:5678::2", 128)
                   .addRoute("::", 0);
        } else {
            builder.addRoute("192.0.2.1", 32);
            builder.addAllowedApplication(getPackageName());
        }

        ParcelFileDescriptor newTunnel = builder.establish();
        if (newTunnel == null) {
            throw new IllegalStateException("Não foi possível criar a interface VPN");
        }

        ParcelFileDescriptor oldTunnel = tunnel;
        tunnel = newTunnel;
        if (oldTunnel != null) {
            try {
                oldTunnel.close();
            } catch (IOException ignored) {}
        }
    }

    private void showBubble() {
        if (Build.VERSION.SDK_INT >= 23 && !android.provider.Settings.canDrawOverlays(this)) {
            return;
        }
        if (bubble != null) return;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        bubble = new TextView(this);
        bubble.setTextSize(11);
        bubble.setTextColor(0xFFFFFFFF);
        bubble.setGravity(Gravity.CENTER);
        bubble.setPadding(4, 4, 4, 4);
        bubble.setElevation(10f);
        updateBubble();

        int type = Build.VERSION.SDK_INT >= 26
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        bubbleParams = new WindowManager.LayoutParams(
            dp(68), dp(68), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = dp(18);
        bubbleParams.y = dp(180);

        bubble.setOnTouchListener(new View.OnTouchListener() {
            private int startX;
            private int startY;
            private float downRawX;
            private float downRawY;
            private boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = bubbleParams.x;
                        startY = bubbleParams.y;
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - downRawX);
                        int dy = (int) (event.getRawY() - downRawY);
                        if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) moved = true;
                        bubbleParams.x = startX + dx;
                        bubbleParams.y = startY + dy;
                        windowManager.updateViewLayout(bubble, bubbleParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) toggleLagMode();
                        return true;
                    default:
                        return true;
                }
            }
        });

        try {
            windowManager.addView(bubble, bubbleParams);
        } catch (Exception e) {
            bubble = null;
        }
    }

    private void toggleLagMode() {
        try {
            if (!lagMode) {
                establishTunnel(true);
                lagMode = true;
                lagStartedAt = SystemClock.elapsedRealtime();
                handler.removeCallbacks(timerRunnable);
                handler.post(timerRunnable);
            } else {
                establishTunnel(false);
                lagMode = false;
                lagStartedAt = 0L;
                handler.removeCallbacks(timerRunnable);
            }
            updateBubble();
        } catch (Exception e) {
            lagMode = false;
            lagStartedAt = 0L;
            handler.removeCallbacks(timerRunnable);
            updateBubble();
        }
    }

    private void updateBubble() {
        if (bubble == null) return;
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(lagMode ? 0xFFD32F2F : 0xFF1565C0);
        bubble.setBackground(background);

        if (lagMode) {
            long seconds = (SystemClock.elapsedRealtime() - lagStartedAt) / 1000L;
            long minutes = seconds / 60L;
            long remainder = seconds % 60L;
            bubble.setText(String.format(Locale.US, "PAUSA\n%02d:%02d", minutes, remainder));
        } else {
            bubble.setText("LAG\nOFF");
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL, "Status da VPN local", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(timerRunnable);
        if (bubble != null && windowManager != null) {
            try {
                windowManager.removeView(bubble);
            } catch (Exception ignored) {}
            bubble = null;
        }
        if (tunnel != null) {
            try {
                tunnel.close();
            } catch (IOException ignored) {}
            tunnel = null;
        }
        stopForeground(true);
        super.onDestroy();
    }
}
