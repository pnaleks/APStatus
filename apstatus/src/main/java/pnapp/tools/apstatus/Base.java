package pnapp.tools.apstatus;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.text.SpannableString;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.Key;
import java.text.DateFormat;
import java.util.Date;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;

/**
 * @author P.N. Alekseev
 * @author pnaleks@gmail.com
 */
public abstract class Base extends Service {
    public static final String LOOPBACK = "127.0.0.1";

    /** Используется для первичного обращения клиента к серверу */
    public static final String ACTION_CONNECT = "action_connect";
    /** Запрос-проверка от сервера к клиенту. Данные в byte[] с ключем {@link #EXTRA_DATA} содержат случайный набор байт закодированный открытым RSA ключом */
    public static final String ACTION_ECHO_REQUEST = "action_echo_request";
    /** Отклик клиента на запрос {@link #ACTION_ECHO_REQUEST} содержит расшифрованный byte[] с ключом {@link #EXTRA_DATA} */
    public static final String ACTION_ECHO_RESPONSE = "action_echo_response";

    public static final String ACTION_KEY_REQUEST = "action_key_request";
    public static final String ACTION_KEY_RESPONSE = "action_key_response";

    public static final String ACTION_ACCEPT = "action_accept";
    public static final String ACTION_REFUSE = "action_refuse";

    public static final String ACTION_UPDATE = "action_update";
    public static final String ACTION_POST = BuildConfig.APPLICATION_ID + ".POST";
    public static final String ACTION_REMOVE = BuildConfig.APPLICATION_ID + ".REMOVE";

    public static final String ACTION_LAUNCH = BuildConfig.APPLICATION_ID + ".LAUNCH";
    public static final String ACTION_START = BuildConfig.APPLICATION_ID + ".START";
    public static final String ACTION_EXIT = BuildConfig.APPLICATION_ID + ".EXIT";

    public static final String ACTION_CHECK = BuildConfig.APPLICATION_ID + ".CHECK";

    public static final String EXTRA_NAME     = "name";
    public static final String EXTRA_DATA     = "data";
    @SuppressWarnings("unused")
    public static final String EXTRA_PORT     = "port";
    public static final String EXTRA_CATEGORY = "category";
    public static final String EXTRA_EXTRAS   = "extras";
    public static final String EXTRA_ID       = "id";
    public static final String EXTRA_PACKAGE  = "package";
    public static final String EXTRA_LABEL    = "label";
    public static final String EXTRA_WHEN     = "when";

    /** Уровень заряда батареи в процентах, int */
    public static final String EXTRA_BATTERY_LEVEL = "battery_level";
    /** Индикатор состояния батарей, одно из значений BATTERY_STATUS_* в {@link ClientBitmapMaker}, int */
    public static final String EXTRA_BATTERY_STATUS = "battery_status";
    /** Уровень сигнала мобильной связи 0...4, int */
    public static final String EXTRA_SIGNAL_LEVEL = "signal_level";
    /** Тип сигнала мобильной связи, String */
    public static final String EXTRA_SIGNAL_TYPE = "signal_type";
    /** Признак наличия активного соединения, boolean */
    public static final String EXTRA_SIGNAL_CONNECTED = "signal_connected";

    /** Регулярное выражение для преобразования IP-адреса из строкового представления */
    private static final Pattern PATTERN_IP = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");

    public static final int MESSAGE_APPROVED = 100;
    public static final int MESSAGE_READER_SUCCESS = 200;
    public static final int MESSAGE_WRITER_SUCCESS = 300;
    public static final int MESSAGE_WATCHDOG = 1000;
    public static final int MESSAGE_ERROR = -1;

    /** Время ожидания исходящего соединения в миллисекундах */
    public static final int WRITER_CONNECT_TIMEOUT = 10000;
    public static final int ACK_TIMEOUT = 1000;
    /** Максимальный размер блока данных */
    public static final int MAX_DATA_SIZE = 2*1024*1024;

    public static final long MAGIC_RAW = 0x504e417070524157L;
    public static final long MAGIC_AES = 0x504e4170704e4553L;
    public static final long MAGIC_END = 0x504e417070457868L;
    public static final int BACKLOG = 50;
    public static final int ACK = 182;

    protected NotificationCompat.Action actionSettings;
    protected NotificationCompat.Action actionExit;

    public static int instanceCount;
    public int instanceId;

    public Base() { instanceId = instanceCount++; }

