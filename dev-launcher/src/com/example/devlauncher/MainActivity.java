package com.example.devlauncher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import java.util.Enumeration;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public class MainActivity extends Activity {
    private LinearLayout root;
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        scroll.addView(root);
        setContentView(scroll);

        addButton("Developer options", new Runnable() {
            public void run() { openDeveloper(); }
        });
        addButton("Wireless debugging page (Android 11+ only)", new Runnable() {
            public void run() { openWirelessDebugging(); }
        });
        addButton("Enable ADB over Wi-Fi (port 5555, needs root)", new Runnable() {
            public void run() { enableTcpAdb(); }
        });
        addButton("Show my IP address", new Runnable() {
            public void run() { status.setText(ipInfo()); }
        });
        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(8, 8, 8, 16);
        root.addView(status);
        addButton("Settings", action(Settings.ACTION_SETTINGS));
        addButton("Device info (tap Build number here)", action(Settings.ACTION_DEVICE_INFO_SETTINGS));
        addButton("Wi-Fi settings", action(Settings.ACTION_WIFI_SETTINGS));
        addButton("Apps settings", action(Settings.ACTION_APPLICATION_SETTINGS));
        addButton("Input method settings", action(Settings.ACTION_INPUT_METHOD_SETTINGS));

        TextView hint = new TextView(this);
        hint.setText("\nCustom component (package / class):");
        root.addView(hint);
        final EditText pkg = new EditText(this);
        pkg.setHint("com.android.settings");
        pkg.setSingleLine(true);
        root.addView(pkg);
        final EditText cls = new EditText(this);
        cls.setHint("com.android.settings.DevelopmentSettings");
        cls.setSingleLine(true);
        root.addView(cls);
        addButton("Launch custom", new Runnable() {
            public void run() {
                start(new Intent().setComponent(new ComponentName(
                        pkg.getText().toString().trim(), cls.getText().toString().trim())));
            }
        });
    }

    private Runnable action(final String a) {
        return new Runnable() {
            public void run() { start(new Intent(a)); }
        };
    }

    private void openDeveloper() {
        Intent[] tries = {
            new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            new Intent().setComponent(new ComponentName("com.android.settings",
                    "com.android.settings.DevelopmentSettings")),
            new Intent().setComponent(new ComponentName("com.android.settings",
                    "com.android.settings.Settings$DevelopmentSettingsActivity")),
            new Intent().setComponent(new ComponentName("com.android.settings",
                    "com.android.settings.development.DevelopmentSettingsActivity")),
        };
        for (Intent i : tries) {
            if (start(i)) return;
        }
    }

    private void openWirelessDebugging() {
        Intent[] tries = {
            new Intent("android.settings.ADB_WIRELESS_SETTINGS"),
            new Intent().setComponent(new ComponentName("com.android.settings",
                    "com.android.settings.Settings$WirelessDebuggingActivity")),
        };
        for (Intent i : tries) {
            if (start(i)) return;
        }
    }

    private String ipInfo() {
        StringBuilder sb = new StringBuilder();
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            if (nis != null) {
                for (NetworkInterface ni : Collections.list(nis)) {
                    for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                        if (!a.isLoopbackAddress() && a.getAddress().length == 4) {
                            sb.append(ni.getName()).append(": ").append(a.getHostAddress()).append("\n");
                        }
                    }
                }
            }
        } catch (Exception e) {
            sb.append("Interface lookup failed: ").append(e).append("\n");
        }
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) {
                sb.append("WifiManager: none\n");
            } else {
                WifiInfo wi = wm.getConnectionInfo();
                int ip = wi == null ? 0 : wi.getIpAddress();
                sb.append("Wi-Fi enabled: ").append(wm.isWifiEnabled()).append("\n");
                sb.append("Wi-Fi state: ").append(wi == null ? "unknown" : wi.getSupplicantState()).append("\n");
                if (ip != 0) {
                    sb.append("Wi-Fi IP: ").append(ip & 255).append('.').append((ip >> 8) & 255)
                      .append('.').append((ip >> 16) & 255).append('.').append((ip >> 24) & 255).append("\n");
                }
            }
        } catch (Exception e) {
            sb.append("WifiManager failed: ").append(e).append("\n");
        }
        return sb.length() == 0 ? "No network info" : sb.toString();
    }

    private void enableTcpAdb() {
        status.setText("Requesting root...");
        new Thread(new Runnable() {
            public void run() {
                final String result = runSu();
                runOnUiThread(new Runnable() {
                    public void run() { status.setText(result + "\n" + ipInfo()
                            + "\nOn the Mac: adb connect <IP>:5555"); }
                });
            }
        }).start();
    }

    private String runSu() {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("setprop service.adb.tcp.port 5555\n");
            os.writeBytes("stop adbd\n");
            os.writeBytes("start adbd\n");
            os.writeBytes("getprop service.adb.tcp.port\n");
            os.writeBytes("exit\n");
            os.flush();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line, last = "";
            while ((line = r.readLine()) != null) last = line;
            p.waitFor();
            return "Root OK. adb tcp port = " + last;
        } catch (Exception e) {
            return "Root failed: " + e.getMessage();
        }
    }

    private boolean start(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "Failed: " + e.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void addButton(String label, final Runnable r) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { r.run(); }
        });
        root.addView(btn);
    }
}
