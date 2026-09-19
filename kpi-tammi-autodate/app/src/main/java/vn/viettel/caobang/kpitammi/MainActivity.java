package vn.viettel.caobang.kpitammi;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status;
    private Button downloadButton;
    private Button shareButton;
    private Button scheduleButton;
    private static final DateTimeFormatter VI_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("vi", "VN"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        requestNotificationPermissionIfNeeded();
        AlarmReceiver.ensureNotificationChannel(this);
        refreshLatestState();

        if (getIntent() != null && getIntent().getBooleanExtra("shareNow", false)) {
            main.postDelayed(this::shareLatest, 400);
        }
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("KPI → Tammi");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("AutoDate V1.3.0 • Báo cáo ngày " + LocalDate.now(ReportClient.VN_ZONE).format(VI_DATE));
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitle.setPadding(0, dp(4), 0, dp(20));
        root.addView(subtitle, matchWrap());

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(dp(14), dp(14), dp(14), dp(14));
        status.setBackgroundColor(0xFFF2F2F2);
        root.addView(status, matchWrap());

        Button check = button("1. KIỂM TRA NGUỒN BÁO CÁO");
        check.setOnClickListener(v -> checkSource());
        root.addView(check, buttonParams());

        downloadButton = button("2. TẢI BÁO CÁO HÔM NAY");
        downloadButton.setOnClickListener(v -> downloadNow());
        root.addView(downloadButton, buttonParams());

        shareButton = button("3. CHIA SẺ BÁO CÁO QUA TAMMI");
        shareButton.setOnClickListener(v -> shareLatest());
        root.addView(shareButton, buttonParams());

        scheduleButton = button("4. BẬT TỰ ĐỘNG HÀNG NGÀY 07:20");
        scheduleButton.setOnClickListener(v -> {
            AlarmReceiver.scheduleDaily(this);
            status.setText("Đã bật lịch tự động 07:20 hằng ngày. Nếu nguồn chưa cập nhật, app tự thử lại mỗi 15 phút đến 09:00.");
            Toast.makeText(this, "Đã bật lịch tự động 07:20", Toast.LENGTH_LONG).show();
        });
        root.addView(scheduleButton, buttonParams());

        TextView note = new TextView(this);
        note.setText("Cơ chế an toàn:\n• App chỉ nhận ZIP có ngày hiện tại.\n• Không đổi tên báo cáo cũ thành ngày mới.\n• Nếu nguồn chưa chạy, app dừng gửi và tự thử lại.\n• URL KPI→Tammi đã khóa cố định trong ứng dụng.");
        note.setTextSize(14);
        note.setPadding(0, dp(18), 0, 0);
        root.addView(note, matchWrap());
        return scroll;
    }

    private void checkSource() {
        setBusy(true, "Đang kiểm tra nguồn KPI→Tammi...");
        io.execute(() -> {
            try {
                ReportClient.Info info = ReportClient.checkSource();
                main.post(() -> {
                    setBusy(false, "NGUỒN ĐÃ SẴN SÀNG\nNgày nguồn: " + info.reportDate.format(VI_DATE)
                            + "\nDung lượng ZIP: " + humanBytes(info.size)
                            + "\nSố khối tải: " + info.totalChunks);
                });
            } catch (ReportClient.StaleReportException e) {
                main.post(() -> setBusy(false, "CHƯA ĐƯỢC PHÉP GỬI\n" + e.getMessage()
                        + "\nApp đã chặn báo cáo cũ và sẽ không gửi nhầm qua Tammi."));
            } catch (Exception e) {
                main.post(() -> setBusy(false, "Lỗi kiểm tra nguồn: " + safeMessage(e)));
            }
        });
    }

    private void downloadNow() {
        setBusy(true, "Đang tải và hậu kiểm báo cáo ngày hôm nay...");
        io.execute(() -> {
            try {
                List<File> files = ReportClient.downloadToday(this);
                main.post(() -> {
                    setBusy(false, "ĐÃ TẢI ĐÚNG NGÀY\n" + files.size() + " file hợp lệ. Có thể bấm Chia sẻ qua Tammi.");
                    shareButton.setEnabled(true);
                });
            } catch (ReportClient.StaleReportException e) {
                AlarmReceiver.scheduleRetry(this, 15);
                main.post(() -> setBusy(false, "NGUỒN CHƯA CẬP NHẬT\n" + e.getMessage()
                        + "\nĐã lập retry sau 15 phút; không gửi báo cáo cũ."));
            } catch (Exception e) {
                main.post(() -> setBusy(false, "Tải báo cáo thất bại: " + safeMessage(e)));
            }
        });
    }

    private void shareLatest() {
        List<File> files = ReportClient.latestFiles(this);
        if (files.isEmpty()) {
            Toast.makeText(this, "Chưa có báo cáo đúng ngày để chia sẻ.", Toast.LENGTH_LONG).show();
            return;
        }

        String todayTag = LocalDate.now(ReportClient.VN_ZONE).format(DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US));
        for (File f : files) {
            if (!f.getName().contains(todayTag)) {
                Toast.makeText(this, "Bộ file không đúng ngày hôm nay. Hãy tải lại.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        ArrayList<Uri> uris = new ArrayList<>();
        for (File f : files) {
            uris.add(FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f));
        }

        Intent send = new Intent(Intent.ACTION_SEND_MULTIPLE);
        send.setType("*/*");
        send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        send.putExtra(Intent.EXTRA_SUBJECT, "Báo cáo KPI ngày " + LocalDate.now(ReportClient.VN_ZONE).format(VI_DATE));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "Gửi báo cáo qua Tammi"));
    }

    private void refreshLatestState() {
        List<File> files = ReportClient.latestFiles(this);
        if (files.isEmpty()) {
            status.setText("Chưa có báo cáo hôm nay trên máy. Bấm Kiểm tra nguồn trước khi tải.");
            shareButton.setEnabled(false);
        } else {
            status.setText("Đã có " + files.size() + " file báo cáo trên máy. Hãy kiểm tra nguồn nếu cần tải bản mới nhất.");
            shareButton.setEnabled(true);
        }
    }

    private void setBusy(boolean busy, String text) {
        status.setText(text);
        downloadButton.setEnabled(!busy);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 130);
        }
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setPadding(dp(8), dp(12), dp(8), dp(12));
        return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(12);
        return p;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int n) {
        return (int)(n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String humanBytes(long value) {
        if (value < 1024) return value + " B";
        double kb = value / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        return String.format(Locale.US, "%.2f MB", kb / 1024.0);
    }

    private static String safeMessage(Throwable e) {
        String s = e.getMessage();
        return s == null || s.trim().isEmpty() ? e.getClass().getSimpleName() : s;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }
}
