import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import java.lang.reflect.Method;
import java.util.Set;

/** Root helper (app_process): list or remove Bluetooth bonds on the head unit.
 *  app_process -Djava.class.path=bt.jar /system/bin BtTool list | unbond-all */
public class BtTool {
    public static void main(String[] a) throws Exception {
        String cmd = a.length > 0 ? a[0] : "list";
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null) { System.out.println("no adapter"); return; }
        System.out.println("adapter " + ad.getName() + " " + ad.getAddress() + " state=" + ad.getState()
                + " scanMode=" + ad.getScanMode());
        Set<BluetoothDevice> set = ad.getBondedDevices();
        System.out.println("bonded: " + set.size());
        for (BluetoothDevice d : set) {
            System.out.println("  " + d.getName() + " " + d.getAddress());
            if (cmd.equals("unbond-all")) {
                Method m = BluetoothDevice.class.getMethod("removeBond");
                System.out.println("    removeBond -> " + m.invoke(d));
            }
        }
        try { Thread.sleep(1500); } catch (InterruptedException ignored) { }
        System.out.println("bonded after: " + ad.getBondedDevices().size());
    }
}
