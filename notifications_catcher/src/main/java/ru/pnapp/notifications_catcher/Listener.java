package ru.pnapp.notifications_catcher;

import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import ru.pnapp.notifications_packer.*;
import ru.pnapp.notifications_packer.BuildConfig;

public class Listener extends NotificationListenerService {
    static final String ACTION_NOTIFICATION_POSTED = BuildConfig.APPLICATION_ID + ".notification_posted";
    static final String ACTION_NOTIFICATION_REMOVED = BuildConfig.APPLICATION_ID + ".notification_removed";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if(getPackageName().equals(sbn.getPackageName())) return;
        String string = new XmlNotificationsPacker().pack(this, sbn);
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(ACTION_NOTIFICATION_POSTED);
        intent.putExtra(Intent.EXTRA_TEXT, string);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if(getPackageName().equals(sbn.getPackageName())) return;
        String string = new XmlNotificationsPacker().pack(this, sbn);
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(ACTION_NOTIFICATION_REMOVED);
        intent.putExtra(Intent.EXTRA_TEXT, string);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}