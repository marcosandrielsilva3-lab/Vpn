package com.example.vpnlocal;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int VPN_REQUEST = 100;
    private static final int OVERLAY_REQUEST = 101;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("VPN Local Demo");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        status = new TextView(this);
        status.setText("Desconectado");
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.setMargins(0, 24, 0, 24);
        layout.addView(status, statusParams);

        Button connect = new Button(this);
        connect.setText("Conectar");
        connect.setOnClickListener(v -> checkOverlayPermission());
        layout.addView(connect);

        Button disconnect = new Button(this);
        disconnect.setText("Desconectar");
        disconnect.setOnClickListener(v -> {
            stopService(new Intent(this, LocalVpnService.class));
            status.setText("Desconectado");
        });
        layout.addView(disconnect);

        TextView note = new TextView(this);
        note.setText("A bolinha flutuante ativa uma simulação de bloqueio de internet. Ela não cria ping real de 1000 ms.");
        note.setTextSize(14);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.setMargins(0, 28, 0, 0);
        layout.addView(note, noteParams);

        setContentView(layout);
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(intent, OVERLAY_REQUEST);
        } else {
            requestVpnPermission();
        }
    }

    private void requestVpnPermission() {
        Intent prepare = VpnService.prepare(this);
        if (prepare != null) {
            startActivityForResult(prepare, VPN_REQUEST);
        } else {
            startVpnService();
        }
    }

    private void startVpnService() {
        Intent service = new Intent(this, LocalVpnService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }
        status.setText("Conectado — bolinha flutuante ativa");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_REQUEST) {
            if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) {
                requestVpnPermission();
            } else {
                status.setText("Permissão da bolinha não concedida");
            }
        } else if (requestCode == VPN_REQUEST) {
            if (resultCode == RESULT_OK) {
                startVpnService();
            } else {
                status.setText("Permissão de VPN não concedida");
            }
        }
    }
}
