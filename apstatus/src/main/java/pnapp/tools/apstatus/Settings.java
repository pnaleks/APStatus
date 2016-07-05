package pnapp.tools.apstatus;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;

import java.util.List;
import java.util.Set;

/**
 * @author P.N. Alekseev
 * @author pnaleks@gmail.com
 */
public class Settings extends PreferenceActivity {
    public static final String PREF_SERVER_MODE = "pref_server_mode";
    public static final String PREF_CLIENT_MODE = "pref_client_mode";

    public static final String PREF_SERVER_NAME = "pref_server_name";
    public static final String PREF_CLIENT_NAME = "pref_client_name";

    public static final String SERVER_KEY = "pref_server_key";
    public static final String CLIENT_KEY = "pref_client_key";

    public static final String PREF_PACKAGES_SET = "pref_packages_set";
    public static final String PREF_FORWARD_NEW = "pref_forward_new";

    public static final String PREF_CONNECT_GATEWAY = "pref_connect_gateway";
    public static final String PREF_CONNECT_ADDRESS = "pref_connect_address";
    public static final String PREF_CONNECT_PORT = "pref_connect_port";

    public static final String PREF_LISTEN_PORT = "pref_listen_port";
    public static final String PREF_AP_MODE = "pref_ap_only";
    public static final String PREF_BIND = "pref_bind";

    /** {@inheritDoc} */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /** {@inheritDoc} */
    @Override
    public void onBuildHeaders(List<Header> target) { loadHeadersFromResource(R.xml.pref_headers, target); }

    @Override
    protected boolean isValidFragment(String fragmentName) { return true; }

    public static class ServerFragment extends PreferenceFragment {
        private boolean serverMode;
        private boolean accessPointMode;

        private void switchOn() {
            SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();
            Context context = getActivity();

            if ( isLoopbackMode(prefs) ) {
                disableComponent(context, AccessPointModeDetector.class);
                disableComponent(context, ConnectivityDetector.class);
                context.startService( new Intent(Base.ACTION_START, Uri.parse(Base.LOOPBACK), context, Server.class) );
                return;
            }

            if ( accessPointMode ) {
                enableComponent(context, AccessPointModeDetector.class);
                disableComponent(context, ConnectivityDetector.class);
                context.sendBroadcast(new Intent(Base.ACTION_CHECK, null, context, AccessPointModeDetector.class));
            } else {
                disableComponent(context, AccessPointModeDetector.class);
                enableComponent(context, ConnectivityDetector.class);
                context.sendBroadcast(new Intent(Base.ACTION_CHECK, null, context, ConnectivityDetector.class));
            }
        }

        private void switchOff() {
            Context context = getActivity();
            disableComponent(context, AccessPointModeDetector.class);
            disableComponent(context, ConnectivityDetector.class);
            context.startService( new Intent(Base.ACTION_EXIT, null, context, Server.class) );
        }

        private Preference.OnPreferenceChangeListener mListener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                switch ( preference.getKey() ) {
                    case PREF_SERVER_MODE:
                        serverMode = (boolean) newValue;
                        if ( serverMode ) switchOn(); else switchOff();
                        return true;
                    case PREF_AP_MODE:
                        accessPointMode = (boolean) newValue;
                        if ( serverMode ) switchOn();
                        return true;
                    case PREF_BIND:
                        break;
                    case PREF_SERVER_NAME:
                        preference.setSummary( newValue.toString() );
                        break;
                    case PREF_LISTEN_PORT:
                        try {
                            int port = Integer.valueOf((String) newValue);
                            if (port < 1024 || port > 65535) return false;
                        } catch ( Exception e ) {
                            return false;
                        }
                        preference.setSummary(newValue.toString());
                        break;
                    default:
                        return false;
                }

                if ( serverMode ) {
                    Context context = getActivity();
                    context.startService(new Intent(Server.ACTION_START, null, context, Server.class));
                }

