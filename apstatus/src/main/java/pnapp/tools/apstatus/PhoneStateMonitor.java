package pnapp.tools.apstatus;

import android.content.Context;
import android.content.res.Resources;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

import java.lang.reflect.Method;

/**
 *
 */
public class PhoneStateMonitor extends PhoneStateListener {
    public static final int EVENTS  = LISTEN_DATA_CONNECTION_STATE | LISTEN_SIGNAL_STRENGTHS;

    private Server context;

    private int level = -1;
    private int state = -1;
    private int type = -1;

    private static boolean initMethods = true;
    private static Method getLteRsrp;
    private static Method getLteRssnr;
    private static Method getLteSignalStrength;
    private static int rsrpThreshType = 0;

    public void activate(Server context) {
        this.context = context;
        ((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE)).listen(this, EVENTS);
    }

    public void  deactivate() {
        ((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE)).listen(this, LISTEN_NONE);
        level = state = type = -1;
    }

    public int getLevel() { return level; }
    public int getType() { return  type; }
    public boolean isConnected() { return state == TelephonyManager.DATA_CONNECTED; }

    @Override
    public void onDataConnectionStateChanged(int connectionState, int networkType) {
        boolean post = false;

        if ( type != networkType ) {
            post = true;
            type = networkType;
        }

        if ( state != connectionState ) {
            post = post || (state == TelephonyManager.DATA_CONNECTED) || (connectionState == TelephonyManager.DATA_CONNECTED);
            state = connectionState;
        }

        if ( post && level != -1 ) {
            context.post(Server.FLAG_SIGNAL_CHANGED);
        }
    }

    @Override
    public void onSignalStrengthsChanged(SignalStrength ss) {
        int level;
        if ( ss.isGsm() ) {
            level = getLteLevel(ss);
            if ( level == 0 ) level = getGsmLevel(ss);
        } else {
            int cdma = getCdmaLevel(ss);
            int evdo = getEvdoLevel(ss);
            level = (cdma > evdo) ? cdma : evdo;
        }
        if ( this.level != level ) {
            this.level = level;
            context.post(Server.FLAG_SIGNAL_CHANGED);
        }
    }

    public static int getGsmLevel(SignalStrength ss) {
        final int level = ss.getGsmSignalStrength();
        if ( level == 99 ) return 0;
        if ( level >= 12 ) return 4;
        if ( level >=  8 ) return 3;
        if ( level >=  4 ) return 2;
        return 1;
    }

    public static int getCdmaLevel(SignalStrength ss) {
        final int cdmaDbm = ss.getCdmaDbm();
        final int cdmaEcio = ss.getCdmaEcio();
        int levelDbm;
        int levelEcio;

        if (cdmaDbm >= -75) levelDbm = 4;
        else if (cdmaDbm >= -85) levelDbm = 3;
        else if (cdmaDbm >= -95) levelDbm = 2;
        else if (cdmaDbm >= -100) levelDbm = 1;
        else levelDbm = 0;

        // Ec/Io are in dB*10
        if (cdmaEcio >= -90) levelEcio = 4;
        else if (cdmaEcio >= -110) levelEcio = 3;
        else if (cdmaEcio >= -130) levelEcio = 2;
        else if (cdmaEcio >= -150) levelEcio = 1;
        else levelEcio = 0;

        return  (levelDbm < levelEcio) ? levelDbm : levelEcio;
    }

    public static int getEvdoLevel(SignalStrength ss) {
        final int evdoDbm = ss.getEvdoDbm();
        final int evdoSnr = ss.getEvdoSnr();
        int levelEvdoDbm;
        int levelEvdoSnr;

        if (evdoDbm >= -65) levelEvdoDbm = 4;
        else if (evdoDbm >= -75) levelEvdoDbm = 3;
        else if (evdoDbm >= -90) levelEvdoDbm = 2;
        else if (evdoDbm >= -105) levelEvdoDbm = 1;
        else levelEvdoDbm = 0;

        if (evdoSnr >= 7) levelEvdoSnr = 4;
        else if (evdoSnr >= 5) levelEvdoSnr = 3;
        else if (evdoSnr >= 3) levelEvdoSnr = 2;
        else if (evdoSnr >= 1) levelEvdoSnr = 1;
        else levelEvdoSnr = 0;

        return  (levelEvdoDbm < levelEvdoSnr) ? levelEvdoDbm : levelEvdoSnr;
    }

    private static int invoke(Method method, SignalStrength ss, int defaultValue) {
        if (initMethods) {
            try {
                getLteRsrp = SignalStrength.class.getDeclaredMethod("getLteRsrp");
                getLteRsrp.setAccessible(true);
            } catch (Exception ignore) {}
            try {
                getLteRssnr = SignalStrength.class.getDeclaredMethod("getLteRssnr");
                getLteRssnr.setAccessible(true);
            } catch (Exception ignore) {}
            try {
                getLteSignalStrength = SignalStrength.class.getDeclaredMethod("getLteSignalStrength");
                getLteSignalStrength.setAccessible(true);
            } catch (Exception ignore) {}
            try {
                Resources sys = Resources.getSystem();
                int id = sys.getIdentifier("config_LTE_RSRP_threshold_type","integer","android");
                if ( id != 0 ) rsrpThreshType = sys.getInteger(id);
            } catch (Exception ignore) {}
            initMethods = false;
        }

        if ( method != null ) {
            try {
                return (int) method.invoke(ss);
            } catch (Exception e) {
                Base.log("PhoneStateMonitor> Exception at invoke: " + e.getMessage());
            }
        }
        return defaultValue;
    }

