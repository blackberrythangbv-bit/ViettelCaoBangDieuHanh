package com.viettel.caobang.dieuhanh;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.ZonedDateTime;

public final class ReportScheduler {
    public static final String ACTION_DAILY = "com.viettel.caobang.dieuhanh.AUTO_REPORT_0730";
    public static final String ACTION_RETRY = "com.viettel.caobang.dieuhanh.AUTO_REPORT_RETRY";
    private static final int REQ_DAILY = 7301;
    private static final int REQ_RETRY = 7302;

    private ReportScheduler() {}

    public static void scheduleNext(Context context) {
        ZonedDateTime now = ZonedDateTime.now(ReportFetchController.VN_ZONE);
        ZonedDateTime next = now.withHour(7).withMinute(30).withSecond(0).withNano(0);
        if (!next.isAfter(now)) next = next.plusDays(1);
        schedule(context, ACTION_DAILY, REQ_DAILY, next.toInstant().toEpochMilli(), true);
    }

    public static void scheduleRetry(Context context, int minutes) {
        long at = System.currentTimeMillis() + Math.max(5, minutes) * 60_000L;
        schedule(context, ACTION_RETRY, REQ_RETRY, at, false);
    }

    private static void schedule(Context context, String action, int requestCode, long triggerAt, boolean preferExact) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(context, ReportAlarmReceiver.class).setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (preferExact && Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else if (preferExact && Build.VERSION.SDK_INT < 31) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }
}
