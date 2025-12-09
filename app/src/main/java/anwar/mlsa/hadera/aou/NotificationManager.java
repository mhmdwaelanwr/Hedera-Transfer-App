package anwar.mlsa.hadera.aou;

import android.content.Context;
import android.content.SharedPreferences;

public class NotificationManager {

    private static final String PREF_NAME = "NotificationPrefs";
    private static final String NOTIFICATIONS_ENABLED_KEY = "notifications_enabled";

    public static void setNotificationsEnabled(Context context, boolean enabled) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        editor.putBoolean(NOTIFICATIONS_ENABLED_KEY, enabled);
        editor.apply();
    }

    public static boolean areNotificationsEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(NOTIFICATIONS_ENABLED_KEY, true); // Default to true
    }
}
