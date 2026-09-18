package com.viettel.caobang.dieuhanh;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

public class ReportAutoService extends Service {
    private static final String CHANNEL_ID = "vt_report_auto";
    private static final int NOTI_ID = 7300;

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTI_ID, buildNotification("Đang lấy báo cáo KPI ngày..."));

        if (ReportFetchController.alreadyAutoDownloadedToday(this)) {
            updateNotification("Báo cáo hôm nay đã được tải");
            stopSelf();
            return START_NOT_STICKY;
        }

        ReportFetchController.fetch(this, new ReportFetchController.Callback() {
            @Override
            public void onSuccess(String fileName) {
                getSharedPreferences(ReportFetchController.PREFS, Context.MODE_PRIVATE)
                        .edit().remove("retry_date").remove("retry_count").apply();
                updateNotification("Đã tải " + fileName);
                stopSelf();
            }

            @Override
            public void onError(String message) {
                int retry = nextRetryCount();
                if (retry <= 4) {
                    ReportScheduler.scheduleRetry(ReportAutoService.this, 15);
                    updateNotification("Chưa lấy được báo cáo, sẽ thử lại sau 15 phút");
                } else {
                    updateNotification("Không lấy được báo cáo hôm nay. Mở app để kiểm tra đăng nhập.");
                }
                stopSelf();
            }
        });

        return START_NOT_STICKY;
    }

    private int nextRetryCount() {
        String today = ReportFetchController.todayIso();
        String retryDate = getSharedPreferences(ReportFetchController.PREFS, Context.MODE_PRIVATE)
                .getString("retry_date", "");
        int count = getSharedPreferences(ReportFetchController.PREFS, Context.MODE_PRIVATE)
                .getInt("retry_count", 0);
        if (!today.equals(retryDate)) count = 0;
        count++;
        getSharedPreferences(ReportFetchController.PREFS, Context.MODE_PRIVATE)
                .edit().putString("retry_date", today).putInt("retry_count", count).apply();
        return count;
    }

    private void ensureChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "Báo cáo KPI tự động",
                NotificationManager.IMPORTANCE_LOW
        );
        ch.setDescription("Tự động lấy báo cáo KPI ngày lúc 07:30");
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivityV124.class);
        PendingIntent pi = PendingIntent.getActivity(
                this,
                7300,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Viettel Cao Bằng")
                .setContentText(text)
                .setContentIntent(pi)
                .setOnlyAlertOnce(true)
                .setOngoing(false)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTI_ID, buildNotification(text));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
