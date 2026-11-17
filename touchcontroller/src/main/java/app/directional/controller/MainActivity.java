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
    private static final int REQ_OVERLAY = 710;
    private static final int REQ_SHIZUKU = 711;
    private static final String GAME = "com.random.zurich";
    private TextView status;
    private boolean pendingOverlay = false;

    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener =
        (requestCode, grantResult) -> {
            if (requestCode == REQ_SHIZUKU) {
                updateStatus();
                Toast.makeText(this,
                    grantResult == PackageManager.PERMISSION_GRANTED
                        ? "Modo rápido autorizado. Ative as setas novamente."
                        : "Permissão Shizuku não concedida.",
                    Toast.LENGTH_LONG).show();
            }
        };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
        } catch (Throwable ignored) {}
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override
    protected void onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener); }
        catch (Throwable ignored) {}
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(26), dp(20), dp(28));
        page.setBackgroundColor(Color.rgb(10, 15, 28));
        page.addView(text("CONTROLE DIRECIONAL", 25, Color.WHITE, true));
        page.addView(text("Setas flutuantes para controlar os movimentos do jogo", 14, 0xFFB6C3D8, false));
        space(page, 18);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(bg(0xFF17243A, 16));
        page.addView(card, new LinearLayout.LayoutParams(-1, -2));
        card.addView(text("Cada toque completo envia um movimento. O modo rápido é opcional e usa Shizuku; sem ele, o app usa Acessibilidade.", 14, 0xFFE3EAF5, false));
        status = text("", 14, 0xFF72E0B5, true);
        card.addView(status);

        Button fast = button("Ativar modo rápido (Shizuku)");
        Button accessibility = button("Ativar controle (Acessibilidade)");
        Button start = button("Ativar setas e abrir o jogo");
        Button stop = button("Desativar setas");
        Button open = button("Abrir jogo");
        card.addView(fast);
        card.addView(accessibility);
        card.addView(start);
        card.addView(stop);
        card.addView(open);

        fast.setOnClickListener(v -> enableFastMode());
        accessibility.setOnClickListener(v -> openAccessibilitySettings());
        start.setOnClickListener(v -> prepareControls());
        stop.setOnClickListener(v -> stopControls());
        open.setOnClickListener(v -> launchGame());

        space(page, 16);
        LinearLayout help = new LinearLayout(this);
        help.setOrientation(LinearLayout.VERTICAL);
        help.setPadding(dp(16), dp(16), dp(16), dp(16));
        help.setBackground(bg(0xFF17243A, 16));
        page.addView(help, new LinearLayout.LayoutParams(-1, -2));
        help.addView(text("COMO ATIVAR", 15, 0xFF72E0B5, true));
        help.addView(text("1. Para o modo rápido: instale e inicie Shizuku, toque em Ativar modo rápido e conceda a permissão.\n2. Como alternativa, ative Controle Direcional em Serviços instalados na Acessibilidade.\n3. Permita aparecer sobre outros apps e toque em Ativar setas.", 14, Color.WHITE, false));
        help.addView(text("Setas: ↑ Pular   ↓ Deslizar   ← Faixa esquerda   → Faixa direita", 13, 0xFFB6C3D8, false));

        setContentView(page);
    }

    private void enableFastMode() {
        try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this,
                    "Abra o Shizuku e inicie o serviço primeiro. No Android 11 ou superior, use Depuração sem fio.",
                    Toast.LENGTH_LONG).show();
                return;
            }
            if (Shizuku.isPreV11()) {
                Toast.makeText(this, "Atualize o Shizuku para uma versão compatível.", Toast.LENGTH_LONG).show();
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Modo rápido já está autorizado.", Toast.LENGTH_SHORT).show();
            } else {
                Shizuku.requestPermission(REQ_SHIZUKU);
            }
            updateStatus();
        } catch (Throwable error) {
            Toast.makeText(this, "Não foi possível iniciar o modo rápido: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void prepareControls() {
        if (!GestureAccessibilityService.isRunning() && !DirectionalOverlayService.hasFastInputPermission()) {
            new AlertDialog.Builder(this)
                .setTitle("Ative um método de controle")
                .setMessage("Ative Controle Direcional em Serviços instalados na Acessibilidade ou autorize o modo rápido Shizuku. Um dos dois precisa estar ativo.")
                .setPositiveButton("Abrir Acessibilidade", (dialog, which) -> openAccessibilitySettings())
                .setNeutralButton("Ativar Shizuku", (dialog, which) -> enableFastMode())
                .setNegativeButton("Cancelar", null)
                .show();
            return;
        }

        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            pendingOverlay = true;
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_OVERLAY);
            return;
        }
        startControlsAndGame();
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Throwable e) {
            Toast.makeText(this, "Não foi possível abrir as configurações de Acessibilidade.", Toast.LENGTH_LONG).show();
        }
    }

    private void startControlsAndGame() {
        Intent intent = new Intent(this, DirectionalOverlayService.class);
        intent.setAction("app.directional.controller.START");
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
            Toast.makeText(this, "Setas ativadas. Abrindo o jogo...", Toast.LENGTH_SHORT).show();
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::launchGame, 700);
        } catch (Throwable e) {
            Toast.makeText(this, "Não foi possível iniciar as setas: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopControls() {
        Intent intent = new Intent(this, DirectionalOverlayService.class);
        intent.setAction("app.directional.controller.STOP");
        try {
            startService(intent);
        } catch (Throwable e) {
            stopService(new Intent(this, DirectionalOverlayService.class));
        }
        Toast.makeText(this, "Controles desativados.", Toast.LENGTH_SHORT).show();
    }

    private void launchGame() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(GAME);
        if (launch == null) {
            Toast.makeText(this, "O jogo com pacote " + GAME + " não está instalado.", Toast.LENGTH_LONG).show();
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
    }

    private void updateStatus() {
        if (status == null) return;
        String accessibility = GestureAccessibilityService.isRunning() ? "ativa" : "precisa ser ativada";
        String overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this) ? "concedida" : "pendente";
        String shizuku = "indisponível";
        try {
            if (Shizuku.pingBinder()) {
                shizuku = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    ? "autorizado" : "aguardando permissão";
            }
        } catch (Throwable ignored) {}
        status.setText("Acessibilidade: " + accessibility +
            "\nShizuku: " + shizuku +
            "\nSobreposição: " + overlay);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(4), 0, dp(8));
        return view;
    }

    private Button button(String label) {
        Button view = new Button(this);
        view.setText(label);
        view.setAllCaps(false);
        view.setTextColor(Color.WHITE);
        view.setTextSize(14);
        view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF176B59));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.topMargin = dp(7);
        view.setLayoutParams(params);
        return view;
    }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private void space(LinearLayout parent, int height) {
        View view = new View(this);
        parent.addView(view, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
