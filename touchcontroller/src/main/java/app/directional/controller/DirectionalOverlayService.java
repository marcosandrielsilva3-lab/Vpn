package app.directional.controller;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import rikka.shizuku.Shizuku;

public class DirectionalOverlayService extends Service {
    private static final String START = "app.directional.controller.START";
    private static final String STOP = "app.directional.controller.STOP";
    private static final String CHANNEL = "directional_controls";
    private static final int NOTIFICATION_ID = 8101;
    private final Handler handler = new Handler(Looper.getMainLooper());
    // Bounded queue prevents old taps from piling up and making the controls feel delayed.
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(4),
        new ThreadPoolExecutor.DiscardOldestPolicy()
    );
    private WindowManager wm;
    private View root;
    private WindowManager.LayoutParams params;
    private IGestureShell shell;
    private Shizuku.UserServiceArgs userArgs;
    private boolean binding = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            shell = IGestureShell.Stub.asInterface(binder);
            binding = false;
            handler.post(() -> {
                Toast.makeText(DirectionalOverlayService.this, "Controle conectado e pronto.", Toast.LENGTH_SHORT).show();
                showOverlay();
                updateNotification("Setas prontas para usar.");
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            shell = null;
            binding = false;
            handler.post(() -> Toast.makeText(DirectionalOverlayService.this,
                "Conexão Shizuku perdida. Reative as setas.", Toast.LENGTH_LONG).show());
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (START.equals(action) || STOP.equals(action))
            startForeground(NOTIFICATION_ID, notification("Preparando controles."));
        if (START.equals(action)) {
            bindShell();
            // Do not show clickable arrows until the Shizuku gesture service is connected.
            if (shell != null) showOverlay();
            updateNotification("Conectando controle de gestos...");
        } else if (STOP.equals(action)) {
            removeOverlay();
            unbindShell();
            stopForeground(true);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void bindShell() {
        if (shell != null || binding) return;
        try {
            if (!Shizuku.pingBinder() ||
                Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Autorize Shizuku antes de usar as setas.", Toast.LENGTH_LONG).show();
                return;
            }
            userArgs = new Shizuku.UserServiceArgs(
                new ComponentName(getPackageName(), GestureShellService.class.getName()))
                .tag("directional-gesture-shell").daemon(false).processNameSuffix("gesture")
                .debuggable(BuildConfig.DEBUG).version(1);
            binding = true;
            Shizuku.bindUserService(userArgs, connection);
            handler.postDelayed(() -> {
                if (binding && shell == null) {
                    Toast.makeText(DirectionalOverlayService.this,
                        "O Shizuku demorou para conectar. Confira se ele continua ativo e tente reativar.",
                        Toast.LENGTH_LONG).show();
                }
            }, 5000);
        } catch (Throwable e) {
            binding = false;
            Toast.makeText(this, "Falha ao conectar o serviço de gestos: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void unbindShell() {
        if (userArgs != null) {
            try { Shizuku.unbindUserService(userArgs, connection, true); } catch (Throwable ignored) {}
        }
        shell = null;
        binding = false;
        userArgs = null;
    }

    private void showOverlay() {
        if (root != null) return;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Permita a sobreposição nas configurações.", Toast.LENGTH_LONG).show();
            return;
        }

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(7), dp(8), dp(8));
        panel.setBackground(background(0xD9162237, 18));

        TextView drag = new TextView(this);
        drag.setText("CONTROLE • SEGURE PARA MOVER");
        drag.setTextColor(0xFFE2EAF6);
        drag.setTextSize(10);
        drag.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        drag.setGravity(Gravity.CENTER);
        drag.setPadding(dp(5), dp(4), dp(5), dp(7));
        panel.addView(drag, new LinearLayout.LayoutParams(-1, dp(28)));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER);
        top.addView(spacer());
        top.addView(makeArrow("↑", "up"));
        top.addView(spacer());
        panel.addView(top, new LinearLayout.LayoutParams(-1, dp(70)));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        bottom.addView(makeArrow("←", "left"));
        bottom.addView(makeArrow("↓", "down"));
        bottom.addView(makeArrow("→", "right"));
        panel.addView(bottom, new LinearLayout.LayoutParams(-1, dp(70)));

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
        params.x = dp(14);
        params.y = dp(70);

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
        b.setTextSize(34);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setBackground(background(0xEE29415F, 15));
        b.setSoundEffectsEnabled(false);
        b.setHapticFeedbackEnabled(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(70), dp(66));
        lp.setMargins(dp(4), dp(2), dp(4), dp(2));
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            sendGesture(direction);
        });
        return b;
    }

    private void sendGesture(String direction) {
        final IGestureShell current = shell;
        if (current == null) {
            Toast.makeText(this, "O controle ainda está conectando. Aguarde aparecer 'Controle conectado'.",
                Toast.LENGTH_SHORT).show();
            if (!binding) bindShell();
            return;
        }

        DisplayMetrics m = new DisplayMetrics();
        Display d = wm.getDefaultDisplay();
        d.getRealMetrics(m);
        int w = m.widthPixels;
        int h = m.heightPixels;
        int sx, sy, ex, ey;

        if ("up".equals(direction)) {
            // Jump starts farther from the bottom edge and travels a larger vertical distance.
            sx = (int)(w * 0.50f);
            sy = (int)(h * 0.76f);
            ex = sx;
            ey = (int)(h * 0.24f);
        } else if ("down".equals(direction)) {
            // Keep the previously working slide direction, slightly away from the screen edge.
            sx = (int)(w * 0.50f);
            sy = (int)(h * 0.43f);
            ex = sx;
            ey = (int)(h * 0.78f);
        } else if ("left".equals(direction)) {
            sx = (int)(w * 0.82f);
            sy = (int)(h * 0.60f);
            ex = (int)(w * 0.34f);
            ey = sy;
        } else if ("right".equals(direction)) {
            sx = (int)(w * 0.34f);
            sy = (int)(h * 0.60f);
            ex = (int)(w * 0.82f);
            ey = sy;
        } else {
            return;
        }

        try {
            executor.execute(() -> {
                try {
                    String result = current.swipe(sx, sy, ex, ey, 70);
                    if (result == null || !result.startsWith("EXIT:0")) {
                        handler.post(() -> Toast.makeText(this,
                            "Falha ao enviar gesto: " + result, Toast.LENGTH_LONG).show());
                    }
                } catch (Throwable e) {
                    handler.post(() -> Toast.makeText(this,
                        "Falha ao enviar seta: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
        } catch (Throwable e) {
            Toast.makeText(this, "Não foi possível enviar o toque: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
            ? new Notification.Builder(this, CHANNEL)
            : new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Controle Direcional")
            .setContentText(msg)
            .setContentIntent(p)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build();
    }

    private void updateNotification(String msg) {
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
            .notify(NOTIFICATION_ID, notification(msg));
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(
                CHANNEL, "Controles na tela", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Mantém as setas direcionais visíveis durante o jogo.");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), 0x805E789A);
        return d;
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    @Override public void onDestroy() {
        removeOverlay();
        unbindShell();
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}