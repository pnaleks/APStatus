package pnapp.tools.apstatus;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.util.Base64;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.HashMap;

import javax.crypto.Cipher;

/**
 * @author P.N. Alekseev
 * @author pnaleks@gmail.com
 */
public class Client extends Base {
    public static final long WATCHDOG_TIMEOUT_MILLIS = 120_000L;
    public static final long CONNECT_DELAY_MILLIS = 10_000L;

    public static final String DEFAULT_CLIENT_NAME =
            Build.MODEL.contains(Build.MANUFACTURER) ? Build.MODEL : Build.MANUFACTURER + ' ' + Build.MODEL;
    private String clientName;
    private PrivateKey clientKey;

    /** Идентификатор уведомления для {@link #postDefaultNotification(Intent)} */
    private static final int NOTIFICATION_ID_DEFAULT = 99;

    /** Счетчик идентификаторов уведомлений для {@link Client.RemoteNotification} */
    public static int lastNotificationId = NOTIFICATION_ID_DEFAULT + 1;


    private Supplicant mSupplicant;

    private String serverHost;
    private int serverPort;
    private String serverName;

    private ClientBitmapMaker bitmapMaker;
    public static ArrayList<RemoteNotification> remoteNotifications = new ArrayList<>();

    private boolean enabled;
    private boolean started;

    public static final HashMap<String,Integer> iconsMap = new HashMap<String,Integer>(){{
        put(NotificationCompat.CATEGORY_CALL, R.drawable.ic_call_white_24dp);
        put(NotificationCompat.CATEGORY_EMAIL, R.drawable.ic_email_white_24dp);
        put(NotificationCompat.CATEGORY_MESSAGE, R.drawable.ic_message_white_24dp);
        put(null, R.drawable.ic_apstatus_a_24dp);
    }};

    public Client() {}

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = (intent == null) ? null : intent.getAction();

        if ( action == null ) {
            log("Client > Warning! Started with null intent or its action!");
            return START_NOT_STICKY;
        }
        log("Client > Has gotten " + action + " intent");

        if ( ACTION_EXIT.equals(action) ) {
            log("Client > Exiting");
            stop();
            return START_NOT_STICKY;
        }

        enabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Settings.PREF_CLIENT_MODE, false);
        if ( !enabled ) {
            log("Client > Has been disabled by the settings. Shutting down!");
            stop();
            return START_NOT_STICKY;
        }

        switch (action) {
            case ACTION_LAUNCH:
                if ( started ) {
                    startActivity(new Intent(this, Settings.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return START_NOT_STICKY;
                }
            case ACTION_START:
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
                start();
            case ConnectivityManager.CONNECTIVITY_ACTION:
                break;
        }

        if ( started ) {
            checkConnection();
        } else {
            log("Client > Not started, action ignored!");
            stop();
        }

        return START_NOT_STICKY;
    }

    protected void checkConnection() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

        if ( networkInfo == null || !networkInfo.isConnected() ) {
            log("Client > Network unavailable");
            stopSupplicant();
            return;
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        String remoteHost = null;
        if ( preferences.getBoolean(Settings.PREF_CONNECT_GATEWAY, true) ) {
            if ( networkInfo.getType() == ConnectivityManager.TYPE_WIFI ) {
                DhcpInfo dhcpInfo = ((WifiManager) getSystemService(WIFI_SERVICE)).getDhcpInfo();
                if ( dhcpInfo.gateway != 0 ) remoteHost = ipToString( dhcpInfo.gateway );
            }
        } else {
            remoteHost = preferences.getString(Settings.PREF_CONNECT_ADDRESS, LOOPBACK);
            if ( networkInfo.getType() == ConnectivityManager.TYPE_WIFI ) {
                DhcpInfo dhcpInfo = ((WifiManager) getSystemService(WIFI_SERVICE)).getDhcpInfo();
                if ( dhcpInfo.ipAddress == 0 ) remoteHost = null;
            }
        }

        if ( remoteHost == null ) {
            log("Client > Unsupported network type : " + networkInfo.getTypeName());
            stopSupplicant();
            return;
        }

        serverHost = remoteHost;
        try {
            serverPort = Integer.valueOf(preferences.getString(Settings.PREF_CONNECT_PORT, "5444"));
        } catch ( NumberFormatException e ) {
            serverPort = 5444;
        }

        startSupplicant(serverHost, serverPort, clientName, clientKey);
    }

    protected void start() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        clientName = preferences.getString(Settings.PREF_CLIENT_NAME, DEFAULT_CLIENT_NAME);
        clientKey = null;

        String key = preferences.getString(Settings.CLIENT_KEY, null);
        if (key != null) {
            try {
                clientKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(key, Base64.DEFAULT)));
            } catch (Exception e) {
                log(e.toString());
            }
        }

        if ( !started ) {
            bitmapMaker = new ClientBitmapMaker(this);
            postDefaultNotification(null);
        }
    }

    protected void stop() {
        updateComponentsState(this, PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        started = false;
        stopSupplicant();
        stopForeground(true);
        bitmapMaker = null;
        if ( LOOPBACK.equals(serverHost) ) startService( new Intent(ACTION_EXIT, null, this, Server.class) );
        stopSelf();
    }


    @Override
    protected void onApproved(SocketTask task) {
        log("Client > Approved with " + mSupplicant.toString());

        if ( mSupplicant.getKey() != clientKey ) {
            clientKey = (PrivateKey) mSupplicant.getKey();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit()
                    .putString(Settings.CLIENT_KEY, Base64.encodeToString(clientKey.getEncoded(), Base64.DEFAULT))
                    .apply();
        }

        setWatchdog(WATCHDOG_TIMEOUT_MILLIS);
    }

    @Override
    protected void onRead(Intent intent) {
        String action = (intent == null) ? null : intent.getAction();
        if ( action == null ) {
            log("Client > Null intent or action from " + mSupplicant.getHost());
            return;
        }

        log("Client > Has read " + action + " from " + mSupplicant.toString());

        serverName = intent.getStringExtra(EXTRA_NAME);

        RemoteNotification item;
        switch (action) {
            case ACTION_UPDATE:
                postDefaultNotification(intent);
                break;
            case ACTION_POST:
                item = RemoteNotification.get(intent);
                if ( item != null ) {
                    postRemoteNotification(item, intent);
                }
                break;
            case ACTION_REMOVE:
                item = RemoteNotification.remove(intent);
                if ( item != null ) {
                    ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(item.getId());
                    log("Client > Notification removed (" + item.remotePackage + " : " + item.getId() + ')');
                }
                break;
            default:
                log("Client > Intent with unexpected action '" + action + "' from " + mSupplicant.toString());
        }

        setWatchdog(WATCHDOG_TIMEOUT_MILLIS);
    }

    @Override
    protected void onError(SocketTask task) {
        setWatchdog(CONNECT_DELAY_MILLIS);
    }

    @Override
    protected void onWatchdog() {
        if ( isConnected() ) {
            log("Client > Watchdog. Stopping the supplicant");
            stopSupplicant();
            setWatchdog(CONNECT_DELAY_MILLIS);
        } else {
            log("Client > Watchdog. Check connection");
            checkConnection();
            //startSupplicant(serverHost, serverPort, clientName, clientKey);
        }
    }

    /**
     * Отправляет уведомление содержащее:
     * <ul>
     *     <li>сведения о состоянии батареи и мобильной сети в иконке (если intent не null)</li>
     *     <li>имя клиента в заголовке</li>
     *     <li>информация о состоянии соединения</li>
     * </ul>
     *
     * @param intent данные полученные от сервера с информацией о состоянии батареи и мобильной связи или {@code null}
     */
    public void postDefaultNotification(Intent intent) {

        if ( !enabled ) {
            log("Client > WARNING! Call to postDefaultNotification while client mode is disables");
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

        String title;
        String text;
        String ticker;

        if ( intent != null ) {
            title = getString(R.string.client_format_title, intent.getStringExtra(EXTRA_NAME));
            text = getString(R.string.format_paired, serverName, mSupplicant.getHostAddress(), mSupplicant.getPort());
            builder.setLargeIcon(bitmapMaker.set(intent).draw());
            ticker = bitmapMaker.toString();
        } else if ( isConnected() ) {
            title = getString(R.string.client_default_title);
            text = getString(R.string.client_format_connected, serverHost, serverPort);
            ticker = text;
        } else if ( mSupplicant != null ) {
            title = getString(R.string.client_default_title);
            text = getString(R.string.client_format_connecting, serverHost, serverPort);
            ticker = null;
        } else {
            title = getString(R.string.client_default_title);
            text = getString(R.string.idle);
            ticker = null;
        }

        builder
                .setSmallIcon(R.drawable.ic_apstatus_a_24dp)
                .setContentTitle(title)
                .setContentText(text)
                .addAction(actionSettings)
                .addAction(actionExit);

        if ( ticker != null ) builder.setTicker(ticker).setSubText(ticker);

        builder.setContentIntent(
                MessageBox.compose(
                        this,
                        NOTIFICATION_ID_DEFAULT,
                        R.string.client_default_title,
                        R.string.client_about,
                        actionSettings,
                        actionExit)
        );

        startForeground(NOTIFICATION_ID_DEFAULT, builder.build());
        started = true;
    }


    public void postRemoteNotification(@NonNull RemoteNotification item, Intent intent) {
        StringBuilder sb = new StringBuilder();
        sb.append("Client > Notification posted (").append(item.remotePackage).append(" : ").append(item.getId()).append(')');

        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

            String category = intent.getStringExtra(EXTRA_CATEGORY);
            builder.setSmallIcon(iconsMap.containsKey(category) ? iconsMap.get(category) : R.drawable.ic_apstatus_a_24dp);

            Bundle extras = intent.getBundleExtra(EXTRA_EXTRAS);
            String subText = intent.getStringExtra(EXTRA_LABEL);
            if (extras == null) {
                sb.append(", has no extras");
            } else {
                NotificationCompat.Style style = null;
                sb.append(", has ").append(extras.size()).append(" extras");
                for (String key : extras.keySet()) {
                    Object obj = extras.get(key);
                    if (obj != null) {
                        switch (key) {
                            case NotificationCompat.EXTRA_TITLE:
                                builder.setContentTitle(obj.toString());
                                builder.setTicker(obj.toString());
                                break;
                            case NotificationCompat.EXTRA_SUB_TEXT:
                                subText = obj.toString();
                                break;
                            case NotificationCompat.EXTRA_BIG_TEXT:
                                if ( style == null ) style = getBigTextStyle(intent);
                                break;
                            case NotificationCompat.EXTRA_TEXT_LINES:
                                if ( style == null ) style = getInboxStyle(intent);
                                break;
                            case NotificationCompat.EXTRA_TEXT:
                                builder.setContentText(obj.toString());
                                break;
                            case NotificationCompat.EXTRA_TEMPLATE:
                                break;
                            default:
                                break;
                        }
                    }
                }

                if (subText != null) builder.setSubText(subText);

                if ( style != null ) {
                    sb.append(", styled");
                    builder.setStyle(style);
                }

                Notification notification = builder.build();
                ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(item.getId(), notification);
            }
        } catch (Exception e) {
            log("Client > Error at postRemoteNotification: " + e.getMessage());
        }
        log(sb.toString());
    }

    public NotificationCompat.BigTextStyle getBigTextStyle(Intent intent) {
        CharSequence chars = intent.getCharSequenceExtra(NotificationCompat.EXTRA_BIG_TEXT);

        if ( chars != null ) {
            NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle();
            style.bigText(chars);

            chars = intent.getCharSequenceExtra(NotificationCompat.EXTRA_TITLE_BIG);
            if (chars != null) style.setBigContentTitle(chars);

            chars = intent.getCharSequenceExtra(NotificationCompat.EXTRA_SUMMARY_TEXT);
            if (chars != null) style.setSummaryText(chars);

            return style;
        }
        return null;
    }

    public NotificationCompat.InboxStyle getInboxStyle(Intent intent) {
        CharSequence[] lines = intent.getCharSequenceArrayExtra(NotificationCompat.EXTRA_TEXT_LINES);
        if ( lines != null ) {
            NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
            for (CharSequence c : lines) style.addLine(c);

            CharSequence chars;
            chars = intent.getCharSequenceExtra(NotificationCompat.EXTRA_TITLE_BIG);
            if (chars != null) style.setBigContentTitle(chars);

            chars = intent.getCharSequenceExtra(NotificationCompat.EXTRA_SUMMARY_TEXT);
            if (chars != null) style.setSummaryText(chars);

            return style;
        }
        return null;
    }

    public static class RemoteNotification {
        private String remotePackage;
        private int remoteId;
        private int id;

        public static RemoteNotification get(Intent intent) {
            String remotePackage = intent.getStringExtra(EXTRA_PACKAGE);
            int remoteId = intent.getIntExtra(EXTRA_ID,0);
            if ( remotePackage != null ) {
                for (RemoteNotification item : remoteNotifications) {
                    if ( item.match(remotePackage,remoteId) ) return item;
                }
                RemoteNotification item = new RemoteNotification(remotePackage,remoteId);
                remoteNotifications.add(item);
                return item;
            }
            return null;
        }

        public static RemoteNotification remove(Intent intent) {
            String remotePackage = intent.getStringExtra(EXTRA_PACKAGE);
            int remoteId = intent.getIntExtra(EXTRA_ID,0);
            if ( remotePackage != null ) {
                for(int i = 0; i < remoteNotifications.size(); i++) {
                    RemoteNotification item = remoteNotifications.get(i);
                    if ( item.match(remotePackage,remoteId) ) {
                        remoteNotifications.remove(i);
                        return item;
                    }
                }
            }
            return null;
        }

        public RemoteNotification(@NonNull String remotePackage, int remoteId) {
            this.remotePackage = remotePackage;
            this.remoteId = remoteId;
            this.id = ++lastNotificationId;
        }

        public int getId() { return id; }

        public boolean match(String otherPackage, int otherId) {
            return remoteId == otherId && remotePackage.equals(otherPackage);
        }
    }


    public static class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent serviceIntent = new Intent(intent);
            serviceIntent.setClass(context, Client.class);
            context.startService(serviceIntent);
        }
    }

    public static void updateComponentsState(Context context, int flag) {
        PackageManager packageManager = context.getPackageManager();

        ComponentName receiver = new ComponentName(context, Receiver.class);
        if (packageManager.getComponentEnabledSetting(receiver) != flag) {
            packageManager.setComponentEnabledSetting(receiver, flag, PackageManager.DONT_KILL_APP);
        }
    }

    public class Supplicant extends SocketTask {
        private String name;
        private Socket socket;

        public Supplicant(String remoteAddress, int remotePort) {
            host = remoteAddress;
            port = remotePort;
        }

        public Supplicant setName(String name) {
            this.name = name;
            return this;
        }

        private void read() throws Exception {
            while( true ) {
                Intent intent = readIntent(socket);
                if ( intent == null ) break;
                mHandler.sendMessage( mHandler.obtainMessage(MESSAGE_READER_SUCCESS, intent) );
            }
        }


        @Override
        public void run() {
            socket = new Socket();
            Intent intent;
            try {
                InetSocketAddress address = new InetSocketAddress(host, port);
                hostAddress = address.getAddress().getHostAddress();
                socket.connect(address, WRITER_CONNECT_TIMEOUT);
                intent = new Intent(ACTION_CONNECT);
                intent.putExtra(EXTRA_NAME, name);
                writeIntent(socket, intent);
                while (true) {
                    intent = readIntent(socket);
                    String action = intent == null ? null : intent.getAction();
                    if ( ACTION_KEY_REQUEST.equals(action) ) {
                        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                        kpg.initialize(1024);
                        KeyPair kp = kpg.genKeyPair();
                        key = kp.getPrivate();
                        byte[] serverKey = kp.getPublic().getEncoded();
                        intent = new Intent(ACTION_KEY_RESPONSE).putExtra(EXTRA_DATA, serverKey);
                        writeIntent(socket, intent);
                        continue;
                    }
                    if ( ACTION_ECHO_REQUEST.equals(action) ) {
                        byte[] encrypted = intent.getByteArrayExtra(EXTRA_DATA);
                        Cipher c = Cipher.getInstance("RSA");
                        c.init(Cipher.DECRYPT_MODE, key);
                        byte[] decrypted = c.doFinal(encrypted);
                        intent = new Intent(ACTION_ECHO_RESPONSE).putExtra(EXTRA_DATA, decrypted);
                        writeIntent(socket, intent);
                        continue;
                    }
                    if ( ACTION_CONNECT.equals(action) ) {
                        if ( key == null ) throw new Exception("Connect action but key is null");
                        mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_APPROVED, this));
                        read();
                        break;
                    }
                    throw new Exception("Unexpected action " + action);
                }
            } catch (Exception e) {
                message = e.toString();
                mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_ERROR,this));
            } finally {
                try { socket.close(); } catch (IOException ignore) {}
                socket = null;
            }
        }
    }

    public void startSupplicant(String remoteAddress, int remotePort, String name, Key key) {
        if ( isConnected() && mSupplicant.match(remoteAddress,remotePort) ) return;
        stopSupplicant();
        mSupplicant = new Supplicant(remoteAddress, remotePort);
        mSupplicant.setName(name).setKey(key).go();
        postDefaultNotification(null);
    }

    public void stopSupplicant() {
        if ( mSupplicant != null && mSupplicant.socket != null ) {
            try { mSupplicant.socket.close(); } catch (IOException ignore) {}
        }
        mSupplicant = null;
        setWatchdog(0);
        postDefaultNotification(null);
    }

    public boolean isConnected() {
        return mSupplicant != null && mSupplicant.socket != null && mSupplicant.socket.isConnected();
    }

}
