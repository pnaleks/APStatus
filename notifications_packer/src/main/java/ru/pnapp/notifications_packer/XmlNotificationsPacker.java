package ru.pnapp.notifications_packer;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Process;
import android.service.notification.StatusBarNotification;
import android.support.v7.app.NotificationCompat;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.StringWriter;

public class XmlNotificationsPacker implements NotificationsPacker {

    @Override
    public String pack(Context context, StatusBarNotification statusBarNotification) {
        Serializer serializer = new Persister();
        XmlNotification xmlNotification = new XmlNotification(context, statusBarNotification);
        StringWriter stringWriter = new StringWriter();
        try {
            serializer.write(xmlNotification, stringWriter);
            return stringWriter.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public StatusBarNotification unpack(Context context, String string) {
        Serializer serializer = new Persister();
        try {
            return serializer.read(XmlNotification.class, string).get(context);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @SuppressWarnings("WeakerAccess")
    @Root(name = "notification", strict = false)
    public static class XmlNotification {
        @Attribute
        public String packageName;

        @Attribute
        public int id;

        @Attribute(required = false)
        public String packageLabel;

        @Attribute(required = false)
        public String tag;

        @Attribute(required = false)
        public long postTime;

        @Attribute(required = false)
        public boolean clearable;

        @Attribute(required = false)
        public boolean ongoing;

        @Element(required = false)
        public String title;

        @Element(required = false)
        public String text;

        @Element(required = false)
        public String sub;

        public XmlNotification(Context context, StatusBarNotification sbn) {
            packageName = sbn.getPackageName();

            try {
                PackageManager pm = context.getPackageManager();
                packageLabel = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                packageLabel = null;
            }

            tag = sbn.getTag();
            id = sbn.getId();
            postTime = sbn.getPostTime();
            clearable = sbn.isClearable();
            ongoing = sbn.isOngoing();

            Bundle extras = NotificationCompat.getExtras(sbn.getNotification());
            if( extras != null ) {
                title = extras.getString(NotificationCompat.EXTRA_TITLE);
                text = extras.getString(NotificationCompat.EXTRA_TEXT);
                sub = extras.getString(NotificationCompat.EXTRA_SUB_TEXT);
            }
        }

        public StatusBarNotification get(Context context) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
            if( title != null ) builder.setContentTitle(title);
            if( text != null ) builder.setContentText(text);
            if( sub != null ) builder.setSubText(sub);
            builder.setSmallIcon(android.R.drawable.ic_popup_reminder);

            return new StatusBarNotification(packageName, packageName, id, tag, 0, 0, 0, builder.build(), Process.myUserHandle(), postTime);
        }
    }
}
