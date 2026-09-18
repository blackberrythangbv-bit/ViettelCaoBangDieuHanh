package com.viettel.caobang.dieuhanh;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivityV124 extends MainActivity {
    private Button reportButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ReportScheduler.scheduleNext(this);
        addReportButton();
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