    @Override
    public void onCreate() {
        log(getClass().getSimpleName() + " > Created, ID = " + instanceId);

        if ( actionSettings == null ) {
            actionSettings = new NotificationCompat.Action(
                    R.drawable.ic_settings_white_24dp,
                    getString(R.string.settings),
                    PendingIntent.getActivity(this, 0,
                            new Intent(this, Settings.class), PendingIntent.FLAG_UPDATE_CURRENT)
            );
        }
        if ( actionExit == null ) {
            actionExit = new NotificationCompat.Action(
                    R.drawable.ic_clear_white_24dp,
                    getString(R.string.stop),
                    PendingIntent.getService(this, 0,
                            new Intent(ACTION_EXIT, null, this, getClass()), PendingIntent.FLAG_UPDATE_CURRENT)
            );
        }
    }

    @Override
    public void onDestroy() {
        log(getClass().getSimpleName() + " > Destroyed, ID = " + instanceId);
        actionSettings = null;
        actionExit = null;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    public void toast(int textResId) {
        @SuppressLint("InflateParams")
        TextView view = (TextView) LayoutInflater.from(this).inflate(R.layout.toast, null);

        view.setText(textResId);
        view.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_apstatus_a_24dp,0,0,0);

        Toast toast = new Toast(this);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(view);
        toast.show();
    }

