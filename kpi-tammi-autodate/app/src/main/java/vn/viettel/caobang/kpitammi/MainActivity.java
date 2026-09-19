package vn.viettel.caobang.kpitammi;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
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

    private TextView statusTitle;
    private TextView statusDetail;
    private TextView scheduleTitle;
    private TextView scheduleSubtitle;
    private View downloadCard;
    private View shareCard;

    private static final DateTimeFormatter VI_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("vi", "VN"));

    private static final int NAVY = Color.rgb(12, 38, 74);
    private static final int BLUE = Color.rgb(31, 111, 235);
    private static final int GREEN = Color.rgb(18, 158, 94);
    private static final int PURPLE = Color.rgb(111, 71, 193);
    private static final int ORANGE = Color.rgb(224, 132, 24);
    private static final int TEXT = Color.rgb(24, 39, 58);
    private static final int MUTED = Color.rgb(102, 116, 139);
    private static final int BG = Color.rgb(247, 249, 252);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        setContentView(buildUi());
        requestNotificationPermissionIfNeeded();
        AlarmReceiver.ensureNotificationChannel(this);
        refreshLatestState();
        updateScheduleUi();

        if (getIntent() != null && getIntent().getBooleanExtra("shareNow", false)) {
            main.postDelayed(this::shareLatest, 400);
        }
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, matchWrap());

        TextView logo = new TextView(this);
        logo.setText("↗");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(32);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setBackground(roundGradient(0xFF28C7D9, 0xFF2563EB, 18));
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(66), dp(66));
        header.addView(logo, logoLp);

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(14), 0, 0, 0);
        header.addView(heading, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = text("KPI → Tammi", 29, NAVY, true);
        heading.addView(title, matchWrap());

        TextView version = text("AutoDate V1.4.0", 15, MUTED, false);
        version.setPadding(0, dp(2), 0, 0);
        heading.addView(version, matchWrap());

        TextView date = text("▣  Báo cáo ngày " + LocalDate.now(ReportClient.VN_ZONE).format(VI_DATE),
                15, BLUE, true);
        date.setPadding(0, dp(9), 0, 0);
        root.addView(date, matchWrap());

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        statusCard.setBackground(cardBg(0xFFF0FBF5, 0xFFB7EACD, 18));
        LinearLayout.LayoutParams statusLp = matchWrap();
        statusLp.topMargin = dp(18);
        root.addView(statusCard, statusLp);

        TextView statusIcon = circleIcon("✓", 0xFF22C55E, Color.WHITE);
        statusCard.addView(statusIcon, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout statusText = new LinearLayout(this);
        statusText.setOrientation(LinearLayout.VERTICAL);
        statusText.setPadding(dp(14), 0, 0, 0);
        statusCard.addView(statusText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        statusTitle = text("Đang kiểm tra dữ liệu trên máy…", 17, TEXT, true);
        statusText.addView(statusTitle, matchWrap());

        statusDetail = text("Báo cáo cũ sẽ không được gửi nhầm.", 13, MUTED, false);
        statusDetail.setPadding(0, dp(4), 0, 0);
        statusText.addView(statusDetail, matchWrap());

        TextView section = text("THAO TÁC NHANH", 12, MUTED, true);
        section.setLetterSpacing(0.08f);
        section.setPadding(dp(2), dp(20), 0, dp(2));
        root.addView(section, matchWrap());

        View check = actionCard("⌕", 0xFFEAF2FF, BLUE,
                "KIỂM TRA NGUỒN BÁO CÁO",
                "Kiểm tra và xác minh nguồn KPI→Tammi",
                this::checkSource);
        root.addView(check, actionLp());

        downloadCard = actionCard("↓", 0xFFE9FBF4, GREEN,
                "TẢI BÁO CÁO HÔM NAY",
                "Tải file báo cáo đúng ngày hiện tại",
                this::downloadNow);
        root.addView(downloadCard, actionLp());

        shareCard = actionCard("●", 0xFFF2ECFF, PURPLE,
                "CHIA SẺ BÁO CÁO QUA TAMMI",
                "Chia sẻ toàn bộ file đã hậu kiểm",
                this::shareLatest);
        root.addView(shareCard, actionLp());

        LinearLayout scheduleCard = (LinearLayout) actionCard("◷", 0xFFFFF2DF, ORANGE,
                "", "", this::pickScheduleTime);
        scheduleTitle = (TextView) scheduleCard.findViewWithTag("title");
        scheduleSubtitle = (TextView) scheduleCard.findViewWithTag("subtitle");
        root.addView(scheduleCard, actionLp());

        LinearLayout safe = new LinearLayout(this);
        safe.setOrientation(LinearLayout.VERTICAL);
        safe.setPadding(dp(16), dp(16), dp(16), dp(16));
        safe.setBackground(cardBg(Color.WHITE, 0xFFE4EAF2, 18));
        LinearLayout.LayoutParams safeLp = matchWrap();
        safeLp.topMargin = dp(18);
        root.addView(safe, safeLp);

        LinearLayout safeHead = new LinearLayout(this);
        safeHead.setOrientation(LinearLayout.HORIZONTAL);
        safeHead.setGravity(Gravity.CENTER_VERTICAL);
        safe.addView(safeHead, matchWrap());

        TextView shield = circleIcon("✓", 0xFFE9F3FF, BLUE);
        safeHead.addView(shield, new LinearLayout.LayoutParams(dp(38), dp(38)));

        TextView safeTitle = text("Cơ chế an toàn", 19, NAVY, true);
        safeTitle.setPadding(dp(10), 0, 0, 0);
        safeHead.addView(safeTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView trust = text("An toàn • Ổn định", 12, 0xFF6D83A3, false);
        safeHead.addView(trust, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        addSafetyRow(safe, "App chỉ nhận ZIP có ngày hiện tại.");
        addSafetyRow(safe, "Không đổi tên báo cáo cũ thành ngày mới.");
        addSafetyRow(safe, "Nguồn chưa chạy thì app dừng gửi và tự thử lại.");
        addSafetyRow(safe, "URL KPI→Tammi được khóa cố định trong ứng dụng.");

        TextView footer = text("KPI → Tammi  •  Dữ liệu đúng, gửi đúng lúc", 12, 0xFF8FA0B5, false);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        footer.setPadding(0, dp(18), 0, 0);
        root.addView(footer, matchWrap());

        return scroll;
    }

    private void pickScheduleTime() {
        int hour = AlarmReceiver.getScheduleHour(this);
        int minute = AlarmReceiver.getScheduleMinute(this);

        TimePickerDialog dialog = new TimePickerDialog(this, (view, h, m) -> {
            AlarmReceiver.saveScheduleTime(this, h, m);
            AlarmReceiver.scheduleDaily(this);
            updateScheduleUi();
            String time = AlarmReceiver.formatScheduleTime(this);
            statusTitle.setText("Đã cập nhật giờ chạy " + time);
            statusDetail.setText("App sẽ tự tải báo cáo mỗi ngày vào " + time + " và tự retry nếu nguồn chưa sẵn sàng.");
            Toast.makeText(this, "Đã đặt lịch tự động " + time, Toast.LENGTH_LONG).show();
        }, hour, minute, true);
        dialog.setTitle("Chọn giờ tự động chạy");
        dialog.show();
    }

    private void updateScheduleUi() {
        String time = AlarmReceiver.formatScheduleTime(this);
        if (scheduleTitle != null) scheduleTitle.setText("LỊCH TỰ ĐỘNG HÀNG NGÀY " + time);
        if (scheduleSubtitle != null) scheduleSubtitle.setText("Chạm để thay đổi giờ chạy • hiện tại " + time);
    }

    private void checkSource() {
        setBusy(true, "Đang kiểm tra nguồn KPI→Tammi…");
        io.execute(() -> {
            try {
                ReportClient.Info info = ReportClient.checkSource();
                main.post(() -> {
                    setBusy(false, "Nguồn đã sẵn sàng");
                    statusDetail.setText("Ngày nguồn: " + info.reportDate.format(VI_DATE)
                            + " • ZIP " + humanBytes(info.size) + " • " + info.totalChunks + " khối");
                });
            } catch (ReportClient.StaleReportException e) {
                main.post(() -> {
                    setBusy(false, "Nguồn chưa được phép gửi");
                    statusDetail.setText(e.getMessage() + " • App đã chặn báo cáo cũ.");
                });
            } catch (Exception e) {
                main.post(() -> {
                    setBusy(false, "Lỗi kiểm tra nguồn");
                    statusDetail.setText(safeMessage(e));
                });
            }
        });
    }

    private void downloadNow() {
        setBusy(true, "Đang tải và hậu kiểm báo cáo hôm nay…");
        io.execute(() -> {
            try {
                List<File> files = ReportClient.downloadToday(this);
                main.post(() -> {
                    setBusy(false, "Đã tải đúng ngày • " + files.size() + " file");
                    statusDetail.setText("Bộ báo cáo đã hậu kiểm. Có thể chia sẻ qua Tammi.");
                    shareCard.setEnabled(true);
                    shareCard.setAlpha(1f);
                });
            } catch (ReportClient.StaleReportException e) {
                AlarmReceiver.scheduleRetry(this, 15);
                main.post(() -> {
                    setBusy(false, "Nguồn chưa cập nhật");
                    statusDetail.setText("Đã chặn báo cáo cũ và tự kiểm tra lại sau 15 phút.");
                });
            } catch (Exception e) {
                main.post(() -> {
                    setBusy(false, "Tải báo cáo thất bại");
                    statusDetail.setText(safeMessage(e));
                });
            }
        });
    }

    private void shareLatest() {
        List<File> files = ReportClient.latestFiles(this);
        if (files.isEmpty()) {
            Toast.makeText(this, "Chưa có báo cáo đúng ngày để chia sẻ.", Toast.LENGTH_LONG).show();
            return;
        }

        String todayTag = LocalDate.now(ReportClient.VN_ZONE)
                .format(DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US));
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
        send.putExtra(Intent.EXTRA_SUBJECT,
                "Báo cáo KPI ngày " + LocalDate.now(ReportClient.VN_ZONE).format(VI_DATE));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "Gửi báo cáo qua Tammi"));
    }

    private void refreshLatestState() {
        List<File> files = ReportClient.latestFiles(this);
        if (files.isEmpty()) {
            statusTitle.setText("Chưa có báo cáo hôm nay");
            statusDetail.setText("Bấm Kiểm tra nguồn trước khi tải.");
            shareCard.setEnabled(false);
            shareCard.setAlpha(0.5f);
        } else {
            statusTitle.setText("Đã có " + files.size() + " file báo cáo trên máy");
            statusDetail.setText("Hãy kiểm tra nguồn nếu cần tải bản mới nhất.");
            shareCard.setEnabled(true);
            shareCard.setAlpha(1f);
        }
    }

    private void setBusy(boolean busy, String title) {
        statusTitle.setText(title);
        downloadCard.setEnabled(!busy);
        downloadCard.setAlpha(busy ? 0.55f : 1f);
    }

    private View actionCard(String icon, int iconBg, int accent, String title, String subtitle, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(12), dp(14));
        card.setBackground(cardBg(Color.WHITE, 0xFFE3EAF3, 18));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> action.run());

        TextView ic = circleIcon(icon, iconBg, accent);
        card.addView(ic, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(14), 0, dp(8), 0);
        card.addView(textCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = text(title, 16, NAVY, true);
        t.setTag("title");
        textCol.addView(t, matchWrap());

        TextView s = text(subtitle, 13, MUTED, false);
        s.setTag("subtitle");
        s.setPadding(0, dp(4), 0, 0);
        textCol.addView(s, matchWrap());

        TextView arrow = text("›", 31, 0xFF6C7D95, false);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(46)));

        return card;
    }

    private void addSafetyRow(LinearLayout parent, String line) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(10), 0, 0);

        TextView check = circleIcon("✓", 0xFFE8F8EF, GREEN);
        row.addView(check, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView t = text(line, 13, 0xFF46566B, false);
        t.setPadding(dp(9), dp(1), 0, 0);
        row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(row, matchWrap());
    }

    private TextView text(String value, float sizeSp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        t.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return t;
    }

    private TextView circleIcon(String value, int bg, int fg) {
        TextView t = text(value, 24, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(cardBg(bg, bg, 100));
        return t;
    }

    private GradientDrawable cardBg(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), stroke);
        return g;
    }

    private GradientDrawable roundGradient(int start, int end, int radiusDp) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private LinearLayout.LayoutParams actionLp() {
        LinearLayout.LayoutParams p = matchWrap();
        p.topMargin = dp(11);
        return p;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
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

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 130);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }
}
