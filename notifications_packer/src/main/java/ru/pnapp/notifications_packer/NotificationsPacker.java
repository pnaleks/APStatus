package ru.pnapp.notifications_packer;


import android.content.Context;
import android.service.notification.StatusBarNotification;

public interface NotificationsPacker {
     Class<? extends NotificationsPacker> implementation = XmlNotificationsPacker.class;

    String pack(Context context, StatusBarNotification statusBarNotification);
    StatusBarNotification unpack(Context context, String string);
}
