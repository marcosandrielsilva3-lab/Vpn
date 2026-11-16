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
import android.os.storage.StorageManager;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import rikka.shizuku.Shizuku;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.system.OsConstants;
import android.app.AlertDialog;
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

import java.io.File;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_OVERLAY = 201;
    private static final int REQUEST_NOTIFICATIONS = 202;
    private static final int REQUEST_MANAGE_STORAGE = 205;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView statsText;
    private TextView overlayText;
    private TextView profileText;
    private TextView timerText;
    private TextView cacheStatusText;
    private TextView shizukuStatusText;
    private TextView advancedResultText;
    private IGameTurboShell shellService;
    private boolean shellServiceBinding = false;
    private String pendingShellAction = null;
    private String pendingShellArgument = null;
    private final ExecutorService shellExecutor = Executors.newSingleThreadExecutor();
    private static final int REQUEST_SHIZUKU_PERMISSION = 501;
    private EditText minutesInput;
    private String selectedProfile = "Roblox";
    private String pendingAction = null;
    private int pendingMinutes = 0;

    private final Shizuku.OnBinderReceivedListener binderReceivedListener =
        () -> runOnUiThread(this::refreshShizukuStatus);
    private final Shizuku.OnBinderDeadListener binderDeadListener =
        () -> runOnUiThread(() -> {
            shellService = null;
            shellServiceBinding = false;
            refreshShizukuStatus();
        });
    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener =
        (requestCode, grantResult) -> {
            if (requestCode == REQUEST_SHIZUKU_PERMISSION) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    bindShellService();
                } else {
                    Toast.makeText(this, "A permissão do Shizuku foi negada.", Toast.LENGTH_LONG).show();
                    refreshShizukuStatus();
                }
            }
        };

    private final ServiceConnection shellServiceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            shellService = IGameTurboShell.Stub.asInterface(binder);
            shellServiceBinding = false;
            refreshShizukuStatus();
            String action = pendingShellAction;
            String argument = pendingShellArgument;
            pendingShellAction = null;
            pendingShellArgument = null;
            if (action != null) executeShellAction(action, argument);
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            shellService = null;
            shellServiceBinding = false;
            refreshShizukuStatus();
        }
    };

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
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
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
        refreshCacheStatus();
        refreshShizukuStatus();
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
        shellExecutor.shutdownNow();
        super.onDestroy();
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
        LinearLayout advancedCard = card(page, "CONTROLE AVANÇADO (SHIZUKU)");
        shizukuStatusText = makeText("Shizuku não conectado.", 14, 0xFFE5ECF8, false);
        advancedCard.addView(shizukuStatusText);
        TextView shizukuHint = makeText(
            "Para limpar caches de vários apps e aplicar redução real de resolução, instale o Shizuku, inicie-o por Depuração sem fio no Android 11+ e autorize o Game Turbo. Não precisa de root; a autorização é concedida pelo próprio Shizuku.",
            13, 0xFF9EACC6, false
        );
        advancedCard.addView(shizukuHint);
        Button installShizuku = makeButton("Instalar / abrir Shizuku");
        Button connectShizuku = makeButton("Conectar e autorizar");
        advancedCard.addView(installShizuku);
        advancedCard.addView(connectShizuku);
        installShizuku.setOnClickListener(v -> openShizukuApp());
        connectShizuku.setOnClickListener(v -> connectShizuku());
        refreshShizukuStatus();

        addSpace(page, 14);
        LinearLayout optimizationCard = card(page, "OTIMIZAÇÃO E LIMPEZA");
        TextView optimizationHint = makeText(
            "A limpeza em massa usa o Package Manager pelo Shizuku para solicitar ao Android a remoção dos caches dos aplicativos, sem apagar os dados pessoais. É possível ajustar a resolução geral e, em aparelhos compatíveis, a renderização por jogo.",
            13, 0xFF9EACC6, false
        );
        optimizationCard.addView(optimizationHint);
        cacheStatusText = makeText("", 14, 0xFFE5ECF8, false);
        optimizationCard.addView(cacheStatusText);
        Button clearTurboCache = makeButton("Limpar cache do próprio Game Turbo");
        Button clearAllCache = makeButton("Limpar cache dos aplicativos");
        Button manageStorage = makeButton("Gerenciar armazenamento");
        Button manageApps = makeButton("Gerenciar apps / segundo plano");
        Button displaySettings = makeButton("Abrir resolução e tela");
        Button batterySettings = makeButton("Abrir configurações de bateria");
        optimizationCard.addView(clearTurboCache);
        optimizationCard.addView(clearAllCache);
        optimizationCard.addView(manageStorage);
        optimizationCard.addView(manageApps);
        optimizationCard.addView(displaySettings);
        optimizationCard.addView(batterySettings);
        clearTurboCache.setOnClickListener(v -> clearOwnCache());
        clearAllCache.setOnClickListener(v -> requestShellAction("trim", null));
        manageStorage.setOnClickListener(v -> openSystemStorageManager());
        manageApps.setOnClickListener(v -> openAppManagement());
        displaySettings.setOnClickListener(v -> openDisplaySettings());
        batterySettings.setOnClickListener(v -> openBatterySettings());
        refreshCacheStatus();

        addSpace(page, 14);
        LinearLayout resolutionCard = card(page, "RESOLUÇÃO GERAL DA TELA");
        TextView resolutionHint = makeText(
            "Ajusta o tamanho lógico da tela para todos os aplicativos. A imagem fica mais borrada e a interface pode ficar menor. Use Restaurar para voltar ao estado anterior.",
            13, 0xFF9EACC6, false
        );
        resolutionCard.addView(resolutionHint);
        Button resolution80 = makeButton("Reduzir resolução para 80%");
        Button resolution70 = makeButton("Reduzir resolução para 70%");
        Button resolutionReset = makeButton("Restaurar resolução original");
        resolutionCard.addView(resolution80);
        resolutionCard.addView(resolution70);
        resolutionCard.addView(resolutionReset);
        resolution80.setOnClickListener(v -> requestShellAction("display", "0.8"));
        resolution70.setOnClickListener(v -> requestShellAction("display", "0.7"));
        resolutionReset.setOnClickListener(v -> requestShellAction("display_reset", null));

        addSpace(page, 14);
        LinearLayout gameScaleCard = card(page, "REDUZIR RESOLUÇÃO DE RENDERIZAÇÃO DOS JOGOS");
        TextView gameScaleHint = makeText(
            "O Android pode reduzir o buffer gráfico para 70% em jogos compatíveis (Android 13+ e suporte do fabricante/jogo). Reinicie o jogo após aplicar. Isso reduz a resolução renderizada, mas não substitui os arquivos de textura.",
            13, 0xFF9EACC6, false
        );
        gameScaleCard.addView(gameScaleHint);
        Button robloxDown = makeButton("Roblox: reduzir renderização (70%)");
        Button robloxRestore = makeButton("Roblox: restaurar padrão");
        Button freeFireDown = makeButton("Free Fire: reduzir renderização (70%)");
        Button freeFireRestore = makeButton("Free Fire: restaurar padrão");
        gameScaleCard.addView(robloxDown);
        gameScaleCard.addView(robloxRestore);
        gameScaleCard.addView(freeFireDown);
        gameScaleCard.addView(freeFireRestore);
        robloxDown.setOnClickListener(v -> requestShellAction("game_apply", "com.roblox.client"));
        robloxRestore.setOnClickListener(v -> requestShellAction("game_restore", "com.roblox.client"));
        freeFireDown.setOnClickListener(v -> requestShellAction("game_apply", "com.dts.freefireth"));
        freeFireRestore.setOnClickListener(v -> requestShellAction("game_restore", "com.dts.freefireth"));

        advancedResultText = makeText("Nenhuma operação avançada executada.", 13, 0xFFE5ECF8, false);
        gameScaleCard.addView(advancedResultText);

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
            "Importante: o controle avançado depende do Shizuku ativo. A redução por jogo só funciona se o Android e o jogo aceitarem a intervenção; o Game Turbo não troca arquivos de textura dos jogos. Use os botões Restaurar para desfazer as alterações.",
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

    private String getResolutionText() {
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        return metrics.widthPixels + " × " + metrics.heightPixels + " px";
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
        } else if (requestCode == REQUEST_MANAGE_STORAGE) {
            Toast.makeText(this, resultCode == RESULT_OK ? "Armazenamento atualizado." : "Gerenciamento de armazenamento fechado.", Toast.LENGTH_SHORT).show();
        }
        refreshCacheStatus();
    }

    private void openShizukuApp() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if (launch != null) {
            startActivity(launch);
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")));
        } catch (Exception error) {
            Toast.makeText(this, "Não foi possível abrir a página do Shizuku.", Toast.LENGTH_LONG).show();
        }
    }

    private void connectShizuku() {
        if (!Shizuku.pingBinder()) {
            new AlertDialog.Builder(this)
                .setTitle("Shizuku não está iniciado")
                .setMessage("Instale e abra o Shizuku. No Android 11 ou superior, inicie o serviço usando Depuração sem fio. Depois volte ao Game Turbo e toque em Conectar e autorizar.")
                .setPositiveButton("Abrir Shizuku", (dialog, which) -> openShizukuApp())
                .setNegativeButton("Fechar", null)
                .show();
            return;
        }
        if (Shizuku.isPreV11()) {
            Toast.makeText(this, "Atualize o Shizuku para uma versão recente.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                bindShellService();
            } else {
                Shizuku.requestPermission(REQUEST_SHIZUKU_PERMISSION);
            }
        } catch (Throwable error) {
            Toast.makeText(this, "Falha ao verificar a autorização do Shizuku: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void bindShellService() {
        if (shellService != null || shellServiceBinding) return;
        try {
            Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                new ComponentName(getPackageName(), GameTurboShellService.class.getName())
            ).tag("gameturbo-shell").daemon(false).processNameSuffix("shell").version(1);
            shellServiceBinding = true;
            Shizuku.bindUserService(args, shellServiceConnection);
            Toast.makeText(this, "Conectando ao controle avançado…", Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            shellServiceBinding = false;
            Toast.makeText(this, "Não foi possível conectar ao serviço avançado: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshShizukuStatus() {
        if (shizukuStatusText == null) return;
        if (!Shizuku.pingBinder()) {
            shizukuStatusText.setText("Shizuku: não iniciado ou desconectado.");
        } else {
            try {
                int uid = Shizuku.getUid();
                String identity = uid == 0 ? "root" : (uid == 2000 ? "ADB / shell" : "UID " + uid);
                boolean granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
                shizukuStatusText.setText(
                    "Shizuku conectado (" + identity + ").\nAutorização do Game Turbo: " +
                    (granted ? "concedida" : "pendente") +
                    (shellService != null ? "\nServiço de comandos: pronto" : "")
                );
            } catch (Throwable error) {
                shizukuStatusText.setText("Shizuku detectado, mas ainda não foi autorizado.");
            }
        }
        refreshCacheStatus();
    }

    private void requestShellAction(String action, String argument) {
        if (!Shizuku.pingBinder()) {
            new AlertDialog.Builder(this)
                .setTitle("Controle avançado necessário")
                .setMessage("Esta função precisa do Shizuku ativo. Não é root: o Shizuku fornece acesso ADB/shell autorizado pelo próprio Android. Instale-o e inicie-o pela Depuração sem fio no Android 11+.")
                .setPositiveButton("Abrir Shizuku", (dialog, which) -> openShizukuApp())
                .setNegativeButton("Cancelar", null)
                .show();
            return;
        }
        if (Shizuku.isPreV11()) {
            Toast.makeText(this, "A versão do Shizuku é antiga demais. Atualize o aplicativo Shizuku.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                pendingShellAction = action;
                pendingShellArgument = argument;
                Shizuku.requestPermission(REQUEST_SHIZUKU_PERMISSION);
                return;
            }
            pendingShellAction = action;
            pendingShellArgument = argument;
            bindShellService();
            if (shellService != null) {
                String pending = pendingShellAction;
                String pendingArg = pendingShellArgument;
                pendingShellAction = null;
                pendingShellArgument = null;
                executeShellAction(pending, pendingArg);
            }
        } catch (Throwable error) {
            Toast.makeText(this, "Não foi possível pedir a autorização do Shizuku: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void executeShellAction(String action, String argument) {
        if (shellService == null || action == null) return;
        shellExecutor.execute(() -> {
            String result;
            try {
                if ("trim".equals(action)) {
                    result = shellService.trimAllCaches();
                } else if ("display".equals(action)) {
                    String saved = getPreferences(MODE_PRIVATE).getString("original_display_override", null);
                    if (saved == null) {
                        String current = shellService.getDisplaySize();
                        if (!isShellSuccess(current)) {
                            result = current;
                            postShellResult(result);
                            return;
                        }
                        saved = shellOutput(current);
                        getPreferences(MODE_PRIVATE).edit().putString("original_display_override", saved).apply();
                    }
                    result = shellService.applyDisplayScale(Float.parseFloat(argument));
                } else if ("display_reset".equals(action)) {
                    String saved = getPreferences(MODE_PRIVATE).getString("original_display_override", null);
                    if (saved == null) {
                        result = "A resolução ainda não foi alterada pelo Game Turbo.";
                    } else {
                        result = shellService.restoreDisplaySize(saved);
                        if (isShellSuccess(result)) getPreferences(MODE_PRIVATE).edit().remove("original_display_override").apply();
                    }
                } else if ("game_apply".equals(action)) {
                    String pkg = argument;
                    String key = "game_overlay_original_" + pkg;
                    if (!getPreferences(MODE_PRIVATE).contains(key)) {
                        String old = shellService.getGameOverlayConfig(pkg);
                        if (!isShellSuccess(old)) {
                            result = old;
                            postShellResult(result);
                            return;
                        }
                        String oldValue = shellOutput(old).trim();
                        getPreferences(MODE_PRIVATE).edit().putString(key, oldValue.isEmpty() ? "__NONE__" : oldValue).apply();
                    }
                    result = shellService.applyGameDownscale(pkg, 0.7f);
                } else if ("game_restore".equals(action)) {
                    String pkg = argument;
                    String key = "game_overlay_original_" + pkg;
                    String old = getPreferences(MODE_PRIVATE).getString(key, null);
                    if (old == null) {
                        result = "Não há alteração do Game Turbo salva para restaurar neste jogo.";
                    } else {
                        result = shellService.restoreGameDownscale(pkg, old);
                        if (isShellSuccess(result)) getPreferences(MODE_PRIVATE).edit().remove(key).apply();
                    }
                } else {
                    result = "Ação desconhecida.";
                }
            } catch (Throwable error) {
                result = "Erro: " + error.getClass().getSimpleName() + " — " + error.getMessage();
            }
            postShellResult(result);
        });
    }

    private boolean isShellSuccess(String result) {
        return result != null && result.startsWith("EXIT:0");
    }

    private String shellOutput(String result) {
        if (result == null) return "";
        int newline = result.indexOf('\n');
        return newline >= 0 ? result.substring(newline + 1) : "";
    }

    private void postShellResult(String result) {
        handler.post(() -> {
            if (advancedResultText != null) {
                advancedResultText.setText(result == null ? "Sem retorno do Android." : result);
            }
            boolean ok = isShellSuccess(result);
            Toast.makeText(this, ok ? "Comando do Android executado." : "O Android não confirmou a operação.", Toast.LENGTH_LONG).show();
            refreshShizukuStatus();
        });
    }

    private void openSystemStorageManager() {
        try {
            if (Build.VERSION.SDK_INT >= 25) {
                Intent intent = new Intent(StorageManager.ACTION_MANAGE_STORAGE);
                intent.putExtra(StorageManager.EXTRA_REQUESTED_BYTES, 256L * 1024L * 1024L);
                startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
            } else {
                startActivity(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
            }
        } catch (Exception error) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
                Toast.makeText(this, "Configurações de armazenamento indisponíveis.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openAppManagement() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
        } catch (Exception error) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void openDisplaySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
        } catch (Exception error) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
                Toast.makeText(this, "Configurações de tela indisponíveis.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS));
        } catch (Exception error) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void clearOwnCache() {
        long before = getOwnCacheSize();
        deleteDirectoryContents(getCacheDir());
        File externalCache = getExternalCacheDir();
        if (externalCache != null) deleteDirectoryContents(externalCache);
        long removed = Math.max(0L, before - getOwnCacheSize());
        refreshCacheStatus();
        Toast.makeText(this, "Cache do Game Turbo limpo: " + formatBytes(removed), Toast.LENGTH_LONG).show();
    }

    private void refreshCacheStatus() {
        if (cacheStatusText == null) return;
        cacheStatusText.setText(
            "Cache temporário do Game Turbo: " + formatBytes(getOwnCacheSize()) +
            "\nLimpeza dos outros apps: " +
            (Shizuku.pingBinder() && shellService != null ? "controle avançado conectado" : "precisa do Shizuku ativo e autorizado")
        );
    }

    private long getOwnCacheSize() {
        long total = directorySize(getCacheDir());
        File externalCache = getExternalCacheDir();
        if (externalCache != null) total += directorySize(externalCache);
        return total;
    }

    private long directorySize(File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return file.length();
        long total = 0L;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) total += directorySize(child);
        }
        return total;
    }

    private void deleteDirectoryContents(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                deleteDirectoryContents(child);
                child.delete();
            } else {
                child.delete();
            }
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
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
