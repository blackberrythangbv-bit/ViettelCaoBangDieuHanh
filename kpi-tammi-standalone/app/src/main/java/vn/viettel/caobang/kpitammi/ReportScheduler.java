package vn.viettel.caobang.kpitammi;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class ReportScheduler {
    private ReportScheduler() {}

    public static void scheduleNext0730(Context c) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        ZonedDateTime next = now.withHour(7).withMinute(30).withSecond(0).withNano(0);
        if (!next.isAfter(now)) next = next.plusDays(1);
        scheduleAt(c, next.toInstant().toEpochMilli(), 0);
        c.getSharedPreferences("report_state", Context.MODE_PRIVATE).edit().putLong("next_0730", next.toInstant().toEpochMilli()).apply();
    }

    public static void scheduleRetry(Context c, int attempt) {
        scheduleAt(c, System.currentTimeMillis() + 15 * 60 * 1000L, attempt);
    }

    private static void scheduleAt(Context c, long when, int attempt) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(c, AlarmReceiver.class).putExtra("attempt", attempt);
        PendingIntent pi = PendingIntent.getBroadcast(c, 7300, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        else if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        else am.set(AlarmManager.RTC_WAKEUP, when, pi);
    }
}
