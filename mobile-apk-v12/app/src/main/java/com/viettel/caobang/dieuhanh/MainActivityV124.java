package com.viettel.caobang.dieuhanh;

import android.app.AlarmManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivityV124 extends MainActivity {
    private Button reportButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addReportButton();
        ensureExactAlarmAccessOnce();
        ReportScheduler.scheduleNext(this);
    }

    private void addReportButton() {
        reportButton = new Button(this);
        reportButton.setText("LẤY BÁO CÁO");
        reportButton.setTextColor(Color.WHITE);
        reportButton.setTextSize(12f);
        reportButton.setAllCaps(false);
        reportButton.setElevation(dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(226, 13, 23));
        bg.setCornerRadius(dp(22));
        reportButton.setBackground(bg);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(142), dp(46));
        lp.gravity = Gravity.END | Gravity.BOTTOM;
        lp.setMargins(dp(12), dp(12), dp(16), dp(20));
        addContentView(reportButton, lp);

        reportButton.setOnClickListener(v -> fetchReportNow());
    }

    private void ensureExactAlarmAccessOnce() {
        if (Build.VERSION.SDK_INT < 31) return;
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (am == null || am.canScheduleExactAlarms()) return;

        boolean asked = getSharedPreferences("vt_report_auto", MODE_PRIVATE)
                .getBoolean("asked_exact_alarm", false);
        if (asked) return;
        getSharedPreferences("vt_report_auto", MODE_PRIVATE)
                .edit().putBoolean("asked_exact_alarm", true).apply();

        try {
            Toast.makeText(this,
                    "Bật quyền Báo thức chính xác để tự lấy báo cáo đúng 07:30.",
                    Toast.LENGTH_LONG).show();
            Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception ignored) {
            // Nếu thiết bị không có màn hình quyền riêng, scheduler sẽ tự dùng chế độ gần 07:30.
        }
    }

    private void fetchReportNow() {
        if (reportButton == null) return;
        reportButton.setEnabled(false);
        reportButton.setText("ĐANG LẤY...");
        Toast.makeText(this, "Đang lấy báo cáo trực tiếp, anh có thể tiếp tục dùng app.", Toast.LENGTH_SHORT).show();

        ReportFetchController.fetch(this, new ReportFetchController.Callback() {
            @Override
            public void onSuccess(String fileName) {
                runOnUiThread(() -> {
                    reportButton.setEnabled(true);
                    reportButton.setText("LẤY BÁO CÁO");
                    Toast.makeText(MainActivityV124.this,
                            "Đã lưu Download/ViettelCaoBang/BaoCaoNgay/" + fileName,
                            Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    reportButton.setEnabled(true);
                    reportButton.setText("LẤY BÁO CÁO");
                    Toast.makeText(MainActivityV124.this,
                            "Chưa lấy được báo cáo: " + message,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
