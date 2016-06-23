package pnapp.tools.apstatus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;

/**
 * При обнаружении поддерживаемого сетевого подключения запускает сервер на соответствующем подключении.
 * Есди подключение не поддерживается, сервер выключается. Если сеть недоступна - ничего делать не нужно,
 * возможно, соединение скоро будет восстановлено.
 *
 * @author pnaleks@gmail.com
 * @since 16.12.2015
 */
public class ConnectivityDetector extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();


        String listenHost = null;

        if ( networkInfo == null || !networkInfo.isConnected() ) {
            Base.log("ConnectivityDetector > Network unavailable, action ignored");
        } else if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            // Поделючение по WiFi
            DhcpInfo dhcpInfo = ((WifiManager) context.getSystemService(Context.WIFI_SERVICE)).getDhcpInfo();
            if (dhcpInfo.ipAddress != 0) {
                listenHost = Base.ipToString(dhcpInfo.ipAddress);
                Base.log("ConnectivityDetector > WiFi detected, ip = " + listenHost + ", starting the server");
            } else {
                Base.log("ConnectivityDetector > WiFi detected, ip not attached, stopping the server");
            }
        } else {
            Base.log("ConnectivityDetector > Detected unsupported network " + networkInfo.getTypeName() + ", stopping the server");
        }

        Uri data = listenHost == null ? null : Uri.parse( listenHost );
        context.startService( new Intent(Server.ACTION_START, data, context, Server.class) );
    }
}
