package vn.viettel.caobang.kpitammi;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private EditText edtUrl;
    private EditText edtHour;
    private EditText edtMinute;
    private TextView status;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(AppConfig.PREFS, MODE_PRIVATE);
        ensureDefaults();
        requestNotificationPermissionIfNeeded();
        buildUi();
        ReportScheduler.scheduleConfigured(this);
        refreshStatus();
    }

    private void ensureDefaults() {
        SharedPreferences.Editor e = prefs.edit();
        if (!prefs.contains(AppConfig.KEY_SOURCE_URL)) e.putString(AppConfig.KEY_SOURCE_URL, AppConfig.DEFAULT_SOURCE_URL);
        if (!prefs.contains(AppConfig.KEY_HOUR)) e.putInt(AppConfig.KEY_HOUR, AppConfig.DEFAULT_HOUR);
        if (!prefs.contains(AppConfig.KEY_MINUTE)) e.putInt(AppConfig.KEY_MINUTE, AppConfig.DEFAULT_MINUTE);
        if (!prefs.contains(AppConfig.KEY_AUTO_ENABLED)) e.putBoolean(AppConfig.KEY_AUTO_ENABLED, true);
        e.apply();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(22), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(250, 250, 250));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("KPI → TAMMI");
        title.setTextSize(29);
        title.setTextColor(Color.rgb(80, 80, 80));
        title.setTypeface(null, 1);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("Tự tải ZIP • Giải nén • 1 chạm gửi Tammi");
        sub.setTextSize(18);
        sub.setTextColor(Color.rgb(95, 95, 95));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(2), 0, dp(26));
        root.addView(sub, subLp);

        edtUrl = new EditText(this);
        edtUrl.setHint("URL tải file ZIP báo cáo");
        edtUrl.setSingleLine(true);
        edtUrl.setTextSize(18);
        edtUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        edtUrl.setText(prefs.getString(AppConfig.KEY_SOURCE_URL, AppConfig.DEFAULT_SOURCE_URL));
        root.addView(edtUrl, new LinearLayout.LayoutParams(-1, dp(58)));

        edtHour = new EditText(this);
        edtHour.setSingleLine(true);
        edtHour.setTextSize(18);
        edtHour.setInputType(InputType.TYPE_CLASS_NUMBER);
        edtHour.setText(String.valueOf(prefs.getInt(AppConfig.KEY_HOUR, AppConfig.DEFAULT_HOUR)));
        root.addView(edtHour, new LinearLayout.LayoutParams(-1, dp(52)));

        edtMinute = new EditText(this);
        edtMinute.setSingleLine(true);
        edtMinute.setTextSize(18);
        edtMinute.setInputType(InputType.TYPE_CLASS_NUMBER);
        edtMinute.setText(String.valueOf(prefs.getInt(AppConfig.KEY_MINUTE, AppConfig.DEFAULT_MINUTE)));
        LinearLayout.LayoutParams minLp = new LinearLayout.LayoutParams(-1, dp(52));
        minLp.setMargins(0, 0, 0, dp(10));
        root.addView(edtMinute, minLp);

        Button save = oldButton("LƯU & BẬT TỰ ĐỘNG");
        save.setOnClickListener(v -> {
            if (!saveConfig(true)) return;
            ReportScheduler.scheduleConfigured(this);
            refreshStatus();
            Toast.makeText(this, "Đã lưu. Tự động lấy báo cáo hằng ngày theo giờ đã đặt.", Toast.LENGTH_LONG).show();
        });
        root.addView(save, buttonLp());

        Button run = oldButton("CHẠY THỬ NGAY");
        run.setOnClickListener(v -> {
            if (!saveConfig(false)) return;
            prefs.edit().putString("last_message", "Đang tải báo cáo...").apply();
            refreshStatus();
            AutoReportService.startNow(this, 0, true);
            Toast.makeText(this, "Đang tải báo cáo từ nguồn đã cấu hình.", Toast.LENGTH_SHORT).show();
        });
        root.addView(run, buttonLp());

        Button send = oldButton("GỬI TAMMI");
        send.setOnClickListener(v -> {
            try {
                ReportStore.shareLatest(this);
            } catch (Exception ex) {
                Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        root.addView(send, buttonLp());

        status = new TextView(this);
        status.setTextSize(16);
        status.setTextColor(Color.rgb(95, 95, 95));
        status.setGravity(Gravity.START);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(-1, -2);
        stLp.setMargins(0, dp(22), 0, 0);
        root.addView(status, stLp);

        setContentView(scroll);
    }

    private Button oldButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(16);
        b.setTextColor(Color.rgb(20, 20, 20));
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setBackgroundColor(Color.rgb(220, 222, 222));
        return b;
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58));
        lp.setMargins(0, dp(8), 0, 0);
        return lp;
    }

    private boolean saveConfig(boolean enableAuto) {
        String url = edtUrl.getText().toString().trim();
        if (url.isEmpty()) {
            edtUrl.setText(AppConfig.DEFAULT_SOURCE_URL);
            url = AppConfig.DEFAULT_SOURCE_URL;
        }
        int h;
        int m;
        try {
            h = Integer.parseInt(edtHour.getText().toString().trim());
            m = Integer.parseInt(edtMinute.getText().toString().trim());
        } catch (Exception e) {
            Toast.makeText(this, "Giờ/phút không hợp lệ.", Toast.LENGTH_LONG).show();
            return false;
        }
        if (h < 0 || h > 23 || m < 0 || m > 59) {
            Toast.makeText(this, "Giờ phải 0-23, phút phải 0-59.", Toast.LENGTH_LONG).show();
            return false;
        }
        SharedPreferences.Editor ed = prefs.edit()
                .putString(AppConfig.KEY_SOURCE_URL, url)
                .putInt(AppConfig.KEY_HOUR, h)
                .putInt(AppConfig.KEY_MINUTE, m);
        if (enableAuto) ed.putBoolean(AppConfig.KEY_AUTO_ENABLED, true);
        ed.apply();
        return true;
    }

    private void refreshStatus() {
        if (status == null) return;
        String msg = prefs.getString("last_message", "Sẵn sàng. URL báo cáo đã được cấu hình sẵn.");
        int h = prefs.getInt(AppConfig.KEY_HOUR, AppConfig.DEFAULT_HOUR);
        int m = prefs.getInt(AppConfig.KEY_MINUTE, AppConfig.DEFAULT_MINUTE);
        boolean on = prefs.getBoolean(AppConfig.KEY_AUTO_ENABLED, true);
        status.setText(msg + "\nTự động: " + (on ? String.format("%02d:%02d hằng ngày", h, m) : "đang tắt"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs != null) refreshStatus();
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
