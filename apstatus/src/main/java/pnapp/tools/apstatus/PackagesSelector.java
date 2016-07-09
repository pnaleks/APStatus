package pnapp.tools.apstatus;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceManager;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Create sorted list of {@link CheckBoxPreference} for installed packages.
 * Packages are sorted by application label obtained from {@link PackageManager#getApplicationLabel(ApplicationInfo)}.
 * Checked preferences appears first.
 * ApplicationIcon is also provided.<br>
 *
 * You can add preferences for all installed packages with {@link #addAll()} or for selected packages only
 * with {@link #addAll(Set)} or {@link #addAll(String)}
 *
 * @author P.N. Alekseev
 * @author pnaleks@gmail.com
 */
@SuppressWarnings("unused")
public class PackagesSelector extends TreeSet<CheckBoxPreference> {
    Context context;

    public PackagesSelector(final Context context) {
        super(new Comparator<CheckBoxPreference>() {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            @Override
            public int compare(CheckBoxPreference a, CheckBoxPreference b) {
                boolean aValue = sp.getBoolean(a.getKey(), false);
                boolean bValue = sp.getBoolean(b.getKey(), false);
                if( aValue == bValue ) {
                    return a.getTitle().toString().compareTo(b.getTitle().toString());
                } else {
                    return aValue ? -1 : 1;
                }
            }
        });
        this.context = context;
    }

    /**
     * Adds all packages which names are in the argument
     *
     * @param packages set of package names
     */
    public void addAll(Set<String> packages) {
        if( packages == null || packages.isEmpty() ) return;

        PackageManager pm = context.getPackageManager();

        for( String p : packages ) {
            if ( BuildConfig.APPLICATION_ID.equals(p) ) continue;

            CheckBoxPreference preference = new CheckBoxPreference(context);
            preference.setKey(p);
            preference.setDefaultValue(false);

            try {
                preference.setIcon( pm.getApplicationIcon(p) );
                preference.setTitle( pm.getApplicationLabel(pm.getApplicationInfo(p,0)) );
                preference.setSummary(p);
            } catch (PackageManager.NameNotFoundException e) {
                preference.setTitle(p);
            }

            add(preference);
        }
    }

    /**
     * Adds all packages installed in the system to the set
     */
    public void addAll() {
        List<PackageInfo> packageInfoList = context.getPackageManager().getInstalledPackages(0);
        HashSet<String> packages = new HashSet<>();
        for( PackageInfo packageInfo : packageInfoList ) {
            packages.add(packageInfo.packageName);
        }
        addAll(packages);
    }

    /**
     * Adds packages according to the set in {@link SharedPreferences}
     *
     * @param stringSetPreferenceKey a key for {@link SharedPreferences#getStringSet(String, Set)}
     */
    public void addAll(String stringSetPreferenceKey) {
        addAll(
                PreferenceManager.getDefaultSharedPreferences(context)
                        .getStringSet(stringSetPreferenceKey, null)
        );
    }
}
