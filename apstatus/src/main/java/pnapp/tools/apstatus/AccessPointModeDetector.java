package pnapp.tools.apstatus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.lang.reflect.Method;

/**
 * Этот ресивер должен получать {@code android.net.wifi.WIFI_AP_STATE_CHANGED} интент от системы
 * и запускать либо останавливать сервер
 *
 * @author P.N. Alekseev
 * @author pnaleks@gmail.com
 * @since 16.12.2015
 */
public class AccessPointModeDetector extends BroadcastReceiver {
    private static Method isWifiApEnabledMethod;
    //private static Method getWifiApConfigurationMethod;

    static {
        try {
            isWifiApEnabledMethod = WifiManager.class.getDeclaredMethod("isWifiApEnabled");
            isWifiApEnabledMethod.setAccessible(true);
            //getWifiApConfigurationMethod = wifiManager.getClass().getDeclaredMethod("getWifiApConfiguration");
            //getWifiApConfigurationMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            //Base.log("AccessPointModeDetector > " + e.getMessage());
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if ( wifiManager == null || isWifiApEnabledMethod == null ) return;

        boolean enabled;
        try {
            enabled = (boolean) isWifiApEnabledMethod.invoke(wifiManager);
        } catch (Exception e) {
            enabled = false;
        }

        if ( enabled ) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            String bssid = wifiInfo.getMacAddress();
            long mac = SocketTask.mac(bssid);
            if ( mac != -1L && mac != 0L ) {
                int n = 0;
                while (n++ < 10) {
                    int ip = SocketTask.getLocalIpAddress(mac);
                    if (ip != -1) {
                        Base.log("AccessPointModeDetector > Mode enabled, BSSID=" + bssid + ", ip=" + SocketTask.ipToString(ip) + ", try " + n + ", starting the server");
                        Uri data = Uri.parse(SocketTask.ipToString(ip));
                        context.startService(new Intent(Server.ACTION_START, data, context, Server.class));
                        break;
                    }
                    try { Thread.sleep(10); } catch (InterruptedException ignore) {}
                }
            }
        } else {
            Base.log("AccessPointModeDetector > Mode disabled, stopping the server");
            context.startService( new Intent(Server.ACTION_EXIT, null, context, Server.class) );
        }

        if ( intent != null && Base.ACTION_LAUNCH.equals(intent.getAction()) ) {
            context.startActivity( new Intent(context, Settings.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) );
        }
    }
}
