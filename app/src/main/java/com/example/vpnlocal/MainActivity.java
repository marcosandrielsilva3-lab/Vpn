package com.example.vpnlocal;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int VPN_REQUEST = 100;
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
        connect.setOnClickListener(v -> {
            Intent prepare = VpnService.prepare(this);
            if (prepare != null) startActivityForResult(prepare, VPN_REQUEST);
            else onActivityResult(VPN_REQUEST, RESULT_OK, null);
        });
        layout.addView(connect);

        Button disconnect = new Button(this);
        disconnect.setText("Desconectar");
        disconnect.setOnClickListener(v -> {
            stopService(new Intent(this, LocalVpnService.class));
            status.setText("Desconectado");
        });
        layout.addView(disconnect);

        TextView note = new TextView(this);
        note.setText("Demonstração local: não usa servidor, não muda seu IP público e não protege o tráfego.");
        note.setTextSize(14);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.setMargins(0, 28, 0, 0);
        layout.addView(note, noteParams);

        setContentView(layout);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST && resultCode == RESULT_OK) {
            startService(new Intent(this, LocalVpnService.class));
            status.setText("Conectado (demonstração local)");
        }
    }
}
