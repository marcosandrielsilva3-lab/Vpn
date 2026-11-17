package app.directional.controller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int REQ_OVERLAY = 710, REQ_SHIZUKU = 711;
    private static final String GAME = "com.random.zurich";
    private TextView status;
    private boolean pendingOverlay = false;

    private final Shizuku.OnRequestPermissionResultListener permissionListener = (code, result) -> {
        if (code == REQ_SHIZUKU) {
            if (result == PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, "Shizuku autorizado. Toque em Ativar setas.", Toast.LENGTH_LONG).show();
            else Toast.makeText(this, "Autorização do Shizuku negada.", Toast.LENGTH_LONG).show();
            updateStatus();
        }
    };
    private final Shizuku.OnBinderReceivedListener binderReceived = () -> runOnUiThread(this::updateStatus);
    private final Shizuku.OnBinderDeadListener binderDead = () -> runOnUiThread(this::updateStatus);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        Shizuku.addBinderReceivedListenerSticky(binderReceived);
        Shizuku.addBinderDeadListener(binderDead);
        buildUi();
        updateStatus();
    }
    @Override protected void onResume() { super.onResume(); updateStatus(); }

    private void buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(26), dp(20), dp(28));
        page.setBackgroundColor(Color.rgb(10, 15, 28));
        page.addView(text("CONTROLE DIRECIONAL", 25, Color.WHITE, true));
        page.addView(text("Quatro setas flutuantes para jogar no celular", 14, 0xFFB6C3D8, false));
        space(page, 18);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(bg(0xFF17243A, 16));
        page.addView(card, new LinearLayout.LayoutParams(-1, -2));
        card.addView(text("As setas aparecem por cima do jogo e enviam gestos reais de toque. O APK do jogo e a resolução da tela não são alterados.", 14, 0xFFE3EAF5, false));
        status = text("", 14, 0xFF72E0B5, true);
        card.addView(status);
        Button shizuku = button("1. Conectar / autorizar Shizuku");
        Button start = button("2. Ativar setas e abrir o jogo");
        Button stop = button("Desativar setas");
        Button open = button("Abrir jogo");
        card.addView(shizuku); card.addView(start); card.addView(stop); card.addView(open);
        shizuku.setOnClickListener(v -> connectShizuku());
        start.setOnClickListener(v -> prepareControls());
        stop.setOnClickListener(v -> stopControls());
        open.setOnClickListener(v -> launchGame());
        space(page, 16);
        LinearLayout help = new LinearLayout(this);
        help.setOrientation(LinearLayout.VERTICAL);
        help.setPadding(dp(16), dp(16), dp(16), dp(16));
        help.setBackground(bg(0xFF17243A, 16));
        page.addView(help, new LinearLayout.LayoutParams(-1, -2));
        help.addView(text("COMO AS SETAS FUNCIONAM", 15, 0xFF72E0B5, true));
        help.addView(text("↑ Frente / pular\n↓ Baixar / deslizar\n← Esquerda / trocar de faixa\n→ Direita / trocar de faixa", 15, Color.WHITE, false));
        help.addView(text("Nesse estilo de corrida, o personagem avança sozinho. As setas fazem pulo, deslize e troca de faixa. Segure a barra no topo do controle para mover o conjunto.", 13, 0xFFB6C3D8, false));
        help.addView(text("Requisitos: Shizuku iniciado pela Depuração sem fio, autorização para este app e permissão para aparecer sobre outros apps.", 13, 0xFFB6C3D8, false));
        setContentView(page);
    }

    private void prepareControls() {
        if (!Shizuku.pingBinder()) {
            new AlertDialog.Builder(this).setTitle("Shizuku não está iniciado")
                .setMessage("Abra o Shizuku, inicie pela Depuração sem fio e volte aqui. Depois toque em Ativar setas.")
                .setPositiveButton("Abrir Shizuku", (d,w) -> openShizuku())
                .setNegativeButton("Fechar", null).show();
            return;
        }
        try {
            if (Shizuku.isPreV11()) {
                Toast.makeText(this, "Atualize o Shizuku para uma versão recente.", Toast.LENGTH_LONG).show();
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQ_SHIZUKU);
                Toast.makeText(this, "Autorize o Controle Direcional no Shizuku e toque em Ativar novamente.", Toast.LENGTH_LONG).show();
                return;
            }
        } catch (Throwable e) {
            Toast.makeText(this, "Não foi possível verificar o Shizuku: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            pendingOverlay = true;
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_OVERLAY);
            return;
        }
        startControlsAndGame();
    }

    private void startControlsAndGame() {
        Intent i = new Intent(this, DirectionalOverlayService.class);
        i.setAction("app.directional.controller.START");
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            Toast.makeText(this, "Setas ativadas. Abrindo o jogo...", Toast.LENGTH_LONG).show();
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::launchGame, 900);
        } catch (Throwable e) {
            Toast.makeText(this, "Não foi possível iniciar as setas: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopControls() {
        Intent i = new Intent(this, DirectionalOverlayService.class);
        i.setAction("app.directional.controller.STOP");
        try { startService(i); } catch (Throwable e) { stopService(new Intent(this, DirectionalOverlayService.class)); }
        Toast.makeText(this, "Controles desativados.", Toast.LENGTH_SHORT).show();
    }

    private void launchGame() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(GAME);
        if (launch == null) {
            Toast.makeText(this, "O jogo com pacote " + GAME + " não está instalado. Instale o APK dele primeiro.", Toast.LENGTH_LONG).show();
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
    }

    private void connectShizuku() {
        if (!Shizuku.pingBinder()) { openShizuku(); return; }
        try {
            if (Shizuku.isPreV11()) Toast.makeText(this, "Atualize o Shizuku.", Toast.LENGTH_LONG).show();
            else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, "Shizuku já está autorizado.", Toast.LENGTH_SHORT).show();
            else Shizuku.requestPermission(REQ_SHIZUKU);
        } catch (Throwable e) { Toast.makeText(this, "Falha no Shizuku: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
        updateStatus();
    }

    private void openShizuku() {
        Intent i = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if (i != null) { startActivity(i); return; }
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))); }
        catch (Throwable e) { Toast.makeText(this, "Não foi possível abrir o Shizuku.", Toast.LENGTH_LONG).show(); }
    }

    private void updateStatus() {
        if (status == null) return;
        boolean alive = Shizuku.pingBinder(), allowed = false;
        if (alive) try { allowed = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; } catch (Throwable ignored) {}
        String overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this) ? "concedida" : "pendente";
        status.setText("Shizuku: " + (!alive ? "não iniciado" : allowed ? "autorizado" : "aguardando autorização") +
            "\nPermissão de sobreposição: " + overlay);
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == REQ_OVERLAY) {
            updateStatus();
            if (pendingOverlay && (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this))) {
                pendingOverlay = false; startControlsAndGame();
            } else if (pendingOverlay) {
                pendingOverlay = false;
                Toast.makeText(this, "A permissão de sobreposição não foi concedida.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setPadding(0, dp(4), 0, dp(8)); return v;
    }
    private Button button(String label) {
        Button v = new Button(this); v.setText(label); v.setAllCaps(false); v.setTextColor(Color.WHITE);
        v.setTextSize(14); v.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF176B59));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48)); lp.topMargin = dp(7); v.setLayoutParams(lp);
        return v;
    }
    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private void space(LinearLayout p, int h) { View v = new View(this); p.addView(v, new LinearLayout.LayoutParams(1, dp(h))); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        Shizuku.removeBinderReceivedListener(binderReceived);
        Shizuku.removeBinderDeadListener(binderDead);
        super.onDestroy();
    }
}