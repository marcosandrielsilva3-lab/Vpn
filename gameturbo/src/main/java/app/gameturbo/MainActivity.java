package app.gameturbo;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_OVERLAY = 201;
    private static final int REQUEST_NOTIFICATIONS = 202;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView statsText;
    private TextView overlayText;
    private TextView profileText;
    private TextView timerText;
    private EditText minutesInput;
    private String selectedProfile = "Roblox";
    private String pendingAction = null;
    private int pendingMinutes = 0;

    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            refreshStats();
            refreshTimer();
            handler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selectedProfile = getPreferences(MODE_PRIVATE).getString("profile", "Roblox");
        buildInterface();
        refreshStats();
        refreshTimer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshOverlayStatus();
        refreshTimer();
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshTask);
        super.onPause();
    }

    private void buildInterface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(8, 13, 25));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(22), dp(18), dp(30));
        scroll.addView(page);

        TextView title = makeText("GAME TURBO", 28, Color.WHITE, true);
        page.addView(title);
        TextView subtitle = makeText("Painel de jogos e ferramentas do Android", 14, 0xFF9EACC6, false);
        page.addView(subtitle);

        addSpace(page, 18);
        LinearLayout deviceCard = card(page, "STATUS DO APARELHO");
        statsText = makeText("Lendo informações do aparelho…", 14, 0xFFE5ECF8, false);
        deviceCard.addView(statsText);

        addSpace(page, 14);
        LinearLayout profileCard = card(page, "PERFIL DE JOGO");
        profileText = makeText("", 14, 0xFFE5ECF8, false);
        profileCard.addView(profileText);
        LinearLayout profileButtons = new LinearLayout(this);
        profileButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button roblox = makeButton("Roblox");
        Button freeFire = makeButton("Free Fire");
        profileButtons.addView(roblox, new LinearLayout.LayoutParams(0, dp(46), 1));
        profileButtons.addView(freeFire, new LinearLayout.LayoutParams(0, dp(46), 1));
        profileCard.addView(profileButtons);
        roblox.setOnClickListener(v -> chooseProfile("Roblox"));
        freeFire.setOnClickListener(v -> chooseProfile("Free Fire"));
        updateProfileText();

        addSpace(page, 14);
        LinearLayout launchCard = card(page, "ABRIR JOGO");
        TextView launchHint = makeText("Abre o jogo instalado no aparelho. O Game Turbo não altera as configurações internas do jogo.", 13, 0xFF9EACC6, false);
        launchCard.addView(launchHint);
        Button launchRoblox = makeButton("Abrir Roblox");
        Button launchFreeFire = makeButton("Abrir Free Fire");
        launchCard.addView(launchRoblox);
        launchCard.addView(launchFreeFire);
        launchRoblox.setOnClickListener(v -> launchGame("com.roblox.client"));
        launchFreeFire.setOnClickListener(v -> launchFreeFire());

        addSpace(page, 14);
        LinearLayout overlayCard = card(page, "BOLINHA FLUTUANTE");
        overlayText = makeText("", 14, 0xFFE5ECF8, false);
        overlayCard.addView(overlayText);
        TextView overlayHint = makeText("A bolinha aparece por cima de outros aplicativos. O Android pede sua autorização antes de ativar.", 13, 0xFF9EACC6, false);
        overlayCard.addView(overlayHint);
        Button overlayButton = makeButton("Ativar bolinha flutuante");
        Button removeOverlayButton = makeButton("Desativar bolinha");
        overlayCard.addView(overlayButton);
        overlayCard.addView(removeOverlayButton);
        overlayButton.setOnClickListener(v -> activateOverlay());
        removeOverlayButton.setOnClickListener(v -> runWithNotificationPermission(GameTurboService.ACTION_STOP_OVERLAY, 0));

        addSpace(page, 14);
        LinearLayout timerCard = card(page, "CRONÔMETRO DA SESSÃO");
        timerText = makeText("Nenhum cronômetro ativo.", 14, 0xFFE5ECF8, false);
        timerCard.addView(timerText);
        minutesInput = new EditText(this);
        minutesInput.setSingleLine(true);
        minutesInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        minutesInput.setText("30");
        minutesInput.setHint("Minutos (1 a 240)");
        minutesInput.setHintTextColor(0xFF8A98B2);
        minutesInput.setTextColor(Color.WHITE);
        minutesInput.setPadding(dp(12), 0, dp(12), 0);
        minutesInput.setBackground(roundRect(0xFF202B43, 12));
        timerCard.addView(minutesInput, new LinearLayout.LayoutParams(-1, dp(48)));
        Button startTimer = makeButton("Iniciar cronômetro");
        Button stopTimer = makeButton("Cancelar cronômetro");
        timerCard.addView(startTimer);
        timerCard.addView(stopTimer);
        startTimer.setOnClickListener(v -> startSessionTimer());
        stopTimer.setOnClickListener(v -> runWithNotificationPermission(GameTurboService.ACTION_CANCEL_TIMER, 0));

        addSpace(page, 14);
        LinearLayout screenCard = card(page, "TELA");
        CheckBox keepScreen = new CheckBox(this);
        keepScreen.setText("Manter a tela acesa enquanto este painel estiver aberto");
        keepScreen.setTextColor(0xFFE5ECF8);
        keepScreen.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF47D7A8));
        screenCard.addView(keepScreen);
        keepScreen.setOnCheckedChangeListener((button, checked) -> {
            if (checked) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });

        addSpace(page, 18);
        TextView limitation = makeText(
            "Importante: Android não permite que um app comum aumente diretamente o FPS, faça overclock ou force a GPU de outros jogos. Este app oferece atalhos, dados reais do aparelho, cronômetro e janela flutuante; não promete ganho falso de desempenho.",
            12, 0xFF8F9DB6, false
        );
        page.addView(limitation);

        setContentView(scroll);
    }

    private void chooseProfile(String profile) {
        selectedProfile = profile;
        getPreferences(MODE_PRIVATE).edit().putString("profile", profile).apply();
        updateProfileText();
        Toast.makeText(this, "Perfil " + profile + " selecionado.", Toast.LENGTH_SHORT).show();
    }

    private void updateProfileText() {
        if (profileText == null) return;
        String advice = selectedProfile.equals("Roblox")
            ? "Roblox selecionado. Para priorizar estabilidade, ajuste os gráficos dentro do próprio Roblox."
            : "Free Fire selecionado. Para priorizar estabilidade, ajuste os gráficos e FPS nas configurações do próprio jogo.";
        profileText.setText("Perfil atual: " + selectedProfile + "\n" + advice);
    }

    private void refreshStats() {
        if (statsText == null) return;
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(memory);
        long totalMb = memory.totalMem / (1024L * 1024L);
        long freeMb = memory.availMem / (1024L * 1024L);
        int batteryPercent = -1;
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0 && scale > 0) batteryPercent = Math.round(level * 100f / scale);
        }
        float refresh = getWindowManager().getDefaultDisplay().getRefreshRate();
        String batteryText = batteryPercent >= 0 ? batteryPercent + "%" : "indisponível";
        statsText.setText(
            "Aparelho: " + Build.MANUFACTURER + " " + Build.MODEL +
            "\nAndroid: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")" +
            "\nMemória RAM: " + totalMb + " MB total • " + freeMb + " MB disponíveis" +
            "\nNúcleos lógicos informados: " + Runtime.getRuntime().availableProcessors() +
            "\nBateria: " + batteryText +
            String.format(Locale.getDefault(), "\nTaxa de atualização da tela: %.0f Hz", refresh)
        );
    }

    private void refreshTimer() {
        if (timerText == null) return;
        long end = getSharedPreferences(GameTurboService.PREFS, MODE_PRIVATE).getLong(GameTurboService.PREF_TIMER_END, 0L);
        long remaining = end - System.currentTimeMillis();
        if (remaining <= 0) {
            timerText.setText("Nenhum cronômetro ativo.");
        } else {
            long seconds = remaining / 1000L;
            timerText.setText(String.format(Locale.getDefault(), "Sessão em andamento: %02d:%02d restantes", seconds / 60, seconds % 60));
        }
    }

    private void refreshOverlayStatus() {
        if (overlayText == null) return;
        if (Build.VERSION.SDK_INT >= 23 && Settings.canDrawOverlays(this)) {
            overlayText.setText("Permissão concedida. Você pode ativar a bolinha.");
        } else {
            overlayText.setText("Permissão ainda não concedida.");
        }
    }

    private void activateOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY);
        } else {
            runWithNotificationPermission(GameTurboService.ACTION_START_OVERLAY, 0);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY) {
            refreshOverlayStatus();
            if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) {
                runWithNotificationPermission(GameTurboService.ACTION_START_OVERLAY, 0);
            } else {
                Toast.makeText(this, "A permissão de sobreposição não foi concedida.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startSessionTimer() {
        String raw = minutesInput.getText().toString().trim();
        int minutes;
        try {
            minutes = Integer.parseInt(raw);
        } catch (NumberFormatException error) {
            minutes = 0;
        }
        if (minutes < 1 || minutes > 240) {
            minutesInput.setError("Digite de 1 a 240 minutos");
            return;
        }
        runWithNotificationPermission(GameTurboService.ACTION_START_TIMER, minutes);
    }

    private void runWithNotificationPermission(String action, int minutes) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingAction = action;
            pendingMinutes = minutes;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }
        sendServiceCommand(action, minutes);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS && pendingAction != null) {
            String action = pendingAction;
            int minutes = pendingMinutes;
            pendingAction = null;
            pendingMinutes = 0;
            sendServiceCommand(action, minutes);
        }
    }

    private void sendServiceCommand(String action, int minutes) {
        Intent intent = new Intent(this, GameTurboService.class);
        intent.setAction(action);
        intent.putExtra(GameTurboService.EXTRA_MINUTES, minutes);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
        else startService(intent);
        Toast.makeText(this, "Comando enviado.", Toast.LENGTH_SHORT).show();
        refreshOverlayStatus();
        refreshTimer();
    }

    private void launchFreeFire() {
        if (!launchGame("com.dts.freefireth") && !launchGame("com.dts.freefiremax")) {
            Toast.makeText(this, "Free Fire não foi encontrado. Instale o jogo primeiro.", Toast.LENGTH_LONG).show();
        }
    }

    private boolean launchGame(String packageName) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) return false;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
        return true;
    }

    private LinearLayout card(LinearLayout parent, String heading) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(16));
        box.setBackground(roundRect(0xFF151F33, 18));
        parent.addView(box, new LinearLayout.LayoutParams(-1, -2));
        TextView title = makeText(heading, 12, 0xFF47D7A8, true);
        box.addView(title);
        addSpace(box, 10);
        return box;
    }

    private TextView makeText(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(3), 0, dp(7));
        return view;
    }

    private Button makeButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF176B59));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.topMargin = dp(7);
        button.setLayoutParams(lp);
        return button;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private void addSpace(LinearLayout parent, int height) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
