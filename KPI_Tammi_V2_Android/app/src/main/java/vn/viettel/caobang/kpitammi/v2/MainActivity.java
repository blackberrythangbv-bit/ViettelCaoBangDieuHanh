package vn.viettel.caobang.kpitammi.v2;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private TextView statusTitle, statusSub, dateText, scheduleSub;
    private File latestZip;
    private File pendingSaveZip;
    private boolean busy;
    private static final int SAVE_ZIP = 204;

    private int blue = Color.rgb(30, 105, 200);
    private int navy = Color.rgb(18, 47, 78);
    private int muted = Color.rgb(112, 126, 147);

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Scheduler.schedule(getApplicationContext());
        buildUi();
        restoreLocal();
        if (b != null) { String path = b.getString("pending_save_zip"); if (path != null) pendingSaveZip = new File(path); }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        exec.shutdownNow();
    }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Color.rgb(246,248,252));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(24), dp(22), dp(36));
        sv.addView(root);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = text("↗", 34, Color.WHITE, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(Color.rgb(42,143,229), 22));
        head.addView(icon, new LinearLayout.LayoutParams(dp(78), dp(78)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(16),0,0,0);
        TextView title = text("KPI → Tammi", 34, navy, Typeface.BOLD);
        TextView ver = text("KPI DNS V2 · APK 2.0.4", 20, muted, Typeface.NORMAL);
        titles.addView(title); titles.addView(ver);
        head.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1));
        root.addView(head);

        dateText = text("▣  Báo cáo ngày " + today(), 22, blue, Typeface.BOLD);
        dateText.setPadding(0, dp(16),0,dp(14));
        root.addView(dateText);

        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(18),dp(18),dp(18),dp(18));
        status.setBackground(strokeRound(Color.rgb(240,252,245), Color.rgb(174,228,195), 20));

        TextView check = text("✓", 38, Color.WHITE, Typeface.BOLD);
        check.setGravity(Gravity.CENTER);
        check.setBackground(round(Color.rgb(35,196,105), 100));
        status.addView(check, new LinearLayout.LayoutParams(dp(64),dp(64)));

        LinearLayout statTxt = new LinearLayout(this);
        statTxt.setOrientation(LinearLayout.VERTICAL);
        statTxt.setPadding(dp(16),0,0,0);
        statusTitle = text("Sẵn sàng kiểm tra nguồn", 24, navy, Typeface.BOLD);
        statusSub = text("Nguồn KPI DNS V2 → bộ 8 file", 18, muted, Typeface.NORMAL);
        statTxt.addView(statusTitle); statTxt.addView(statusSub);
        status.addView(statTxt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1));
        root.addView(status, marginTop(8));

        TextView quick = text("THAO TÁC NHANH", 18, muted, Typeface.BOLD);
        quick.setPadding(dp(4),dp(26),0,dp(12));
        root.addView(quick);

        root.addView(actionCard("⌕", "KIỂM TRA NGUỒN\nBÁO CÁO", "Kiểm tra và xác minh nguồn KPI→Tammi", v -> checkSource()));
        root.addView(actionCard("↓", "TẢI BÁO CÁO HÔM\nNAY", "Tải file báo cáo đúng ngày hiện tại", v -> downloadToday()));
        root.addView(actionCard("↓", "LƯU FILE ZIP", "Chọn thư mục Downloads hoặc Drive để lưu", v -> saveZip()));
        root.addView(actionCard("●", "CHIA SẺ BÁO CÁO\nQUA TAMMI", "Chia sẻ toàn bộ 8 file đã hậu kiểm", v -> shareReports()));

        scheduleSub = text("", 17, muted, Typeface.NORMAL);
        LinearLayout schedule = actionCard("◷", "LỊCH TỰ ĐỘNG HÀNG\nNGÀY " + hhmm(), "Chạm để thay đổi giờ chạy", v -> chooseTime());
        scheduleSub.setText("Hiện tại " + hhmm());
        root.addView(schedule);

        LinearLayout safe = new LinearLayout(this);
        safe.setOrientation(LinearLayout.VERTICAL);
        safe.setPadding(dp(18),dp(18),dp(18),dp(18));
        safe.setBackground(strokeRound(Color.WHITE, Color.rgb(223,229,238), 18));
        safe.addView(text("CƠ CHẾ AN TOÀN", 19, navy, Typeface.BOLD));
        safe.addView(text("• Chỉ nhận đúng nguồn KPI DNS V2\n• Kiểm tra đúng ngày, đủ tên 6 AM và định dạng file\n• Không ghi nhận hoàn tất nếu ZIP lỗi/thiếu file\n• Tải nền theo lịch đã chọn", 16, muted, Typeface.NORMAL));
        root.addView(safe, marginTop(18));

        setContentView(sv);
    }

    private void restoreLocal() {
        String p = Scheduler.prefs(this).getString("last_file", "");
        if (!p.isEmpty()) {
            File f = new File(p);
            if (f.exists()) {
                latestZip = f;
                exec.execute(() -> {
                    try {
                        int n = ApiClient.countZipEntries(f);
                        runOnUiThread(() -> { if (!busy) setOk("Đã có báo cáo cục bộ · " + n + " file", "Đúng ngày hiện tại. Khi chia sẻ sẽ tải lại bản mới nhất."); });
                    } catch (Exception e) { runOnUiThread(() -> { if (!busy) setError("Báo cáo cục bộ chưa hợp lệ", "Bấm tải báo cáo hôm nay để lấy bản mới."); }); }
                });
            }
        }
    }

    private LinearLayout actionCard(String sym, String title, String sub, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18),dp(20),dp(14),dp(20));
        row.setBackground(strokeRound(Color.WHITE, Color.rgb(224,229,237), 20));
        row.setOnClickListener(click);

        TextView ico = text(sym, 30, blue, Typeface.BOLD);
        ico.setGravity(Gravity.CENTER);
        ico.setBackground(round(Color.rgb(236,244,255),100));
        row.addView(ico,new LinearLayout.LayoutParams(dp(70),dp(70)));

        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(dp(16),0,dp(10),0);
        tx.addView(text(title,22,navy,Typeface.BOLD));
        tx.addView(text(sub,17,muted,Typeface.NORMAL));
        row.addView(tx,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));

        row.addView(text("›",34,muted,Typeface.NORMAL));
        LinearLayout.LayoutParams lp = marginTop(12);
        row.setLayoutParams(lp);
        return row;
    }

    private void checkSource() {
        if (busy) return;
        setBusy("Đang kiểm tra nguồn…");
        exec.execute(() -> {
            try {
                JSONObject p = ApiClient.ping();
                JSONObject i = ApiClient.info();
                String msg = p.optString("name","KPI_ngay_latest.zip") + " · " + i.optInt("totalChunks",0) + " khối · " + i.optInt("size",0) + " bytes";
                runOnUiThread(() -> setOk("Nguồn KPI→Tammi hoạt động", msg));
            } catch (Exception e) {
                runOnUiThread(() -> setError("Lỗi nguồn báo cáo", e.getMessage()));
            }
        });
    }

    private void downloadToday() {
        if (busy) return;
        setBusy("Đang tải và hậu kiểm 8 file…");
        exec.execute(() -> {
            try {
                File f = ApiClient.downloadLatest(this);
                latestZip = f;
                Scheduler.prefs(this).edit()
                        .putString("last_file", f.getAbsolutePath())
                        .putLong("last_manual_ok", System.currentTimeMillis())
                        .apply();
                runOnUiThread(() -> setOk("Đã tải bản mới nhất · 8 file", "Bộ báo cáo đã hậu kiểm. Có thể chia sẻ qua Tammi."));
            } catch (Exception e) {
                runOnUiThread(() -> setError("Không tải được báo cáo", e.getMessage()));
            }
        });
    }

    private void shareReports() {
        if (busy) return;
        setBusy("Đang chuẩn bị 8 file để chia sẻ…");
        exec.execute(() -> {
            try {
                File zip = ApiClient.downloadLatest(this);
                latestZip = zip;

                File dir = new File(getCacheDir(), "share_reports");
                deleteRecursive(dir);
                if (!dir.mkdirs()) throw new Exception("Không tạo được vùng chia sẻ");

                ArrayList<Uri> uris = new ArrayList<>();
                try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))) {
                    ZipEntry e;
                    byte[] buf = new byte[8192];
                    while ((e = zis.getNextEntry()) != null) {
                        if (e.isDirectory()) continue;
                        String safe = new File(e.getName()).getName();
                        File out = new File(dir, safe);
                        try (FileOutputStream fos = new FileOutputStream(out)) {
                            int n; while ((n = zis.read(buf)) > 0) fos.write(buf,0,n);
                        }
                        uris.add(FileProvider.getUriForFile(this, getPackageName()+".files", out));
                    }
                }
                if (uris.size() != 8) throw new Exception("Hậu kiểm chia sẻ: " + uris.size() + "/8 file");

                Intent s = new Intent(Intent.ACTION_SEND_MULTIPLE);
                s.setType("*/*");
                s.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                s.putExtra(Intent.EXTRA_SUBJECT, "Báo cáo KPI ngày " + today());
                runOnUiThread(() -> {
                    setOk("Đã chuẩn bị đủ 8 file", "Chọn Tammi và người/nhóm cần chia sẻ.");
                    startActivity(Intent.createChooser(s, "Chia sẻ báo cáo KPI"));
                });
            } catch (Exception e) {
                runOnUiThread(() -> setError("Không chia sẻ được", e.getMessage()));
            }
        });
    }

    private void saveZip() {
        if (busy) return;
        setBusy("Đang tải ZIP để lưu…");
        exec.execute(() -> {
            try {
                File zip = ApiClient.downloadLatest(this);
                runOnUiThread(() -> {
                    pendingSaveZip = zip;
                    Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    save.addCategory(Intent.CATEGORY_OPENABLE);
                    save.setType("application/zip");
                    save.putExtra(Intent.EXTRA_TITLE, zip.getName());
                    try { startActivityForResult(save, SAVE_ZIP); }
                    catch (Exception e) { pendingSaveZip = null; setError("Không mở được nơi lưu", e.getMessage()); }
                });
            } catch (Exception e) { runOnUiThread(() -> setError("Không tải được ZIP", e.getMessage())); }
        });
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        if (pendingSaveZip != null) out.putString("pending_save_zip", pendingSaveZip.getAbsolutePath());
        super.onSaveInstanceState(out);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != SAVE_ZIP) return;
        final File source = pendingSaveZip;
        pendingSaveZip = null;
        if (result != RESULT_OK || data == null || data.getData() == null) {
            setOk("Đã hủy lưu ZIP", "Báo cáo chưa được lưu vào thư mục đã chọn.");
            return;
        }
        final Uri destination = data.getData();
        setBusy("Đang lưu ZIP…");
        exec.execute(() -> {
            try {
                if (source == null) throw new Exception("Mất file tạm. Bấm Lưu file ZIP để tải lại.");
                ApiClient.countZipEntries(source);
                try (FileInputStream input = new FileInputStream(source);
                     java.io.OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                    if (output == null) throw new Exception("Không mở được file đích");
                    byte[] bytes = new byte[8192]; int n;
                    while ((n = input.read(bytes)) != -1) output.write(bytes, 0, n);
                    output.flush();
                }
                runOnUiThread(() -> setOk("Đã lưu ZIP · 8 file", "File: " + source.getName()));
            } catch (Exception e) { runOnUiThread(() -> setError("Không lưu được ZIP", e.getMessage())); }
        });
    }

    private void chooseTime() {
        new TimePickerDialog(this, (v,h,m) -> {
            Scheduler.saveAndSchedule(getApplicationContext(), h, m);
            Toast.makeText(this, "Đã đặt lịch " + String.format(Locale.US,"%02d:%02d",h,m), Toast.LENGTH_SHORT).show();
            recreate();
        }, Scheduler.hour(this), Scheduler.minute(this), true).show();
    }

    private void setBusy(String s) {
        busy = true;
        statusTitle.setText(s);
        statusSub.setText("Vui lòng chờ…");
    }

    private void setOk(String t, String s) {
        busy = false;
        statusTitle.setText(t); statusSub.setText(s);
    }

    private void setError(String t, String s) {
        busy = false;
        statusTitle.setText(t); statusSub.setText(s == null ? "" : s);
        Toast.makeText(this, t + ": " + s, Toast.LENGTH_LONG).show();
    }

    private String today() {
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        format.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        return format.format(new Date());
    }

    private String hhmm() {
        return String.format(Locale.US,"%02d:%02d",Scheduler.hour(this),Scheduler.minute(this));
    }

    private TextView text(String s,int sp,int color,int style) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setTypeface(Typeface.DEFAULT,style);
        t.setLineSpacing(0,1.06f);
        return t;
    }

    private GradientDrawable round(int color,int radius) {
        GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g;
    }

    private GradientDrawable strokeRound(int color,int stroke,int radius) {
        GradientDrawable g=round(color,radius); g.setStroke(dp(1),stroke); return g;
    }

    private LinearLayout.LayoutParams marginTop(int v) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin=dp(v); return p;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] a=f.listFiles(); if(a!=null) for(File c:a) deleteRecursive(c);
        }
        f.delete();
    }
}

