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

public class MainActivity extends Activity {
    private static final int REQ_OVERLAY = 710;
    private static final String GAME = "com.random.zurich";
    private TextView status;
    private boolean pendingOverlay = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
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
        card.addView(text("Cada toque completo envia um único gesto. Toque na seta e solte para executar o movimento.", 14, 0xFFE3EAF5, false));
        status = text("", 14, 0xFF72E0B5, true);
        card.addView(status);

        Button accessibility = button("1. Ativar controle rápido (Acessibilidade)");
        Button start = button("2. Ativar setas e abrir o jogo");
        Button stop = button("Desativar setas");
        Button open = button("Abrir jogo");
        card.addView(accessibility);
        card.addView(start);
        card.addView(stop);
        card.addView(open);

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
        help.addView(text("1. Toque em Ativar controle rápido.\n2. Nas configurações do Android, abra Serviços instalados e ative Controle Direcional.\n3. Volte aqui e toque em Ativar setas.", 14, Color.WHITE, false));
        help.addView(text("Também é necessário permitir aparecer sobre outros apps. O serviço executa apenas os gestos solicitados pelas setas; não lê o conteúdo da tela.", 13, 0xFFB6C3D8, false));
        help.addView(text("↑ Pular   ↓ Deslizar   ← Faixa esquerda   → Faixa direita", 13, 0xFFB6C3D8, false));

        setContentView(page);
    }

    private void prepareControls() {
        if (!GestureAccessibilityService.isRunning()) {
            new AlertDialog.Builder(this)
                .setTitle("Ative o Controle Direcional")
                .setMessage("O Android precisa permitir que este app execute os gestos das setas. Na próxima tela, abra Serviços instalados e ative Controle Direcional. Depois volte para este app.")
                .setPositiveButton("Abrir Acessibilidade", (dialog, which) -> openAccessibilitySettings())
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
        status.setText("Acessibilidade: " + accessibility + "\nPermissão de sobreposição: " + overlay);
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
