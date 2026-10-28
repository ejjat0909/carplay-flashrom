import android.net.wifi.WifiConfiguration;
import android.os.IBinder;
import android.system.Os;
import java.lang.reflect.Method;

/** Root helper: start/stop the Wi-Fi hotspot (tethering) on Android 8.1 via the system services.
 *  Usage: app_process -Djava.class.path=hotspot.jar /system/bin Hotspot start <ssid> <pass> | stop | status */
public class Hotspot {
    static Object svc(String name, String stub) throws Exception {
        Class<?> sm = Class.forName("android.os.ServiceManager");
        IBinder b = (IBinder) sm.getMethod("getService", String.class).invoke(null, name);
        if (b == null) throw new IllegalStateException("service not found: " + name);
        return Class.forName(stub).getMethod("asInterface", IBinder.class).invoke(null, b);
    }

    static Method find(Object o, String name) {
        for (Method m : o.getClass().getMethods()) if (m.getName().equals(name)) return m;
        throw new IllegalStateException("no method " + name);
    }

    public static void main(String[] a) throws Exception {
        String cmd = a.length > 0 ? a[0] : "status";
        // Android refuses Wi-Fi AP config from uid 0 ("App not allowed to read or update stored WiFi AP
        // config"); the system user (1000) is allowed. We start as root, so drop to it.
        Os.setgid(1000);
        Os.setuid(1000);
        Object wifi = svc("wifi", "android.net.wifi.IWifiManager$Stub");
        Object conn = svc("connectivity", "android.net.IConnectivityManager$Stub");
        if (cmd.equals("start")) {
            WifiConfiguration c = new WifiConfiguration();
            c.SSID = a[1];
            c.preSharedKey = a[2];
            c.allowedKeyManagement.set(4); // WPA2_PSK
            c.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            try { WifiConfiguration.class.getField("apBand").setInt(c, 0); } catch (Throwable ignored) { }
            Method set = find(wifi, "setWifiApConfiguration");
            if (set.getParameterTypes().length == 2) set.invoke(wifi, c, "android");
            else set.invoke(wifi, c);
            Method st = find(conn, "startTethering");
            // (int type, ResultReceiver receiver, boolean showProvisioningUi, String callerPkg)
            st.invoke(conn, 0, null, false, "android");
            System.out.println("hotspot start requested: " + a[1]);
        } else if (cmd.equals("stop")) {
            find(conn, "stopTethering").invoke(conn, 0, "android");
            System.out.println("hotspot stop requested");
        } else {
            Method s = find(wifi, "getWifiApEnabledState");
            System.out.println("ap state (11=disabled,13=enabled): " + s.invoke(wifi));
        }
    }
}