    public static int getLteLevel(SignalStrength ss) {
        /*
         * TS 36.214 Physical Layer Section 5.1.3 TS 36.331 RRC RSSI = received
         * signal + noise RSRP = reference signal dBm RSRQ = quality of signal
         * dB= Number of Resource blocks xRSRP/RSSI SNR = gain=signal/noise ratio
         * = -10log P1/P2 dB
         */
        int rssiIconLevel = 0, rsrpIconLevel = -1, snrIconLevel = -1;

        int lteRsrp = invoke(getLteRsrp, ss, 0);
        if (rsrpThreshType == 0) {
            if (lteRsrp > -44) rsrpIconLevel = -1;
            else if (lteRsrp >= -85) rsrpIconLevel = 4;
            else if (lteRsrp >= -95) rsrpIconLevel = 3;
            else if (lteRsrp >= -105) rsrpIconLevel = 2;
            else if (lteRsrp >= -115) rsrpIconLevel = 1;
            else if (lteRsrp >= -140) rsrpIconLevel = 0;
        } else {
            if (lteRsrp > -44) rsrpIconLevel = -1;
            else if (lteRsrp >= -98) rsrpIconLevel = 4;
            else if (lteRsrp >= -108) rsrpIconLevel = 3;
            else if (lteRsrp >= -118) rsrpIconLevel = 2;
            else if (lteRsrp >= -128) rsrpIconLevel = 1;
            else if (lteRsrp >= -140) rsrpIconLevel = 0;
        }

        /*
         * Values are -200 dB to +300 (SNR*10dB) RS_SNR >= 13.0 dB =>4 bars 4.5
         * dB <= RS_SNR < 13.0 dB => 3 bars 1.0 dB <= RS_SNR < 4.5 dB => 2 bars
         * -3.0 dB <= RS_SNR < 1.0 dB 1 bar RS_SNR < -3.0 dB/No Service Antenna
         * Icon Only
         */
        int lteRssnr = invoke(getLteRssnr, ss, 301);
        if (lteRssnr > 300) snrIconLevel = -1;
        else if (lteRssnr >= 130) snrIconLevel = 4;
        else if (lteRssnr >= 45) snrIconLevel = 3;
        else if (lteRssnr >= 10) snrIconLevel = 2;
        else if (lteRssnr >= -30) snrIconLevel = 1;
        else if (lteRssnr >= -200) snrIconLevel = 0;

        /* Choose a measurement type to use for notification */
        if (snrIconLevel != -1 && rsrpIconLevel != -1) {
            /*
             * The number of bars displayed shall be the smaller of the bars
             * associated with LTE RSRP and the bars associated with the LTE
             * RS_SNR
             */
            return (rsrpIconLevel < snrIconLevel ? rsrpIconLevel : snrIconLevel);
        }

        if (snrIconLevel != -1) return snrIconLevel;

        if (rsrpIconLevel != -1) return rsrpIconLevel;

        /* Valid values are (0-63, 99) as defined in TS 36.331 */
        int mLteSignalStrength = invoke(getLteSignalStrength, ss, 64);
        if (mLteSignalStrength > 63) rssiIconLevel = 0;
        else if (mLteSignalStrength >= 12) rssiIconLevel = 4;
        else if (mLteSignalStrength >= 8) rssiIconLevel = 3;
        else if (mLteSignalStrength >= 5) rssiIconLevel = 2;
        else if (mLteSignalStrength >= 0) rssiIconLevel = 1;

        return rssiIconLevel;
    }


    public static String getNetworkTypeCode(int type) {
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_EDGE:
                return "E";
            case TelephonyManager.NETWORK_TYPE_GPRS:
                return "G";
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_IDEN:
                return "2G";
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_UMTS:
                return "3G";
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
                return "H";
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
                return "H+";
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "4G";
            case TelephonyManager.NETWORK_TYPE_UNKNOWN:
            default:
                return "\u00d7";
        }
    }

    public static String getNetworkTypeName(int type) {
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_1xRTT:
                return "1xRTT";
            case TelephonyManager.NETWORK_TYPE_CDMA:
                return "CDMA";
            case TelephonyManager.NETWORK_TYPE_EDGE:
                return "EDGE";
            case TelephonyManager.NETWORK_TYPE_EHRPD:
                return "EHRPD";
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
                return "EVDO 0";
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
                return "EVDO A";
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
                return "EVDO B";
            case TelephonyManager.NETWORK_TYPE_GPRS:
                return "GPRS";
            case TelephonyManager.NETWORK_TYPE_HSDPA:
                return "HSDPA";
            case TelephonyManager.NETWORK_TYPE_HSPA:
                return "HSPA";
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return "HSPAP";
            case TelephonyManager.NETWORK_TYPE_HSUPA:
                return "HSUPA";
            case TelephonyManager.NETWORK_TYPE_IDEN:
                return "IDEN";
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "LTE";
            case TelephonyManager.NETWORK_TYPE_UMTS:
                return "UMTS";
            case TelephonyManager.NETWORK_TYPE_UNKNOWN:
                return "Unknown";
            default:
                return "Unexpected " + String.valueOf(type);
        }
    }
}
