package com.cloone.btguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

/** Polls the Wi-Fi Direct group every few seconds and applies {@link Policy} to Bluetooth. */
public class GuardService extends Service {

    private static final String TAG = "BtGuard";
    private static final long POLL_MS = 4000L;
    private static final long OFF_AFTER_MS = 20000L;  // phone has been in the group this long
    private static final long ON_AFTER_MS = 15000L;   // group has been empty this long

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Policy policy = new Policy(OFF_AFTER_MS, ON_AFTER_MS);
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private boolean lastSeen;
    private boolean logged;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            ask();
            handler.postDelayed(this, POLL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        goForeground();
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        if (manager != null) {
            channel = manager.initialize(this, Looper.getMainLooper(), null);
        }
        if (manager == null || channel == null) {
            Log.w(TAG, "no Wi-Fi Direct on this unit; guard idle");
            stopSelf();
            return;
        }
        Log.i(TAG, "started");
        handler.post(poll);
    }

    /** Required after startForegroundService(); a low-importance, silent notice. */
    private void goForeground() {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel("guard", "Bluetooth Guard",
                    NotificationManager.IMPORTANCE_MIN));
            b = new Notification.Builder(this, "guard");
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle("Bluetooth Guard").setContentText("Bluetooth off while Android Auto runs on Wi-Fi")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth);
        startForeground(2001, b.build());
    }

    private void ask() {
        try {
            manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                @Override
                public void onGroupInfoAvailable(WifiP2pGroup group) {
                    boolean phone = group != null && group.isGroupOwner()
                            && group.getClientList() != null && !group.getClientList().isEmpty();
                    if (!logged || phone != lastSeen) {
                        Log.i(TAG, "phone in Wi-Fi Direct group: " + phone);
                        logged = true;
                        lastSeen = phone;
                    }
                    apply(policy.update(phone, SystemClock.elapsedRealtime()));
                }
            });
        } catch (RuntimeException error) {
            Log.w(TAG, "could not read the group", error);
        }
    }

    private void apply(Policy.Action action) {
        if (action == Policy.Action.NONE) {
            return;
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return;
        }
        try {
            if (action == Policy.Action.BLUETOOTH_OFF && adapter.isEnabled()) {
                Log.i(TAG, "Android Auto is on Wi-Fi: switching Bluetooth off");
                adapter.disable();
            } else if (action == Policy.Action.BLUETOOTH_ON && !adapter.isEnabled()) {
                Log.i(TAG, "phone left: switching Bluetooth on for the next connection");
                adapter.enable();
            }
        } catch (SecurityException error) {
            Log.w(TAG, "Bluetooth change refused", error);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Every startForegroundService() must be answered with startForeground() within 5 s.
        goForeground();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(poll);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
