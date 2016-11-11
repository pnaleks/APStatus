package ru.pnapp.notifications_packer;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.StatusBarNotification;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class ApplicationTest {
    private static final String PKG = BuildConfig.APPLICATION_ID + ".test";

    @SuppressLint("NewApi")
    @Test
    public void test() throws  Exception {
        Context context = InstrumentationRegistry.getTargetContext();

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        org.junit.Assert.assertTrue(cancelAllNotifications());


        RemoteNotificationManager remoteNotificationManager = RemoteNotificationManager.getInstance(context);

        for (int i = 1; i < 20; i++) {
            Notification notification = new NotificationCompat.Builder(context)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle("TEST " + i)
                    .setContentText("Notification no." + i)
                    .build();

            remoteNotificationManager.notify("test_app", "TEST", i, notification);
        }


        boolean ready = false;
        for (int i = 0; i < 100; i++) {
            StatusBarNotification[] statusBarNotifications = notificationManager.getActiveNotifications();
            for (StatusBarNotification notification : statusBarNotifications) {
                if (notification.getId() == 119) {
                    ready = true;
                    break;
                }
            }
            if (ready) {
                Log.i("PNApp.TEST", "Notifications are ready after " + i  + " wait cycles");
                break;
            }
        }
        org.junit.Assert.assertTrue(ready);


        StatusBarNotification[] statusBarNotifications = notificationManager.getActiveNotifications();

        int n = 0;
        for (StatusBarNotification notification : statusBarNotifications) {
            if (PKG.equals(notification.getPackageName())) n++;
        }

        org.junit.Assert.assertEquals(10, n);

        String[] xmlStrings = new String[10];
        NotificationsPacker packer = NotificationsPacker.implementation.newInstance();
        n = 0;
        for (StatusBarNotification notification : statusBarNotifications) {
            if (PKG.equals(notification.getPackageName())) {
                xmlStrings[n] = packer.pack(context, notification);
                n++;
            }
        }

        org.junit.Assert.assertTrue(cancelAllNotifications());

        remoteNotificationManager.cancelAll();

        for(String xml : xmlStrings) {
            StatusBarNotification statusBarNotification = packer.unpack(context, xml);
            org.junit.Assert.assertNotNull(statusBarNotification);
            remoteNotificationManager.notify(
                    statusBarNotification.getPackageName(),
                    statusBarNotification.getTag(),
                    statusBarNotification.getId(),
                    statusBarNotification.getNotification()
            );
        }

        //org.junit.Assert.assertEquals(n, 20);
    }

    @SuppressLint("NewApi")
    private boolean cancelAllNotifications() {
        NotificationManager notificationManager =
                (NotificationManager) InstrumentationRegistry.getTargetContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();

        boolean clean = false;
        for (int i = 0; i < 100; i++) {
            clean = true;
            StatusBarNotification[] statusBarNotifications = notificationManager.getActiveNotifications();
            for (StatusBarNotification notification : statusBarNotifications) {
                if (PKG.equals(notification.getPackageName())) {
                    clean = false;
                    break;
                }
            }
            if (clean) {
                Log.i("PNApp.TEST", "Notifications are clean after " + i  + " wait cycles");
                break;
            }
        }

        return clean;
    }

}
