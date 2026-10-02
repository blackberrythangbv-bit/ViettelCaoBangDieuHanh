package vn.viettel.caobang.kpitammi.v2;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Calendar;

public final class Scheduler {
    private static final String PREF = "kpi_tammi";
    private Scheduler() {}

    public static int hour(Context c) { return prefs(c).getInt("hour", 7); }
    public static int minute(Context c) { return prefs(c).getInt("minute", 39); }

    public static void saveAndSchedule(Context c, int hour, int minute) {
        prefs(c).edit().putInt("hour", hour).putInt("minute", minute).apply();
        schedule(c);
    }

    public static void schedule(Context c) {
        int h = hour(c), m = minute(c);
        Calendar first = Calendar.getInstance();
        first.set(Calendar.HOUR_OF_DAY, h);
        first.set(Calendar.MINUTE, m);
        first.set(Calendar.SECOND, 0);
        first.set(Calendar.MILLISECOND, 0);
        if (first.getTimeInMillis() <= System.currentTimeMillis()) first.add(Calendar.DAY_OF_YEAR, 1);

        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(c, DownloadReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 22002, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (am != null) {
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, first.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY, pi);
        }
    }

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
