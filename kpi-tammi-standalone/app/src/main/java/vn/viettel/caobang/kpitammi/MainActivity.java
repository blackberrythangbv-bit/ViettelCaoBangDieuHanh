package vn.viettel.caobang.kpitammi;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private TextView status;
    private TextView scheduleInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ReportScheduler.scheduleNext0730(this);
        requestNotificationPermissionIfNeeded();
        buildUi();
        refreshStatus();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(22));
        root.setBackgroundColor(Color.rgb(247, 249, 251));

        TextView title = new TextView(this);
        title.setText("TỰ ĐỘNG LẤY BÁO CÁO KPI");
        title.setTextSize(23);
        title.setTextColor(Color.rgb(226, 13, 23));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("VIETTEL CAO BẰNG • KPI / TAMMI");
        sub.setTextSize(14);
        sub.setGravity(Gravity.CENTER);
        sub.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(6), 0, dp(24));
        root.addView(sub, subLp);

        status = cardText("Trạng thái: chưa chạy");
        root.addView(status, cardLp());

        scheduleInfo = cardText("Lịch tự động: 07:30 hằng ngày");
        LinearLayout.LayoutParams infoLp = cardLp();
        infoLp.setMargins(0, dp(10), 0, dp(18));
        root.addView(scheduleInfo, infoLp);

        Button fetch = actionButton("LẤY BÁO CÁO NGAY", Color.rgb(226, 13, 23));
        fetch.setOnClickListener(v -> {
            Toast.makeText(this, "Đang lấy báo cáo ở chế độ ẩn...", Toast.LENGTH_SHORT).show();
            AutoReportService.startNow(this, 0, true);
            status.setText("Trạng thái: đang lấy báo cáo...");
        });
        root.addView(fetch, buttonLp());

        Button login = actionButton("ĐĂNG NHẬP WEB APP", Color.rgb(80, 80, 80));
        login.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
        root.addView(login, buttonLp());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Button exact = actionButton("CẤP QUYỀN CHẠY 07:30 CHÍNH XÁC", Color.rgb(130, 10, 18));
            exact.setOnClickListener(v -> requestExactAlarm());
            root.addView(exact, buttonLp());
        }

        Button openFolder = actionButton("MỞ THƯ MỤC BÁO CÁO", Color.rgb(90, 90, 90));
        openFolder.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse("content://com.android.externalstorage.documents/root/primary"));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e) {
                Toast.makeText(this, "Báo cáo lưu trong Download/ViettelCaoBang/BaoCaoNgay", Toast.LENGTH_LONG).show();
            }
        });
        root.addView(openFolder, buttonLp());

        TextView note = new TextView(this);
        note.setText("Báo cáo được lấy trực tiếp từ Web App gốc. App chạy nền lúc 07:30; nếu lỗi mạng sẽ thử lại lúc 07:45, 08:00 và 08:15. Cần đăng nhập Web App một lần trong app này để lưu phiên đăng nhập.");
        note.setTextSize(13);
        note.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.setMargins(0, dp(18), 0, 0);
        root.addView(note, noteLp);

        setContentView(root);
    }

    private TextView cardText(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTextColor(Color.rgb(35,35,35));
        t.setPadding(dp(14), dp(14), dp(14), dp(14));
        t.setBackgroundColor(Color.WHITE);
        return t;
    }

    private LinearLayout.LayoutParams cardLp() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private Button actionButton(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackgroundColor(color);
        return b;
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.setMargins(0, dp(10), 0, 0);
        return lp;
    }

    private void refreshStatus() {
        String last = getSharedPreferences("report_state", MODE_PRIVATE).getString("last_message", "Chưa có lần lấy báo cáo thành công");
        long next = getSharedPreferences("report_state", MODE_PRIVATE).getLong("next_0730", 0L);
        status.setText("Trạng thái: " + last);
        if (next > 0) {
            String s = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(next));
            scheduleInfo.setText("Lần tự động kế tiếp: " + s + " (giờ Việt Nam)");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null) refreshStatus();
    }

    private void requestExactAlarm() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (am != null && am.canScheduleExactAlarms()) {
            Toast.makeText(this, "Đã có quyền chạy lịch chính xác.", Toast.LENGTH_SHORT).show();
            ReportScheduler.scheduleNext0730(this);
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Hãy bật Báo thức & lời nhắc cho app trong Cài đặt.", Toast.LENGTH_LONG).show();
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2201);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
