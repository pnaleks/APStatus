package ru.pnapp.notifications_packer;

import android.app.Notification;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;

import java.util.LinkedList;

/* TODO: Look at README.txt!!! */
@SuppressWarnings("WeakerAccess")
public class RemoteNotificationManager {
    private final static String TAG = "remote_notification";
    private int notificationId;
    private LinkedList<Alias> aliases;
    private NotificationManagerCompat notificationManager;

    private static RemoteNotificationManager instance;

    public static RemoteNotificationManager getInstance(Context context) {
        if (instance == null) instance = new RemoteNotificationManager(context);
        return instance;
    }

    public RemoteNotificationManager(Context context) {
        aliases = new LinkedList<>();
        notificationManager = NotificationManagerCompat.from(context);
    }

    static class Alias {
        int localId;
        int id;
        String tag;
        String packageName;

        Alias(@NonNull String packageName, @Nullable String tag, int id) {
            this.packageName = packageName;
            this.tag = tag;
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            if (hashCode() != obj.hashCode()) return false;
            if (obj instanceof Alias) {
                Alias other = (Alias) obj;
                if (other.id != id || !other.packageName.equals(packageName)) return false;
                if (tag == null) return other.tag == null;
                return tag.equals(other.tag);
            }
            return false;
        }

        @Override
        public int hashCode() {
            final int prime = 37;
            int h = prime + id;
            h = prime * h + packageName.hashCode();
            if (tag != null) h = prime * h + tag.hashCode();
            return h;
        }
    }

    private Alias getAlias(String packageName, String tag, int id) {
        Alias alias = new Alias(packageName, tag, id);
        for (Alias old : aliases) {
            if (old.equals(alias)) return old;
        }
        alias.localId = ++notificationId;
        aliases.add(alias);
        return alias;
    }

    public void notify(String packageName, int id, Notification notification) {
        notify(packageName, null, id, notification);
    }

    public void  notify(String packageName, String tag, int id, Notification notification) {
        Alias alias = getAlias(packageName, tag, id);
        notificationManager.notify(TAG, alias.localId, notification);
    }

    public void cancel(String packageName, int id) {
        cancel(packageName, null, id);
    }

    public void cancel(String packageName, String tag, int id) {
        Alias alias = new Alias(packageName, tag, id);
        for (Alias old : aliases) {
            if (old.equals(alias)) {
                notificationManager.cancel(TAG, old.localId);
                aliases.remove(old);
            }
        }
    }

    void cancelAll() {
        for (Alias alias : aliases) {
            notificationManager.cancel(alias.tag, alias.localId);
        }
        aliases.clear();
    }
}
