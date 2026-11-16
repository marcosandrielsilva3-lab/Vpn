package app.gameturbo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class GameTurboService extends Service {
    public static final String ACTION_START_OVERLAY = "app.gameturbo.START_OVERLAY";
    public static final String ACTION_STOP_OVERLAY = "app.gameturbo.STOP_OVERLAY";
    public static final String ACTION_START_TIMER = "app.gameturbo.START_TIMER";
    public static final String ACTION_CANCEL_TIMER = "app.gameturbo.CANCEL_TIMER";
    public static final String EXTRA_MINUTES = "minutes";
    public static final String PREFS = "gameturbo_service";
    public static final String PREF_TIMER_END = "timer_end";
    private static final String CHANNEL_ID = "gameturbo_session";
    private static final int NOTIFICATION_ID = 7401;
    private static final int FINISHED_NOTIFICATION_ID = 7402;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View overlayRoot;
    private LinearLayout overlayPanel;
    private WindowManager.LayoutParams overlayParams;
    private boolean overlayActive = false;
    private boolean timerActive = false;
    private long timerEndWallClock = 0L;

    private final Runnable timerTick = new Runnable() {
        @Override public void run() {
            if (!timerActive) return;
            long remaining = timerEndWallClock - System.currentTimeMillis();
            if (remaining <= 0) {
                finishTimer();
                return;
            }
            updateForegroundNotification("Sessão termina em " + formatTime(remaining));
            handler.postDelayed(this, 15000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_START_TIMER.equals(action) || ACTION_START_OVERLAY.equals(action) ||
            ACTION_STOP_OVERLAY.equals(action) || ACTION_CANCEL_TIMER.equals(action)) {
            startForeground(NOTIFICATION_ID, buildNotification("Game Turbo está ativo."));
        }

        if (ACTION_START_OVERLAY.equals(action)) {
            showOverlay();
        } else if (ACTION_STOP_OVERLAY.equals(action)) {
            removeOverlay();
            stopIfIdle();
        } else if (ACTION_START_TIMER.equals(action)) {
            int minutes = intent.getIntExtra(EXTRA_MINUTES, 30);
            startTimer(minutes);
        } else if (ACTION_CANCEL_TIMER.equals(action)) {
            cancelTimer();
            stopIfIdle();
        }
        return START_NOT_STICKY;
    }

    private void showOverlay() {
        if (overlayActive) return;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Ative a permissão de sobreposição nas configurações do Android.", Toast.LENGTH_LONG).show();
            stopIfIdle();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.setBackground(background(0xEE111B2E, 18));

        TextView bubble = new TextView(this);
        bubble.setText("GT");
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(16);
        bubble.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackground(background(0xFF18A77E, 28));
        root.addView(bubble, new LinearLayout.LayoutParams(dp(54), dp(54)));

        overlayPanel = new LinearLayout(this);
        overlayPanel.setOrientation(LinearLayout.VERTICAL);
        overlayPanel.setPadding(dp(6), dp(8), dp(6), dp(4));
        overlayPanel.setVisibility(View.GONE);
        root.addView(overlayPanel, new LinearLayout.LayoutParams(dp(190), -2));
        addOverlayAction("Abrir Game Turbo", () -> openMainApp());
        addOverlayAction("Abrir Roblox", () -> launchPackage("com.roblox.client"));
        addOverlayAction("Abrir Free Fire", () -> launchFreeFire());
        addOverlayAction("Fechar bolinha", () -> {
            removeOverlay();
            stopIfIdle();
        });

        int type = Build.VERSION.SDK_INT >= 26
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
        overlayParams = new WindowManager.LayoutParams(
            -2, -2, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.TOP | Gravity.END;
        overlayParams.x = dp(16);
        overlayParams.y = dp(180);

        final float[] down = new float[2];
        final int[] original = new int[2];
        final boolean[] moved = new boolean[1];
        bubble.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getRawX();
                    down[1] = event.getRawY();
                    original[0] = overlayParams.x;
                    original[1] = overlayParams.y;
                    moved[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - down[0];
                    float dy = event.getRawY() - down[1];
                    if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) moved[0] = true;
                    if (moved[0]) {
                        overlayParams.x = Math.max(0, original[0] + (int) dx);
                        overlayParams.y = Math.max(0, original[1] + (int) dy);
                        try { windowManager.updateViewLayout(overlayRoot, overlayParams); } catch (Exception ignored) {}
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved[0] && overlayPanel != null) {
                        overlayPanel.setVisibility(overlayPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                        try { windowManager.updateViewLayout(overlayRoot, overlayParams); } catch (Exception ignored) {}
                    }
                    return true;
                default:
                    return true;
            }
        });

        overlayRoot = root;
        try {
            windowManager.addView(overlayRoot, overlayParams);
            overlayActive = true;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("overlay_on", true).apply();
            updateForegroundNotification("Bolinha flutuante ativa.");
        } catch (Exception error) {
            overlayRoot = null;
            overlayPanel = null;
            overlayActive = false;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("overlay_on", false).apply();
            Toast.makeText(this, "Não foi possível mostrar a janela flutuante.", Toast.LENGTH_LONG).show();
            stopIfIdle();
        }
    }

    private void addOverlayAction(String label, Runnable action) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setPadding(dp(10), dp(11), dp(10), dp(11));
        button.setBackground(background(0xFF26354F, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4);
        overlayPanel.addView(button, lp);
        button.setOnClickListener(v -> action.run());
    }

    private void removeOverlay() {
        if (overlayRoot != null) {
            try { windowManager.removeView(overlayRoot); } catch (Exception ignored) {}
        }
        overlayRoot = null;
        overlayPanel = null;
        overlayParams = null;
        overlayActive = false;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("overlay_on", false).apply();
        updateForegroundNotification(timerActive ? "Cronômetro de sessão ativo." : "Game Turbo pronto.");
    }

    private void startTimer(int minutes) {
        if (minutes < 1 || minutes > 240) minutes = 30;
        timerEndWallClock = System.currentTimeMillis() + minutes * 60_000L;
        timerActive = true;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(PREF_TIMER_END, timerEndWallClock).apply();
        handler.removeCallbacks(timerTick);
        handler.post(timerTick);
    }

    private void cancelTimer() {
        timerActive = false;
        timerEndWallClock = 0L;
        handler.removeCallbacks(timerTick);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(PREF_TIMER_END, 0L).apply();
        updateForegroundNotification(overlayActive ? "Bolinha flutuante ativa." : "Cronômetro cancelado.");
    }

    private void finishTimer() {
        timerActive = false;
        timerEndWallClock = 0L;
        handler.removeCallbacks(timerTick);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(PREF_TIMER_END, 0L).apply();
        Notification finished = buildFinishedNotification();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(FINISHED_NOTIFICATION_ID, finished);
        if (overlayActive) {
            updateForegroundNotification("Bolinha flutuante ativa.");
        } else {
            stopForeground(true);
            stopSelf();
        }
    }

    private void stopIfIdle() {
        if (!overlayActive && !timerActive) {
            stopForeground(true);
            stopSelf();
        } else {
            updateForegroundNotification(timerActive ? "Sessão termina em " + formatTime(timerEndWallClock - System.currentTimeMillis()) : "Bolinha flutuante ativa.");
        }
    }

    private void openMainApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void launchFreeFire() {
        if (!launchPackage("com.dts.freefireth") && !launchPackage("com.dts.freefiremax")) {
            Toast.makeText(this, "Free Fire não encontrado.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean launchPackage(String packageName) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) return false;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        return true;
    }

    private Notification buildNotification(String message) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(
            this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Game Turbo")
            .setContentText(message)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build();
    }

    private Notification buildFinishedNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(
            this, 2, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Game Turbo")
            .setContentText("Seu tempo de sessão terminou.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build();
    }

    private void updateForegroundNotification(String message) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification(message));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Game Turbo", NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Notificação do cronômetro e da bolinha flutuante.");
            channel.setShowBadge(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private String formatTime(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(timerTick);
        removeOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
