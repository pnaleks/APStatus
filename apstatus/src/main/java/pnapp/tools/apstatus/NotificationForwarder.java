package pnapp.tools.apstatus;

import android.annotation.TargetApi;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import java.util.HashSet;
import java.util.Set;

/**
 * Обработка, анализ и пересылка локальных уведомлений на удаленный узел
 *
 * @author P.N. Alekseev
 * @author pnaleks@gmail.com
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class NotificationForwarder extends NotificationListenerService {
    public NotificationForwarder() {}

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();

        // Не обрабатываю уведомления от самого себя, чтобы избежать зацикливания
        if ( packageName == null || packageName.equals(BuildConfig.APPLICATION_ID) ) return;

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Сервер выключен
        if ( !preferences.getBoolean(Settings.PREF_SERVER_MODE,false) ) return;

        Set<String> packages = preferences.getStringSet(Settings.PREF_PACKAGES_SET, null);

        if ( packages == null ) packages = new HashSet<>();

        // Первое уведомление от данного пакета после установки программы
        if ( !packages.contains(packageName) ) {
            boolean def = preferences.getBoolean(Settings.PREF_FORWARD_NEW,false);
            packages.add(packageName);
            preferences.edit()
                    .putBoolean(packageName,def)
                    .putStringSet(Settings.PREF_PACKAGES_SET, packages)
                    .apply();
            Base.log("NFS > First notification from " + packageName);
            if ( !def ) return;
        }

        if ( !preferences.getBoolean(packageName,false) ) {
            // Запрещена передача уведомлений от этого пакета
            Base.log("NFS > Disabled notification from " + packageName);
            return;
        }

        Notification notification = sbn.getNotification();
        if ( notification == null ) {
            // Нет смысла передавать уведомление без данных, возможно таких и не бывает
            Base.log("NFS > Null notification from " + packageName);
            return;
        }

        if ( (notification.flags & Notification.FLAG_LOCAL_ONLY) != 0 ) {
            // Помечено как локальное
            Base.log("NFS > Local only notification from " + packageName);
            return;
        }

        Intent intent = new Intent(this, Server.class);

        intent
                .setAction(Server.ACTION_POST)
                .putExtra(Server.EXTRA_PACKAGE, packageName)
                .putExtra(Server.EXTRA_ID, sbn.getId())
                .putExtra(Server.EXTRA_WHEN, notification.when);

        try {
            PackageManager pm = getPackageManager();
            intent.putExtra(Server.EXTRA_LABEL,
                    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            Base.log("NFS > Error: " + e.toString());
        }

        Bundle extras = NotificationCompat.getExtras(notification);

        StringBuilder sb = new StringBuilder();
        if (extras != null) {
            sb.append("NFS > Notification from ").append(packageName).append(':').append('\n');
            for( String key : extras.keySet() ) {
                Object obj = extras.get(key);
                if ( obj != null ) {
                    String log = obj.getClass().getSimpleName() + " " + key;
                    if (obj instanceof Bitmap || obj instanceof Bundle) {
                        extras.remove(key);
                        log += " removed!";
                    } else {
                        if (obj instanceof Boolean || obj instanceof Integer) {
                            log += " = " + obj.toString();
                        }
                    }
                    sb.append(log).append('\n');
                } else {
                    sb.append(key).append(" is NULL\n");
                }
            }
            intent.putExtra(Server.EXTRA_EXTRAS, extras);
        } else {
            sb.append("NFS > Notification from ").append(packageName).append(" with no extras!\n");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            String category = notification.category;
            if (category != null) {
                intent.putExtra(Server.EXTRA_CATEGORY, category);
            }
        }

        try {
            startService(intent);
        } catch ( Exception e) {
            sb.append("NFS > Exception: ").append(e.getMessage());
            Base.log(sb.toString());
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();

        // Не обрабатываю уведомления от самого себя, чтобы избежать зацикливания
        if ( packageName == null || packageName.equals(BuildConfig.APPLICATION_ID) ) return;

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        if ( !preferences.getBoolean(Settings.PREF_SERVER_MODE,false) ) return;

        if ( preferences.getBoolean(packageName,false) ) {
            Intent intent = new Intent(this, Server.class);
            intent
                    .setAction(Server.ACTION_REMOVE)
                    .putExtra(Server.EXTRA_PACKAGE, packageName)
                    .putExtra(Server.EXTRA_ID, sbn.getId());
            startService(intent);
        }
    }

    public static boolean isEnabled(Context context) {
        String packageName = context.getPackageName();

        Set<String> enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context);
        return enabledListeners.contains(packageName);

        //ContentResolver contentResolver = context.getContentResolver();
        //String enabledNotificationListeners = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners");
        //return enabledNotificationListeners != null && enabledNotificationListeners.contains(packageName);
    }
}
