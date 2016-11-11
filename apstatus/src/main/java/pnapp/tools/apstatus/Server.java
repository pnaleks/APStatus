package pnapp.tools.apstatus;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Base64;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;

import javax.crypto.Cipher;

/**
 * @author P.N. Alekseev
 * @author pnaleks@gmail.com
 */
public class Server extends Base {
    public static final String DEFAULT_SERVER_NAME =
            Build.MODEL.contains(Build.MANUFACTURER) ? Build.MODEL : Build.MANUFACTURER + ' ' + Build.MODEL;

    public static final int FLAG_BATTERY_CHANGED = 0x00000001;
    public static final int FLAG_SIGNAL_CHANGED = 0x00000002;

    private static final int NOTIFICATION_ID_DEFAULT = 1;
    private static final int NOTIFICATION_ID_AUTHORIZATION = 2;

    private String serverName;
    private String clientName;

    private PhoneStateMonitor mMonitor;

    private ClientBitmapMaker.BatteryStatus batteryStatus = ClientBitmapMaker.BatteryStatus.UNKNOWN;
    private int batteryLevel = 0;

    private boolean started;

    private PublicKey serverKey;
    private Writer writer;

    private ArrayList<Intent> writerQueue = new ArrayList<>();

    private Receiver mReceiver;
    private WSServer mServer;

