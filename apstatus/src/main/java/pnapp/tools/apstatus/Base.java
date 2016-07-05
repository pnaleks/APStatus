package pnapp.tools.apstatus;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.Key;
import java.text.DateFormat;
import java.util.Date;

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

    abstract protected void onRead(Intent intent);
    abstract protected void onWrite(SocketTask writer);
    abstract protected void onError(SocketTask task);
    abstract protected void onApproved(SocketTask task);
    abstract protected void onWatchdog();

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
                    if ( !"Socket closed".equals(task.getMessage()) ) { onError(task); }
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
        if (size <= 0 || size > MAX_DATA_SIZE) throw new IOException("Bad data size " + size);

        byte[] data = new byte[size];
        size = is.read(data);
        if (size == -1) throw new IOException("Unexpected EOF");
        if (size != data.length) throw new IOException("Read " + size + " bytes, " + data.length + " expected");

        String json = new String(data);
        log("readIntent:\n" + json);
        Intent intent = (new Gson()).fromJson(json, Intent.class);
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

        Intent intent = (new Gson()).fromJson(new String(data), Intent.class);
        OutputStream os = socket.getOutputStream();
        os.write(ACK);
        os.flush();
        return intent;
    }

    public static void writeIntent(Socket socket, Intent intent) throws Exception {
        OutputStream os = socket.getOutputStream();
        ByteBuffer buffer = ByteBuffer.allocate(12);

        Gson gson = (new GsonBuilder())
                .addSerializationExclusionStrategy(new ExclusionStrategy() {
                    @Override
                    public boolean shouldSkipField(FieldAttributes f) {
                        return "mClassLoader".equals(f.getName());
                    }

                    @Override
                    public boolean shouldSkipClass(Class<?> clazz) {
                        return false;
                    }
                })
                .create();
        String json =  gson.toJson(intent);
        log("writeIntent:\n" + json);
        byte[] data = json.getBytes();
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
        byte[] data = (new Gson()).toJson(intent).getBytes();
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
}
