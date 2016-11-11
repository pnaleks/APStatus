package pnapp.tools.apstatus;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author P.N. Alekseev
 * @author pnaleks@gmail.com
 */
public class ClientBitmapMaker {
    /** Список допустимых значений статуса батареи соответствующих иконкам в {@link #batteryBitmaps}*/
    public enum BatteryStatus { UNKNOWN, UNPLUGGED, LOW, PLUGGED, AC, USB, WIRELESS }

    private int batteryLevel = -1;
    private BatteryStatus batteryStatus = BatteryStatus.UNKNOWN;
    private int signalLevel = -1;
    private int signalType = -1;
    private boolean signalConnected;

    private Bitmap bitmap;
    private Canvas canvas;

    private boolean drawBattery;
    private boolean drawSignal;

    private Bitmap[] batteryBitmaps;

    private Path batteryBackground;
    private Path signalBackground;

    private Paint bgPaint;
    private Paint fgPaint;

    private float batteryLevelX;
    private float batteryLevelY;

    private float batteryIconX;
    private float batteryIconY;

    private float signalTypeX;
    private float signalTypeY;

    private float signalLevelX;
    private float signalLevelY;
    private float signalLevelSize;

    private float maxTextWidth;

    private String format;

    public ClientBitmapMaker(Context context) {
        Resources resources = context.getResources();
        batteryBitmaps = new Bitmap[] {
                BitmapFactory.decodeResource(resources, R.drawable.ic_battery_unknown_white_24dp),
                BitmapFactory.decodeResource(resources, R.drawable.ic_battery_full_white_24dp),
                BitmapFactory.decodeResource(resources, R.drawable.ic_battery_alert_white_24dp),
                BitmapFactory.decodeResource(resources, R.drawable.ic_power_white_24dp),
                BitmapFactory.decodeResource(resources, R.drawable.ic_battery_charging_full_white_24dp),
                BitmapFactory.decodeResource(resources, R.drawable.ic_usb_white_24dp),
                BitmapFactory.decodeResource(resources, R.drawable.ic_wifi_tethering_white_24dp)
        };

        float height = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height);
        float width = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
        float density = resources.getDisplayMetrics().density;

        float icHeight = 0;
        float icWidth = 0;
        for(Bitmap bitmap : batteryBitmaps) {
            if ( icHeight < bitmap.getHeight() ) icHeight = bitmap.getHeight();
            if ( icWidth < bitmap.getWidth()) icWidth = bitmap.getWidth();
        }

