package com.example.vpnlocal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import java.io.IOException;

public class LocalVpnService extends VpnService {
    private static final String CHANNEL = "local_vpn_status";
    private ParcelFileDescriptor tunnel;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification;
        if (Build.VERSION.SDK_INT >= 26) {
            notification = new Notification.Builder(this, CHANNEL)
                .setContentTitle("VPN Local Demo")
                .setContentText("Demonstração VPN local ativa")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .build();
        } else {
            notification = new Notification.Builder(this)
                .setContentTitle("VPN Local Demo")
                .setContentText("Demonstração VPN local ativa")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .build();
        }
        startForeground(1, notification);

        try {
            if (tunnel != null) tunnel.close();
            Builder builder = new Builder()
                .setSession("VPN Local Demo")
                .addAddress("10.0.0.2", 32)
                .addRoute("192.0.2.1", 32);
            builder.addAllowedApplication(getPackageName());
            tunnel = builder.establish();
            if (tunnel == null) stopSelf();
        } catch (Exception e) {
            stopSelf();
        }
        return START_NOT_STICKY;
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
        try {
            if (tunnel != null) tunnel.close();
        } catch (IOException ignored) {}
        stopForeground(true);
        super.onDestroy();
    }
}
