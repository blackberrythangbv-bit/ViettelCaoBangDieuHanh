package vn.viettel.caobang.kpitammi;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "kpi_tammi_daily";
    private static final int REQ_DAILY = 720;
    private static final int REQ_RETRY = 721;
    private static final int NOTIFY_READY = 1301;
    private static final int NOTIFY_WAIT = 1302;
    private static final DateTimeFormatter VI_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("vi", "VN"));

    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult pendingResult = goAsync();
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                List<File> files = ReportClient.downloadToday(app);
                notifyReady(app, files.size());
                scheduleDaily(app);
            } catch (ReportClient.StaleReportException stale) {
                ZonedDateTime now = ZonedDateTime.now(ReportClient.VN_ZONE);
                if (now.getHour() < 9) {
                    scheduleRetry(app, 15);
                    notifyWaiting(app, "Nguồn chưa cập nhật. Tự kiểm tra lại sau 15 phút.");
                } else {
                    scheduleDaily(app);
                    notifyWaiting(app, "Nguồn KPI chưa cập nhật trước 09:00. App đã chặn báo cáo cũ.");
                }
            } catch (Exception e) {
                ZonedDateTime now = ZonedDateTime.now(ReportClient.VN_ZONE);
                if (now.getHour() < 9) scheduleRetry(app, 15); else scheduleDaily(app);
                notifyWaiting(app, "Lỗi tải báo cáo: " + safeMessage(e));
            } finally {
                pendingResult.finish();
            }
        }, "KpiTammiAlarm").start();
    }

    public static void scheduleDaily(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        ZonedDateTime now = ZonedDateTime.now(ReportClient.VN_ZONE);
        ZonedDateTime next = now.withHour(7).withMinute(20).withSecond(0).withNano(0);
        if (!next.isAfter(now)) next = next.plusDays(1);
        PendingIntent pi = receiverIntent(context, REQ_DAILY, "vn.viettel.caobang.kpitammi.DAILY");
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pi);
    }

    public static void scheduleRetry(Context context, int minutes) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long when = System.currentTimeMillis() + Math.max(1, minutes) * 60_000L;
        PendingIntent pi = receiverIntent(context, REQ_RETRY, "vn.viettel.caobang.kpitammi.RETRY");
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
    }

    private static PendingIntent receiverIntent(Context context, int requestCode, String action) {
        Intent i = new Intent(context, AlarmReceiver.class);
        i.setAction(action);
        return PendingIntent.getBroadcast(context, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "KPI → Tammi", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Trạng thái tải báo cáo KPI hằng ngày");
            nm.createNotificationChannel(ch);
        }
    }

    private static void notifyReady(Context context, int count) {
        ensureNotificationChannel(context);
        if (!canNotify(context)) return;

        Intent open = new Intent(context, MainActivity.class);
        open.putExtra("shareNow", true);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 1303, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String date = LocalDate.now(ReportClient.VN_ZONE).format(VI_DATE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Báo cáo KPI " + date + " đã sẵn sàng")
                .setContentText(count + " file đã được hậu kiểm đúng ngày. Chạm để gửi qua Tammi.")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(count + " file báo cáo đã tải và hậu kiểm đúng ngày " + date + ". Chạm thông báo để mở màn hình chia sẻ qua Tammi."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFY_READY, b.build());
    }

    private static void notifyWaiting(Context context, String message) {
        ensureNotificationChannel(context);
        if (!canNotify(context)) return;
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 1304, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("KPI → Tammi: chưa gửi")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pi);
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFY_WAIT, b.build());
    }

    private static boolean canNotify(Context context) {
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private static String safeMessage(Throwable e) {
        String s = e.getMessage();
        return s == null || s.trim().isEmpty() ? e.getClass().getSimpleName() : s;
    }
}