    public static String mLogDate;
    public static void log(String text) {
        if ( BuildConfig.DEBUG ) {
            if (Build.MODEL.contains("Android SDK")) Log.i("APStatus.log", text);
        } else {
            File logFile = new File("sdcard/APStatus.log");
            if ( !logFile.exists() )  {
                try {
                    //noinspection ResultOfMethodCallIgnored
                    logFile.createNewFile();
                }
                catch (Exception ignore) {}
            }
            try {
                //BufferedWriter for performance, true to set append to file flag
                BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));

                Date now = new Date();
                String date = DateFormat.getDateInstance().format(now);
                String time = DateFormat.getTimeInstance().format(now);

                if ( mLogDate == null || !mLogDate.equals(date) ) {
                    mLogDate = date;
                    buf.append("\n<<<<<<<< ").append(mLogDate).append(" >>>>>>>>\n");
                }

                buf.append(time).append(' ').append(text);
                buf.newLine();

                buf.close();
            }
            catch (Exception ignore) {}
        }
    }

    protected void onRead(Intent intent) {}
    protected void onWrite(@SuppressWarnings("UnusedParameters") SocketTask writer) {}
    protected void onError(@SuppressWarnings("UnusedParameters") SocketTask task) {}
    protected void onApproved(SocketTask task) {}
    protected void onWatchdog() {}

    /**
     * Переустановка защитного механизма на срабатываение через заданное время, по
     * истечении которого будет вызвана функция {@link #onWatchdog()}
     *
     * @param millis задержка срабатывания или ноль для отключения
     */
    protected void setWatchdog(long millis) {
        mHandler.removeMessages(MESSAGE_WATCHDOG);
        if ( millis != 0 ) {
            mHandler.sendMessageDelayed( mHandler.obtainMessage(MESSAGE_WATCHDOG), millis );
        }
    }

    protected Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {

            switch( msg.what ) {
                case MESSAGE_ERROR:
                    SocketTask task = (SocketTask) msg.obj;
                    log("Error at " + task.toString() + ": " + task.getMessage());
                    if ( !"Socket closed".equals(task.getMessage()) ) {
                        onError(task);
                    }
                    break;
                case MESSAGE_WRITER_SUCCESS:
                    onWrite((SocketTask) msg.obj);
                    return true;
                case MESSAGE_READER_SUCCESS:
                    onRead((Intent) msg.obj);
                    break;
                case MESSAGE_APPROVED:
                    onApproved((SocketTask) msg.obj);
                    break;
                case MESSAGE_WATCHDOG:
                    onWatchdog();
                    break;
                default:
                    return false;
            }
            return true;
        }
    });

    public static Intent readIntent(Socket socket) throws Exception {
        InputStream is = socket.getInputStream();
        ByteBuffer buffer = ByteBuffer.allocate(12);
        if (is.read(buffer.array()) != 12) throw new IOException("Corrupted header");

        long magic = buffer.getLong();
        if (magic == MAGIC_END) return null;
        if (magic != MAGIC_RAW) throw new IOException("Bad magic " + magic);

        int size = buffer.getInt();
        if (size < 0 || size > MAX_DATA_SIZE) throw new IOException("Bad data size " + size);

        byte[] data = new byte[size];
        size = is.read(data);
        if (size == -1) throw new IOException("Unexpected EOF");
        if (size != data.length) throw new IOException("Read " + size + " bytes, " + data.length + " expected");

        Intent intent = getIntent(data);
        OutputStream os = socket.getOutputStream();
        os.write(ACK);
        os.flush();
        return intent;
    }

    @SuppressWarnings("unused")
    public static Intent readIntent(Socket socket, Key key) throws Exception {
        InputStream is = socket.getInputStream();
        ByteBuffer buffer = ByteBuffer.allocate(12);
        if (is.read(buffer.array()) != 12) throw new IOException("Corrupted header");

        long magic = buffer.getLong();
        if (magic == MAGIC_END) return null;
        if (magic != MAGIC_AES) throw new IOException("Bad magic " + magic);

        int size = buffer.getInt();
        if (size < 0 || size > MAX_DATA_SIZE) throw new IOException("Bad data size " + size);

        byte[] data = new byte[size];
        size = is.read(data);
        if (size == -1) throw new IOException("Unexpected EOF");
        if (size != data.length) throw new IOException("Read " + size + " bytes, " + data.length + " expected");

        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, key);
        data = c.doFinal(data);

        Intent intent = getIntent(data);
        OutputStream os = socket.getOutputStream();
        os.write(ACK);
        os.flush();
        return intent;
    }

    public static void writeIntent(Socket socket, Intent intent) throws Exception {
        OutputStream os = socket.getOutputStream();
        ByteBuffer buffer = ByteBuffer.allocate(12);
        byte[] data = getBytes(intent);
        buffer.putLong(MAGIC_RAW);
        buffer.putInt(data.length);
        os.write(buffer.array());
        os.write(data);
        os.flush();

        socket.setSoTimeout(ACK_TIMEOUT);
        int ack = socket.getInputStream().read();
        if( ack != ACK ) throw new Exception("Unexpected ACK = " + ack);
        socket.setSoTimeout(0);
    }

    @SuppressWarnings("unused")
    public static void writeIntent(Socket socket, Intent intent, Key key) throws Exception {
        OutputStream os = socket.getOutputStream();
        ByteBuffer buffer = ByteBuffer.allocate(12);
        byte[] data = getBytes(intent);
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, key);
        buffer.putLong(MAGIC_AES);
        buffer.putInt(c.getOutputSize(data.length));
        os.write(buffer.array());
        os.write(c.doFinal(data));
        os.flush();

        socket.setSoTimeout(ACK_TIMEOUT);
        int ack = socket.getInputStream().read();
        if( ack != ACK ) throw new Exception("Unexpected ACK = " + ack);
        socket.setSoTimeout(0);
    }

    public static Intent getIntent(byte[] bytes) throws IOException, ClassNotFoundException, NullPointerException {
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bis);
        Intent intent = new Intent();
        String s;
        int size;
        while (true) {
            s = (String) ois.readObject();
            switch (s) {
                case "action":
                    intent.setAction( (String) ois.readObject() );
                    break;
                case "type":
                    intent.setType( (String) ois.readObject() );
                    break;
                case "data":
                    intent.setData(Uri.parse((String) ois.readObject()));
                    break;
                case "categories":
                    size = ois.readInt();
                    for (int i = 0; i < size; i++ ) {
                        intent.addCategory((String) ois.readObject());
                    }
                    break;
                case "extras":
                    Bundle extras = getBundle(ois);
                    if ( extras != null ) {
                        intent.putExtras(extras);
                    }
                    break;
                case "end":
                    return intent;
            }
        }
    }

    public static byte[] getBytes(Intent intent) throws  IOException, NullPointerException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);

        String s;
        if ( (s = intent.getAction()) != null ) {
            oos.writeObject("action");
            oos.writeObject(s);
        }
        if ( (s = intent.getType()) != null ) {
            oos.writeObject("type");
            oos.writeObject(s);
        }
        if ( (s = intent.getDataString()) != null ) {
            oos.writeObject("data");
            oos.writeObject(s);
        }

        oos.writeObject("categories");
        Set<String> categories = intent.getCategories();
        if ( categories == null ) {
            oos.writeInt(0);
        } else {
            oos.writeInt(categories.size());
            for (String category : categories) {
                oos.writeObject(category);
            }
        }

        put(oos, "extras", intent.getExtras());

        oos.writeObject("end");
        oos.flush(); //На всякий случай, а то что-то проблемы с передачей больших объемов
        return bos.toByteArray();
    }

    private static void put(ObjectOutputStream oos, String name, Bundle bundle) throws IOException {
        oos.writeObject(name);
        if ( bundle == null ) {
            oos.writeInt(0);
            return;
        }

        int size = 0;
        for (String key : bundle.keySet()) {
            Object obj = bundle.get(key);
            if ( obj == null ) continue;
            if ( obj instanceof Serializable || obj instanceof Uri || obj instanceof Bitmap || obj instanceof Bundle || obj instanceof SpannableString) {
                size++;
            } else {
                log("Base.put: Key '" + key + "' has unsupported type '" + obj.getClass().getName() + "'");
            }
        }

        oos.writeInt(size);
        for (String key : bundle.keySet()) {
            Object obj = bundle.get(key);
            if ( obj == null ) continue;
            if ( obj instanceof Serializable ) {
                oos.writeObject("Serializable");
                oos.writeObject(key);
                oos.writeObject(obj);
            } else if ( obj instanceof SpannableString ) {
                oos.writeObject("Serializable");
                oos.writeObject(key);
                oos.writeObject(obj.toString() );
                log("PUT > SpannableString " + key + ": " + obj.toString());
            } else if ( obj instanceof Uri ) {
                oos.writeObject("Uri");
                oos.writeObject(key);
                oos.writeObject(obj.toString());
            } else if ( obj instanceof Bitmap ) {
                Bitmap bitmap = (Bitmap) obj;
                oos.writeObject("Bitmap");
                oos.writeObject(key);
                bitmap.compress(Bitmap.CompressFormat.PNG, 0, oos);
            } else if ( obj instanceof Bundle ) {
                oos.writeObject("Bundle");
                put(oos, key, (Bundle) obj);
            }
        }
    }

    private static Bundle getBundle(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        int size = ois.readInt();
        StringBuilder sb = new StringBuilder();
        sb.append("GetBundle > size = ").append(size).append('\n');

        if ( size <= 0 ) {
            log(sb.toString());
            return null;
        }

        Bundle bundle = new Bundle(size);
        for (int i = 0; i < size; i++) {
            String what = (String) ois.readObject();
            String key = (String) ois.readObject();

            String type = what;
            String info;

            switch ( what ) {
                case "Uri":
                    Uri uri = Uri.parse( (String) ois.readObject() );
                    bundle.putParcelable(key,uri);
                    info = uri.toString();
                    break;
                case "Bitmap":
                    Bitmap bitmap = BitmapFactory.decodeStream(ois);
                    bundle.putParcelable(key, bitmap);
                    info = (bitmap == null) ? "null" : bitmap.getByteCount() + " bytes";
                    break;
                case "Bundle":
                    Bundle b = getBundle(ois);
                    bundle.putBundle(key,b);
                    info = (b == null) ? "null" : b.size() + " items";
                    break;
                case "Serializable":
                    Serializable value = (Serializable) ois.readObject();
                    bundle.putSerializable(key, value);
                    type = "'" + value.getClass().getSimpleName();
                    if ( value.getClass().isArray() ) info = "{" + Array.getLength(value) + " items}";
                    else info = value.toString();
                    break;
                default:
                    info = "Unexpected type";
            }
            sb.append("    ").append(type).append(' ').append(key).append(" = ").append(info).append('\n');
        }
        log(sb.toString());
        return bundle;
    }

    @SuppressLint("DefaultLocale")
    public static String ipToString(int ip) {
        return String.format("%d.%d.%d.%d", ip & 0x0ff, (ip >>> 8) & 0x0ff, (ip >>> 16) & 0x0ff, ip >>> 24);
    }

    /**
     * Преобразует строковое представление IP-адреса в int. Только для IPv4
     *
     * @param hostAddress IP-адрес в виде строки типа 127.0.0.1
     * @return IP-адрес или -1 если hostAddress имеет неправильный формат
     */
    public static int ip(String hostAddress) {
        Matcher m = PATTERN_IP.matcher(hostAddress);
        if ( m.matches() ) {
            return
                    (Integer.parseInt( m.group(4) ) << 24) |
                    (Integer.parseInt( m.group(3) ) << 16) |
                    (Integer.parseInt( m.group(2) ) <<  8) |
                    (Integer.parseInt( m.group(1) )      );
        }
        return -1;
    }

    public static boolean isLoopback(String address) {
        return "localhost".equalsIgnoreCase(address) || (ip(address)&255)  == 127;
    }
}