    /**
     * {@link BroadcastReceiver} for {@link Intent#ACTION_BATTERY_CHANGED} event
     */
    public static class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent serviceIntent = new Intent(intent);
            serviceIntent.setClass(context, Server.class);
            context.startService(serviceIntent);
        }
    }

    public class WSServer extends WebSocketServer {

        public WSServer(InetSocketAddress address) {
            super(address);
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {

        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {

        }

        @Override
        public void onMessage(WebSocket conn, String message) {

        }

        @Override
        public void onError(WebSocket conn, Exception ex) {

        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = (intent == null) ? null : intent.getAction();

        if ( action == null ) {
            log("Server > Warning! Started with null intent or action!");
            return START_NOT_STICKY;
        }

        log("Server > Has gotten " + action + " intent");

        if ( ACTION_EXIT.equals(action) ) {
            stop();
            return START_NOT_STICKY;
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        if ( !preferences.getBoolean(Settings.PREF_SERVER_MODE, false) ) {
            stop();
            return START_NOT_STICKY;
        }

        switch (action) {
            case ACTION_LAUNCH:
                if ( started ) {
                    if ( !preferences.getBoolean(Settings.PREF_CLIENT_MODE,false) )
                        startActivity( new Intent(this, Settings.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) );
                    return START_NOT_STICKY;
                } else {
                    postDefaultNotification();
                }
            case ACTION_START:
                start(intent);
                return START_NOT_STICKY;
        }

        if ( !started ) {
            log("Server > Should be started explicitly. Action ignored!");
            stop();
            return START_NOT_STICKY;
        }

        switch (action) {
            case Intent.ACTION_BATTERY_CHANGED:
                onBatteryChanged(intent);
                break;
            case ACTION_ACCEPT:
                ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID_AUTHORIZATION);
                if ( writer != null ) {
                    log("Server > Client " + writer.toString() + " accepted by user");
                    serverKey = (PublicKey) writer.getKey();
                    preferences.edit()
                            .putString(Settings.SERVER_KEY, Base64.encodeToString(serverKey.getEncoded(), Base64.DEFAULT))
                            .putBoolean(Settings.PREF_BIND, false)
                            .apply();
                    post(FLAG_BATTERY_CHANGED | FLAG_SIGNAL_CHANGED);
                    postDefaultNotification();
                }
                break;
            case ACTION_REFUSE:
                ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID_AUTHORIZATION);
                if ( writer != null ) {
                    log("Server > Client " + writer.toString() + " refused by user");
                    writer.close();
                    writer = null;
                }
                break;
            case ACTION_POST:
            case ACTION_REMOVE:
                post(intent);
                break;
        }

        return START_NOT_STICKY;
    }

    protected void start(Intent intent) {
        startInspector( intent.getDataString() );

        if ( getInspector() != null ) {

            if (mReceiver == null) {
                mReceiver = new Receiver();
                registerReceiver(mReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            }

            if (mMonitor == null) {
                mMonitor = new PhoneStateMonitor();
                mMonitor.activate(this);
            }

            post(FLAG_BATTERY_CHANGED | FLAG_SIGNAL_CHANGED);
            postDefaultNotification();
        }
    }

    protected void stop() {
        stopInspector();

        if ( writer != null ) {
            writer.close();
            writer = null;
        }

        if ( started ) {
            started = false;
            if (mReceiver != null) {
                unregisterReceiver(mReceiver);
                mReceiver = null;
            }
            if (mMonitor != null) {
                mMonitor.deactivate();
                mMonitor = null;
            }
            stopForeground(true);
        }

        stopSelf();
    }

    public void post(int flags) {
        if ( flags == 0 ) return;

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject
                    .put("action", ACTION_UPDATE)
                    .put(EXTRA_NAME, serverName);

            if ( (flags & FLAG_BATTERY_CHANGED) != 0 ) {
                jsonObject
                        .put(EXTRA_BATTERY_LEVEL, batteryLevel)
                        .put(EXTRA_BATTERY_STATUS, batteryStatus.toString());
            }

            if ( (flags & FLAG_SIGNAL_CHANGED) != 0 ) {
                if( mMonitor != null ) {
                    jsonObject
                            .put(EXTRA_SIGNAL_LEVEL, mMonitor.getLevel())
                            .put(EXTRA_SIGNAL_TYPE, mMonitor.getType())
                            .put(EXTRA_SIGNAL_CONNECTED, mMonitor.isConnected());
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (mServer != null) {
            for (WebSocket client : mServer.connections()) {
                client.send(jsonObject.toString());
            }
        }
        write();
    }

    public void post(Intent intent) {
        String pn = intent.getStringExtra(EXTRA_PACKAGE);
        int id = intent.getIntExtra(EXTRA_ID,0);
        for(int i = 1; i < writerQueue.size(); i++) {
            Intent queued = writerQueue.get(i);
            if ( pn.equals(queued.getStringExtra(EXTRA_PACKAGE)) && id == intent.getIntExtra(EXTRA_ID,0) ) {
                if ( ACTION_POST.equals(intent.getAction()) ) {
                    writerQueue.set(i, intent);
                    log("Server > Notification updated (" + pn + ':' + id + ')');
                    write();
                } else {
                    writerQueue.remove(i);
                    log("Server > Notification removed (" + pn + ':' + id + ')');
                }
                return;
            }
        }
        intent.putExtra(EXTRA_NAME, serverName);
        writerQueue.add(intent);
        log("Server > Notification added (" + pn + ':' + id + ')');
        write();
    }

    public void write() {
        if ( writer == null ) return;

        if ( writer.getIntent() != null ) {
            log("Server > Writer is busy");
            return;
        }

        if ( writerQueue.isEmpty() ) {
            log("Server > Writer queue is empty");
            return;
        }

        log("Server > " + writer.toString() + " started, " + writerQueue.size() + " items queued");
        writer.put(writerQueue.get(0)).go();
    }

    public void onBatteryChanged(Intent intent) {
        boolean changed = false;

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0);
        if ( scale != 0 && level != -1 ) {
            level = 100 * level / scale;
            if ( batteryLevel != level ) {
                batteryLevel = level;
                changed = true;
            }
        }

        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);

        ClientBitmapMaker.BatteryStatus s;

        if ( plugged == 0 ) {
            s = (level > 15) ? ClientBitmapMaker.BatteryStatus.UNPLUGGED : ClientBitmapMaker.BatteryStatus.LOW;
        } else {
            if ( status == BatteryManager.BATTERY_STATUS_CHARGING ) {
                switch ( plugged ) {
                    case BatteryManager.BATTERY_PLUGGED_USB:
                        s = ClientBitmapMaker.BatteryStatus.USB;
                        break;
                    case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                        s = ClientBitmapMaker.BatteryStatus.WIRELESS;
                        break;
                    default:
                        s = ClientBitmapMaker.BatteryStatus.AC;
                        break;
                }
            } else {
                s = ClientBitmapMaker.BatteryStatus.PLUGGED;
            }
        }

        if ( batteryStatus != s) {
            batteryStatus = s;
            changed = true;
        }

        if ( changed ) post(FLAG_BATTERY_CHANGED);
    }

    @Override
    protected void onWrite(SocketTask task) {
        if ( writer != task ) throw new RuntimeException("Unexpected onWrite from " + task.toString());

        if ( !writerQueue.isEmpty() && writerQueue.get(0) == writer.getIntent() ) writerQueue.remove(0);

        if ( writerQueue.isEmpty() ) {
            log("Server > " + writer.toString() + " completed, queue is empty");
            writer.put(null);
        } else {
            log("Server > Continuing " + writer.toString() + ", " + writerQueue.size() + " items queued");
            writer.put(writerQueue.get(0)).go();
        }
    }

    @Override
    protected void onApproved(SocketTask task) {
        if ( writer != null ) {
            writer.close();
            writer = null;
        }

        writer = (Writer) task;
        clientName = (String) writer.getTag();

        if ( serverKey == null ) {
            if ( PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Settings.PREF_BIND,true) ) {
                log("Server > New client should be authorized by the user");
                postAuthorizationNotification();
                return;
            }
        }

        if ( serverKey != writer.getKey() ) {
            log("Server > Approved client has unexpected key");
            writer.close();
            writer = null;
            return;
        }

        log("Server > Client " + clientName + " approved with " + writer.toString());

        post(FLAG_BATTERY_CHANGED | FLAG_SIGNAL_CHANGED);
        postDefaultNotification();
    }

    @Override
    protected void onError(SocketTask task) {
        if ( task instanceof Writer ) {
            if ( task == writer ) {
                log("Server > " + writer.toString() + " finished, deactivating");
                writer = null;
            }
        } else if ( task instanceof Inspector ) {
            if ( task == mInspector ) {
                startInspector(null);
            }
        }
        postDefaultNotification();
    }

    public void postDefaultNotification() {
        String title;
        if ( serverName == null )  {
            title = getString(R.string.server_title_default);
        } else {
            title = getString(R.string.server_title_format, serverName);
        }

        String text;
        if ( writer != null ) {
            text = getString(R.string.format_paired, clientName, writer.getHost(), writer.getPort());
        } else if ( isListening() ) {
            text = getString(R.string.format_awaiting, getInspector().getHostAddress(), getInspector().getPort());
        } else {
            text = getString(R.string.idle);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

        builder
                .setSmallIcon(R.drawable.ic_apstatus_a_24dp)
                .setContentTitle(title )
                .setContentText(text)
                .setTicker(text)
                .addAction(actionSettings)
                .addAction(actionExit);

        builder.setContentIntent(
                MessageBox.compose(
                        this,
                        NOTIFICATION_ID_DEFAULT,
                        R.string.server_title_default,
                        R.string.server_about,
                        actionSettings,
                        actionExit)
        );

        startForeground(NOTIFICATION_ID_DEFAULT, builder.build());
        started = true;
    }

    public void postAuthorizationNotification() {
        String text = String.format(getString(R.string.server_format_authorization),
                writer.getTag(), writer.getHost(), writer.getPort());

        NotificationCompat.Action accept = new NotificationCompat.Action(
                R.drawable.ic_check_white_24dp,
                getString(R.string.accept),
                PendingIntent.getService(this, 0,
                        new Intent(ACTION_ACCEPT, null, this, getClass()), PendingIntent.FLAG_UPDATE_CURRENT)
        );

        NotificationCompat.Action refuse = new NotificationCompat.Action(
                R.drawable.ic_check_white_24dp,
                getString(R.string.refuse),
                PendingIntent.getService(this, 0,
                        new Intent(ACTION_REFUSE, null, this, getClass()), PendingIntent.FLAG_UPDATE_CURRENT)
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder
                .setDefaults(Notification.DEFAULT_SOUND)
                .setSmallIcon(R.drawable.ic_apstatus_a_24dp)
                .setContentTitle(getString(R.string.authorization_request))
                .setTicker(text)
                .setContentText(text)
                .addAction(accept)
                .addAction(refuse);

        builder.setContentIntent(MessageBox.compose(NOTIFICATION_ID_AUTHORIZATION, builder));

        ((NotificationManager) getSystemService(Server.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID_AUTHORIZATION, builder.build());
    }

    public class Inspector extends SocketTask {
        private ServerSocket serverSocket;

        Inspector(String localAddress, int localPort) {
            port = localPort;
            host = localAddress;
        }

        @Override
        public void go() {
            if ( !listening() ) new Thread(this).start();
        }

        @Override
        public void run() {
            try {
                InetAddress address = null;
                if ( host != null ) {
                    address = InetAddress.getByName(host);
                    hostAddress = address.getHostAddress();
                } else {
                    hostAddress = "0.0.0.0";
                }

                serverSocket = new ServerSocket(port, BACKLOG, address);
                while ( serverSocket.isBound() ) {
                    inspect(serverSocket.accept());
                }
                message = "Socket not bound";
            } catch (Exception e) {
                message = e.getMessage();
            } finally {
                close();
                mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_ERROR, this));
            }
        }

        private void inspect(Socket socket) {
            String name;
            Intent intent;
            String hostAddress = "Unknown";

            try {
                hostAddress = socket.getInetAddress().getHostAddress();
                // Жду приветствия
                intent = readIntent(socket);
                if ( intent == null ) return;
                if (!ACTION_CONNECT.equals(intent.getAction()))
                    throw new Exception("Wrong action " + intent.getAction() + ", " + ACTION_CONNECT + " expected");

                name = intent.getStringExtra(EXTRA_NAME);
                if ( name == null ) throw new Exception("Anonymous client");

                if ( key == null ) {
                    writeIntent(socket, new Intent(ACTION_KEY_REQUEST));
                    intent = readIntent(socket);
                    if ( intent == null ) return;
                    if ( !ACTION_KEY_RESPONSE.equals(intent.getAction()) ) throw new Exception("Wrong action " + intent.getAction() + ", " + ACTION_KEY_RESPONSE + " expected");
                    byte[] bytes = intent.getByteArrayExtra(EXTRA_DATA);
                    key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
                }

                // Запрос эха
                byte[] original = new byte[108];
                new SecureRandom().nextBytes(original);
                Cipher c = Cipher.getInstance("RSA");
                c.init(Cipher.ENCRYPT_MODE, key);
                intent = new Intent(ACTION_ECHO_REQUEST);
                intent.putExtra(EXTRA_DATA, c.doFinal(original));
                writeIntent(socket, intent);
                intent = readIntent(socket);
                if ( intent == null ) return;
                if ( !ACTION_ECHO_RESPONSE.equals(intent.getAction()) ) throw new Exception("Wrong action " + intent.getAction() + ", " + ACTION_ECHO_RESPONSE + " expected");
                byte[] returned = intent.getByteArrayExtra(Base.EXTRA_DATA);
                if ( returned == null ) throw new Exception("Decrypted data expected");

                if ( original.length != returned.length ) throw new Exception("Returned data has wrong length");

                int i = 0;
                while (i < original.length) {
                    if (original[i] != returned[i]) throw new Exception("Echo doesn't match at " + i);
                    i++;
                }
                writeIntent(socket, new Intent(ACTION_CONNECT));
                mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_APPROVED, new Writer(socket).setKey(key).setTag(name)));
            } catch (Exception e) {
                try { socket.close(); } catch (IOException ignore) {}
                message = "Inspection of " + hostAddress + " fails: " + e.getMessage();
                mHandler.sendMessage( mHandler.obtainMessage(MESSAGE_ERROR, this) );
            }
        }

        boolean listening() { return serverSocket != null && serverSocket.isBound(); }
        boolean listening(String localAddress, int localPort) { return  match(localAddress, localPort) && listening(); }

        void close() {
            if ( serverSocket != null ) {
                try { serverSocket.close(); } catch (IOException ignore) {}
                serverSocket = null;
            }
        }
    }

    public class Writer extends SocketTask {
        private Socket socket;
        private Intent intent;
        private boolean close;

        @SuppressWarnings("unused")
        public Writer(String host, int port) {
            this.host = host;
            this.port = port;
        }

        Writer(Socket socket) {
            this.socket = socket;
            host = hostAddress = socket.getInetAddress().getHostAddress();
            port = socket.getPort();
        }

        /**
         * @param intent интент для отправки
         * @return ссылка на самого себя
         */
        Writer put(Intent intent) {
            this.intent = intent;
            return this;
        }

        public Intent getIntent() { return intent; }

        @Override
        public void go() {
            if ( intent == null ) throw new RuntimeException("Empty writer!");
            (new Thread(this)).start();
        }

        @Override
        public void run() {
            if( close ) {
                gentlyClose();
                return;
            }

            if ( socket != null && !socket.isConnected()) {
                try { socket.close(); } catch (IOException ignore) {}
                socket = null;
            }

            try {
                if ( socket == null  ){
                    socket = new Socket();
                    InetSocketAddress address = new InetSocketAddress(host, port);
                    hostAddress = address.getAddress().getHostAddress();
                    socket.connect(address, WRITER_CONNECT_TIMEOUT);
                }
                writeIntent(socket, intent);
                mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_WRITER_SUCCESS,this));
            } catch (Exception e) {
                gentlyClose();
                message = e.getMessage();
                mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_ERROR,this));
            }
        }

        private void gentlyClose() {
            if ( socket != null ) {
                try {
                    ByteBuffer buffer = ByteBuffer.allocate(12);
                    buffer.putLong(MAGIC_END);
                    buffer.putInt(0);
                    socket.getOutputStream().write(buffer.array());
                    socket.close();
                } catch (IOException ignore) {}
                socket = null;
            }
        }

        void close() {
            if( socket != null ) {
                close = true;
                (new Thread(this)).start();
            }
        }
    }


    private Inspector mInspector;

    public void startInspector(String localAddress) {
        if ( localAddress == null ) {
            if ( mInspector == null ) return;
            localAddress = mInspector.getHost();
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int port = Integer.valueOf(prefs.getString(Settings.PREF_LISTEN_PORT, "5444"));

        serverName = prefs.getString(Settings.PREF_SERVER_NAME, DEFAULT_SERVER_NAME);
        if ( prefs.getBoolean(Settings.PREF_BIND, true) ) {
            serverKey = null;
        } else if ( serverKey == null ) {
            String key = prefs.getString(Settings.SERVER_KEY, null);
            if ( key != null ) {
                try {
                    serverKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decode(key, Base64.DEFAULT)));
                } catch (Exception ignore) {}
            }
        }

        if ( mInspector != null ) {
            if ( mInspector.listening(localAddress, port) ) {
                log("Server > Inspector already at " + localAddress);
                mInspector.setKey(serverKey);
                return;
            }
            mInspector.close();
        }
        log("Server > Inspector going to " + localAddress);
        mInspector = new Inspector(localAddress, port);
        mInspector.setKey(serverKey).go();
    }

    public void stopInspector() {
        if ( mInspector != null ) {
            mInspector.close();
            mInspector = null;
        }
    }

    public Inspector getInspector() { return mInspector; }

    public boolean isListening() {
        return mInspector != null && mInspector.listening();
    }

}
