package pnapp.tools.apstatus;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;

/**
 * В соответсвии с настройками программы запускается сервер и/или клиент. Если же обе службы отключены, запускаются настройки.
 *
 * @author P.N. Alekseev
 * @author pnaleks@gmail.com
 */
public class Starter extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        boolean launchSettings = true;

        if ( preferences.getBoolean(Settings.PREF_SERVER_MODE,false) ) {
            if ( Settings.isLoopbackMode(preferences) ) {
                startService(new Intent(Base.ACTION_LAUNCH, Uri.parse(Base.LOOPBACK), this, Server.class));
            } else {
                sendBroadcast(new Intent(Base.ACTION_LAUNCH));
            }
            launchSettings = false;
        }

        if ( preferences.getBoolean(Settings.PREF_CLIENT_MODE,false) ) {
            Client.updateComponentsState(this, PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
            startService(new Intent(Base.ACTION_LAUNCH, null, this, Client.class));
            launchSettings = false;
        }

        if  ( launchSettings ) {
            startActivity(new Intent(this, Settings.class));
        }
        finish();
    }
}