                return true;
            }
        };

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_server);

            SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();
            Preference p;

            serverMode = prefs.getBoolean(PREF_SERVER_MODE,false);
            accessPointMode = prefs.getBoolean(PREF_AP_MODE,true);

            findPreference(PREF_SERVER_MODE).setOnPreferenceChangeListener(mListener);
            findPreference(PREF_AP_MODE).setOnPreferenceChangeListener(mListener);

            p = findPreference(PREF_SERVER_NAME);
            p.setOnPreferenceChangeListener(mListener);
            p.setSummary(prefs.getString(PREF_SERVER_NAME, Server.DEFAULT_SERVER_NAME));

            p = findPreference(PREF_LISTEN_PORT);
            p.setOnPreferenceChangeListener(mListener);
            p.setSummary(prefs.getString(PREF_LISTEN_PORT, null));
        }
    }

    public static class ClientFragment extends PreferenceFragment {
        boolean clientMode;
        boolean connectGateway;
        String connectAddress;
        boolean loopback;

        private void switchLoopback() {
            SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();
            Context context = getActivity();

            boolean newValue = clientMode && !connectGateway && SocketTask.isLoopback(connectAddress);

            if ( newValue == loopback ) return;

            loopback = newValue;

            if (prefs.getBoolean(PREF_SERVER_MODE,false) ) {
                if( loopback ) {
                    disableComponent(context, AccessPointModeDetector.class);
                    disableComponent(context, ConnectivityDetector.class);
                    context.startService(new Intent(Base.ACTION_START, Uri.parse(Base.LOOPBACK), context, Server.class));
                    return;
                }

                if (prefs.getBoolean(PREF_AP_MODE, true)) {
                    enableComponent(context, AccessPointModeDetector.class);
                    disableComponent(context, ConnectivityDetector.class);
                    context.sendBroadcast(new Intent(Base.ACTION_CHECK, null, context, AccessPointModeDetector.class));
                } else {
                    context.startService(new Intent(Base.ACTION_EXIT, null, context, Server.class));
                    disableComponent(context, AccessPointModeDetector.class);
                    enableComponent(context, ConnectivityDetector.class);
                    context.sendBroadcast(new Intent(Base.ACTION_CHECK, null, context, ConnectivityDetector.class));
                }

            }
        }

        private Preference.OnPreferenceChangeListener mListener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                switch ( preference.getKey() ) {
                    case PREF_CLIENT_MODE:
                        clientMode = (boolean) newValue;
                        switchLoopback();
                        if ( !clientMode ) {
                            getActivity().startService(new Intent(Client.ACTION_EXIT, null, getActivity(), Client.class));
                            return true;
                        }
                        break;
                    case PREF_CONNECT_ADDRESS:
                        connectAddress = (String) newValue;
                        switchLoopback();
                        preference.setSummary( connectAddress );
                        break;
                    case PREF_CLIENT_NAME:
                        String str = newValue.toString();
                        preference.setSummary( str );
                        break;
                    case PREF_CONNECT_GATEWAY:
                        connectGateway = (boolean) newValue;
                        switchLoopback();
                        findPreference(PREF_CONNECT_ADDRESS).setEnabled( !connectGateway );
                        break;
                    case PREF_CONNECT_PORT:
                        try {
                            int port = Integer.valueOf((String) newValue);
                            if (port < 1 || port > 65535) return false;
                        } catch ( Exception e ) {
                            return false;
                        }
                        preference.setSummary( newValue.toString() );
                        break;
                    default:
                        return false;
                }

                if ( clientMode ) {
                    Context context = getActivity();
                    Client.updateComponentsState(context, PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
                    context.startService(new Intent(Client.ACTION_START, null, context, Client.class));
                }
                return true;
            }
        };

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_client);

            SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();
            Preference p;

            clientMode = prefs.getBoolean(PREF_CLIENT_MODE,false);
            connectAddress = prefs.getString(PREF_CONNECT_ADDRESS, Base.LOOPBACK);
            connectGateway = prefs.getBoolean(PREF_CONNECT_GATEWAY, true);

            loopback = clientMode && !connectGateway && SocketTask.isLoopback(connectAddress);

            findPreference(PREF_CLIENT_MODE).setOnPreferenceChangeListener(mListener);
            findPreference(PREF_CONNECT_GATEWAY).setOnPreferenceChangeListener(mListener);

            p = findPreference(PREF_CLIENT_NAME);
            p.setOnPreferenceChangeListener(mListener);
            p.setSummary(prefs.getString(PREF_CLIENT_NAME, Client.DEFAULT_CLIENT_NAME));


            p = findPreference(PREF_CONNECT_ADDRESS);
            p.setOnPreferenceChangeListener(mListener);
            p.setSummary(connectAddress);
            p.setEnabled(!connectGateway);

            p = findPreference(PREF_CONNECT_PORT);
            p.setOnPreferenceChangeListener(mListener);
            p.setSummary(prefs.getString(PREF_CONNECT_PORT, null));
        }
    }

    public static class PackagesFragment extends PreferenceFragment {
        Preference prefSystemSettings;
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Activity context = getActivity();

            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
            PackageManager pm = context.getPackageManager();

            Set<String> prefPackagesSet = getPreferenceManager().getSharedPreferences().getStringSet(PREF_PACKAGES_SET, null);

            prefSystemSettings = new Preference(context);
            prefSystemSettings.setTitle(R.string.pref_system_settings);
            prefSystemSettings.setIntent(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            screen.addPreference(prefSystemSettings);

            CheckBoxPreference preference = new CheckBoxPreference(context);
            preference.setKey("pref_forward_new");
            preference.setTitle(R.string.pref_forward_new);
            preference.setSummaryOn(R.string.pref_forward_new_summary_on);
            preference.setSummaryOff(R.string.pref_forward_new_summary_off);
            preference.setDefaultValue(false);
            screen.addPreference(preference);

            if ( prefPackagesSet == null ) {
                Preference info = new Preference(context);
                info.setSummary(R.string.pref_no_packages_detected_summary);
                screen.addPreference(info);
            } else {
                // TODO: Сделать сортировку по алфавиту
                // TODO: Отображать сначала отмеченные пакеты
                for( String packageName : prefPackagesSet ) {
                    // Игнорирую собственный пакет. На всякий случай!
                    if ( BuildConfig.APPLICATION_ID.equals(packageName) ) continue;

                    CharSequence title;
                    CharSequence summary = null;
                    Drawable icon = null;
                    try {
                        ApplicationInfo applicationInfo = pm.getApplicationInfo(packageName,0);
                        title = pm.getApplicationLabel(applicationInfo);
                        icon = pm.getApplicationIcon(packageName);
                        summary = packageName;
                    } catch (PackageManager.NameNotFoundException e) {
                        title = packageName;
                    }

                    preference = new CheckBoxPreference(context);
                    preference.setKey(packageName);
                    preference.setTitle(title);
                    if ( summary != null ) preference.setSummary(summary);
                    if ( icon != null ) preference.setIcon(icon);
                    preference.setDefaultValue(false);
                    screen.addPreference(preference);
                }
            }

            setPreferenceScreen(screen);
        }

        @Override
        public void onResume() {
            super.onResume();
            if ( prefSystemSettings != null ) {
                boolean enabled = NotificationForwarder.isEnabled(getActivity());
                prefSystemSettings
                        .setSummary(enabled ? R.string.pref_system_settings_summary_enabled : R.string.pref_system_settings_summary_disabled);
            }
        }
    }

    /**
     * Если клиент настроен на локальное подключение то и сервер нужно активирован на локальном интерфейсе
     *
     * @param preferences Ссылка на {@link SharedPreferences} в контексте вызывающей процедуры
     * @return истину, если клиент настроен на локальное подключение
     */
    public static boolean isLoopbackMode(SharedPreferences preferences) {
        return preferences.getBoolean(PREF_CLIENT_MODE,false)
                && !preferences.getBoolean(PREF_CONNECT_GATEWAY,true)
                && SocketTask.isLoopback(preferences.getString(Settings.PREF_CONNECT_ADDRESS,Base.LOOPBACK));
    }

    public static boolean enableComponent(Context pkg, Class<?> cls) {
        PackageManager packageManager = pkg.getPackageManager();
        ComponentName componentName = new ComponentName(pkg, cls);
        if (packageManager.getComponentEnabledSetting(componentName) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            packageManager.setComponentEnabledSetting(componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            Base.log("Settings > " + componentName.toString() + " has been enabled");
            return true;
        }
        return false;
    }

    public static boolean disableComponent(Context pkg, Class<?> cls) {
        PackageManager packageManager = pkg.getPackageManager();
        ComponentName componentName = new ComponentName(pkg, cls);
        if (packageManager.getComponentEnabledSetting(componentName) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            packageManager.setComponentEnabledSetting(componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            Base.log("Settings > " + componentName.toString() + " has been disabled");
            return true;
        }
        return false;
    }
}