        bitmap = Bitmap.createBitmap((int)width, (int)height, Bitmap.Config.ARGB_8888);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0xffff5d42);

        fgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fgPaint.setColor(0xffffffff);
        fgPaint.setTextSize(0.6F * icHeight);
        fgPaint.setTypeface(Typeface.DEFAULT_BOLD);
        fgPaint.setTextAlign(Paint.Align.LEFT);

        Rect textBounds = new Rect();
        fgPaint.getTextBounds("100", 0, 3, textBounds);

        float cardHeight = height / 2 - density;
        batteryBackground = getCard(0, 0, width, cardHeight);
        signalBackground  = getCard(0, cardHeight + 2F*density, width, height);

        batteryIconX = 0;
        batteryIconY = (cardHeight - icHeight)/2F;

        //batteryLevelX = (width + icWidth) / 2F;
        batteryLevelX = icWidth + 2F*density;
        batteryLevelY = (cardHeight + textBounds.height())/2F;

        signalLevelSize = icHeight - 2F*density;
        signalLevelX = density;
        signalLevelY = cardHeight + 2F*density + (cardHeight + signalLevelSize)/2F;

        //signalTypeX = (width + icWidth) / 2F;
        signalTypeX = icWidth + 2F*density;
        signalTypeY = cardHeight + 2F*density + (cardHeight + textBounds.height())/2F;

        maxTextWidth = width - icWidth - 3F*density;

        canvas = new Canvas(bitmap);

        format = resources.getString(R.string.bitmap_maker_format);
    }


    public ClientBitmapMaker set(JSONObject jsonObject) {
        BatteryStatus status;

        try {
            status =  BatteryStatus.valueOf(jsonObject.getString(Base.EXTRA_BATTERY_STATUS));
        } catch (JSONException|IllegalArgumentException e) {
            status = batteryStatus;
        }
        setBatteryStatus(status);
        setBatteryLevel(jsonObject.optInt(Base.EXTRA_BATTERY_LEVEL, batteryLevel));
        setSignalLevel(jsonObject.optInt(Base.EXTRA_SIGNAL_LEVEL, signalLevel));
        setSignalType(jsonObject.optInt(Base.EXTRA_SIGNAL_TYPE, signalType));
        setSignalConnected(jsonObject.optBoolean(Base.EXTRA_SIGNAL_CONNECTED, signalConnected));

        return this;
    }

    public ClientBitmapMaker set(Intent intent) {
        try {
            setBatteryStatus(
                    BatteryStatus.valueOf(
                            intent.getStringExtra(Base.EXTRA_BATTERY_STATUS)
                    )
            );
        } catch (IllegalArgumentException|NullPointerException ignore) {}

        setBatteryLevel(intent.getIntExtra(Base.EXTRA_BATTERY_LEVEL, batteryLevel));
        setSignalLevel(intent.getIntExtra(Base.EXTRA_SIGNAL_LEVEL, signalLevel));
        setSignalType(intent.getIntExtra(Base.EXTRA_SIGNAL_TYPE, signalType));
        setSignalConnected(intent.getBooleanExtra(Base.EXTRA_SIGNAL_CONNECTED, signalConnected));

        return this;
    }

    public ClientBitmapMaker setBatteryStatus(BatteryStatus status) {
        if ( batteryStatus != status ) {
            batteryStatus = status;
            drawBattery = true;
        }
        return this;
    }


    public ClientBitmapMaker setBatteryLevel(int level) {
        if ( batteryLevel != level ) {
            batteryLevel = level;
            drawBattery = true;
        }
        return this;
    }

    public ClientBitmapMaker setSignalLevel(int level) {
        if ( signalLevel != level ) {
            signalLevel = level;
            drawSignal = true;
        }
        return this;
    }

    public ClientBitmapMaker setSignalType(int type) {
        if ( signalType != type  ) {
            signalType = type;
            drawSignal = true;
        }
        return this;
    }

    public ClientBitmapMaker setSignalConnected(boolean connected) {
        if ( signalConnected != connected ) {
            signalConnected = connected;
            drawSignal = true;
        }
        return this;
    }

    public Path getCard(float left, float top, float right, float bottom) {
        Path path = new Path();

        float corner = (bottom - top) / 3F;

        path.moveTo(left+corner,top);
        path.lineTo(right, top);
        path.lineTo(right, bottom - corner);
        path.lineTo(right - corner, bottom);
        path.lineTo(left, bottom);
        path.lineTo(left, top + corner);
        path.close();

        return path;
    }

    public Bitmap draw() {
        if ( drawBattery ) {
            canvas.drawPath(batteryBackground, bgPaint);

            canvas.drawBitmap(batteryBitmaps[batteryStatus.ordinal()], batteryIconX, batteryIconY, fgPaint);

            String str = String.valueOf(batteryLevel)+'%';

            float width = fgPaint.measureText(str);
            if ( width > maxTextWidth ) {
                fgPaint.setTextScaleX(maxTextWidth/width);
                canvas.drawText(str, batteryLevelX, batteryLevelY, fgPaint);
                fgPaint.setTextScaleX(1);
            } else {
                canvas.drawText(str, batteryLevelX, batteryLevelY, fgPaint);
            }

            drawBattery = false;
        }

        if ( drawSignal ) {
            canvas.drawPath(signalBackground, bgPaint);

            Path p1 = new Path();

            fgPaint.setAlpha(0x7f);

            p1.moveTo(signalLevelX, signalLevelY);
            p1.lineTo(signalLevelX + signalLevelSize, signalLevelY);
            p1.lineTo(signalLevelX + signalLevelSize, signalLevelY - signalLevelSize);
            p1.close();
            canvas.drawPath(p1, fgPaint);

            if ( signalConnected ) fgPaint.setAlpha(0xff);

            String str = PhoneStateMonitor.getNetworkTypeCode(signalType);
            float width = fgPaint.measureText(str);
            if ( width > maxTextWidth ) {
                fgPaint.setTextScaleX(maxTextWidth/width);
                canvas.drawText(str, signalTypeX, signalTypeY, fgPaint);
                fgPaint.setTextScaleX(1);
            } else {
                canvas.drawText(str, signalTypeX, signalTypeY, fgPaint);
            }

            fgPaint.setAlpha(0xff);

            if ( signalLevel > 0 ) {
                float size = signalLevelSize * signalLevel / 4F;
                p1.rewind();
                p1.moveTo(signalLevelX, signalLevelY);
                p1.lineTo(signalLevelX + size, signalLevelY);
                p1.lineTo(signalLevelX + size, signalLevelY - size);
                p1.close();
                canvas.drawPath(p1, fgPaint);
            }

            drawSignal = false;
        }

        return bitmap;
    }

    @Override
    public String toString() {
        String status = "";
        switch (batteryStatus) {
            case AC: status = "AC"; break;
            case USB: status = "USB"; break;
            case WIRELESS: status = "WL"; break;
        }

        String type = signalConnected ? PhoneStateMonitor.getNetworkTypeName(signalType) : "";

        int i = signalLevel > 0 && signalLevel < SIGNAL_LEVEL_CHARS.length ? signalLevel : 0;
        return String.format(format, batteryLevel, status, SIGNAL_LEVEL_CHARS[i], type);
    }

    public static char[] SIGNAL_LEVEL_CHARS = {'\u00d7','\u00bc','\u00bd','\u00be','4'};
}
