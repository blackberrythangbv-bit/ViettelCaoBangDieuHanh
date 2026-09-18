package vn.viettel.caobang.kpitammi;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AutoReportService extends Service {
    private static final String CHANNEL_ID = "kpi_report_auto";
    private static final int NOTI_ID = 7301;
    private ReportWebRunner runner;
    private int attempt;
    private boolean manual;

    public static void startNow(Context context, int attempt, boolean manual) {
        Intent i = new Intent(context, AutoReportService.class)
                .putExtra("attempt", attempt)
                .putExtra("manual", manual);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
        else context.startService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        attempt = intent != null ? intent.getIntExtra("attempt", 0) : 0;
        manual = intent != null && intent.getBooleanExtra("manual", false);
        startForeground(NOTI_ID, notification("Đang lấy báo cáo KPI..."));
        saveMessage("Đang lấy báo cáo...");

        runner = new ReportWebRunner(this, new ReportWebRunner.Callback() {
            @Override
            public void onSuccess(String fileName) {
                String time = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
                saveMessage("Đã lấy " + fileName + " lúc " + time);
                notifyFinal("Đã lấy báo cáo", fileName);
                ReportScheduler.scheduleNext0730(AutoReportService.this);
                stopSelf();
            }

            @Override
            public void onError(String message) {
                if ("NEED_LOGIN".equals(message)) {
                    saveMessage("Cần đăng nhập Web App một lần trong app");
                    notifyFinal("Chưa lấy được báo cáo", "Mở app và bấm ĐĂNG NHẬP WEB APP.");
                    ReportScheduler.scheduleNext0730(AutoReportService.this);
                } else if (!manual && attempt < 3) {
                    int nextAttempt = attempt + 1;
                    saveMessage("Lỗi lần " + (attempt + 1) + ": " + message + ". Sẽ thử lại sau 15 phút.");
                    notifyFinal("Sẽ thử lại báo cáo", "Lỗi: " + message + " • thử lại sau 15 phút");
                    ReportScheduler.scheduleRetry(AutoReportService.this, nextAttempt);
                } else {
                    saveMessage("Không lấy được báo cáo: " + message);
                    notifyFinal("Không lấy được báo cáo", message);
                    ReportScheduler.scheduleNext0730(AutoReportService.this);
                }
                stopSelf();
            }
        });
        runner.start();
        return START_NOT_STICKY;
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Báo cáo KPI tự động", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("Thông báo lấy báo cáo KPI lúc 07:30");
                nm.createNotificationChannel(ch);
            }
        }
    }

    private Notification notification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        b.setContentTitle("Tự động gửi BC")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(pi)
                .setAutoCancel(false)
                .setOngoing(true);
        return b.build();
    }

    private void notifyFinal(String title, String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        b.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setOngoing(false);
        nm.notify(NOTI_ID + 1, b.build());
    }

    private void saveMessage(String msg) {
        getSharedPreferences("report_state", MODE_PRIVATE).edit().putString("last_message", msg).apply();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
