package ru.pnapp.notifications_catcher;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import ru.pnapp.notifications_packer.XmlNotificationsPacker;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if( getIntent().hasExtra(Intent.EXTRA_TEXT) ) {
            String string = getIntent().getStringExtra(Intent.EXTRA_TEXT);
            ((TextView) findViewById(R.id.text)).setText(getIntent().getAction() + '\n' + string);
            Notification notification = (new XmlNotificationsPacker()).unpack(this, string).getNotification();
            if( notification != null ) {
                ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(1, notification);
            }

        } else {
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            finish();
        }
    }
}
