package vn.viettel.caobang.kpitammi.v2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.net.Uri;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** A handoff to Tammi. Only the user can confirm that they actually sent a report. */
public class ManualSendActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private ReportShareBundle reports;
    private LinearLayout root, items;
    private TextView status;
    private EditText message;
    private boolean loading;
    private String day;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences("manual_report_send", MODE_PRIVATE);
        day = ReportRecipients.day(new Date());
        ScrollView scroll = new ScrollView(this);
        root = column(); root.setPadding(dp(18), dp(22), dp(18), dp(32));
        scroll.setBackgroundColor(Color.rgb(246,248,252)); scroll.addView(root);
        button(root, "‹ Quay lại", () -> finish());
        label(root, "Gửi báo cáo thủ công", 26, true);
        label(root, "Ngày " + day.replace('-', '/'), 18, true);
        label(root, "Chọn từng mục → chọn Tammi → kiểm tra đúng người nhận → bấm Gửi trong Tammi. Quay lại đây để xác nhận đã gửi.", 16, false);
        status = label(root, "", 16, true);
        button(root, "Tải lại báo cáo mới nhất", this::load);
        items = column(); root.addView(items);
        setContentView(scroll);
        load();
    }

    private void load() {
        if (loading) return;
        loading = true; reports = null; items.removeAllViews();
        status.setText("Đang tải và kiểm tra báo cáo hôm nay…");
        executor.execute(() -> {
            try {
                String snapshotDay = ReportRecipients.day(new Date());
                File zip = ApiClient.downloadLatest(this);
                ReportShareBundle result = ReportShareBundle.extract(zip,
                    new File(getCacheDir(), "manual_reports/" + UUID.randomUUID()), snapshotDay);
                if (!snapshotDay.equals(ReportRecipients.day(new Date()))) throw new Exception("Đã sang ngày mới. Hãy tải lại.");
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    loading = false; day = snapshotDay; reports = result; render();
                });
            } catch (Exception e) {
                runOnUiThread(() -> { if (!isDestroyed()) { loading = false; status.setText("Không chuẩn bị được báo cáo: " + e.getMessage()); } });
            }
        });
    }

    private void render() {
        items.removeAllViews();
        fileCard("GROUP_EXCEL", "Excel báo cáo nhóm", ReportRecipients.GROUP);
        fileCard("GROUP_TOTAL", "Ảnh tổng hợp nhóm", ReportRecipients.GROUP);
        LinearLayout textCard = card();
        label(textCard, "Tin chữ điều hành", 20, true);
        label(textCard, ReportRecipients.GROUP, 16, true);
        label(textCard, "Dán tin nhắn điều hành từ KPI DNS đúng ngày báo cáo.", 15, false);
        message = new EditText(this); message.setTextSize(16); message.setMinLines(4);
        message.setHint("Tin điều hành ngày " + day.replace('-', '/'));
        message.setText(prefs.getString("draft:" + day, "")); textCard.addView(message);
        button(textCard, "Chia sẻ tin chữ", this::shareText);
        button(textCard, "Xác nhận đã gửi tin chữ", () -> {
            try { confirm(textId(), ReportRecipients.GROUP); } catch (Exception e) { error(e); }
        });
        TextView textState = label(textCard, "", 15, false);
        message.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
            public void onTextChanged(CharSequence s,int st,int before,int count) {
                prefs.edit().putString("draft:" + day, s.toString()).apply();
                try { textState.setText(stateLabel(textId())); } catch (Exception e) { textState.setText("Chưa có tin chữ đúng ngày"); }
            }
            public void afterTextChanged(android.text.Editable s) {}
        });
        try { textState.setText(stateLabel(textId())); } catch (Exception e) { textState.setText("Chưa có tin chữ đúng ngày"); }
        for (ReportRecipients.Recipient am : ReportRecipients.AMS) fileCard(am.code, "Ảnh KPI · " + am.shortName, am.tammiName);
        summary();
    }

    private void fileCard(String key, String title, String recipient) {
        LinearLayout card = card();
        label(card, title, 20, true); label(card, recipient, 16, true);
        File file = reports.files.get(key);
        label(card, file.getName(), 14, false);
        try {
            String id = reports.itemId(key, recipient);
            label(card, stateLabel(id), 15, false);
            button(card, "Sao chép tên người nhận", () -> {
                ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Người nhận", recipient));
                Toast.makeText(this,"Đã sao chép tên",Toast.LENGTH_SHORT).show();
            });
            if (file.getName().endsWith(".png")) button(card, "Xem ảnh", () -> preview(file));
            button(card, "Chia sẻ qua Tammi", () -> shareFile(file, id, recipient));
            Button ack = button(card, "Xác nhận đã gửi", () -> confirm(id, recipient));
            ack.setEnabled("opened".equals(prefs.getString(id,"")));
        } catch (Exception e) { error(e); }
    }

    private void shareFile(File file, String id, String recipient) {
        if (!current()) return;
        if (!file.isFile()) { Toast.makeText(this,"File tạm đã mất. Hãy tải lại báo cáo.",Toast.LENGTH_LONG).show(); return; }
        Uri uri = FileProvider.getUriForFile(this,getPackageName()+".files",file);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(file.getName().endsWith(".png") ? "image/png" : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.setClipData(ClipData.newUri(getContentResolver(),file.getName(),uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        handoff(send,id,recipient);
    }

    private String textId() throws Exception {
        String text = message.getText().toString().trim();
        if (text.length() == 0 || text.length() > 12000 ||
            !(text.contains(day.replace('-', '/')) || text.contains(day)))
            throw new Exception("Tin chữ phải có ngày " + day.replace('-', '/') + " và không quá 12.000 ký tự.");
        return day + ":GROUP_TEXT:" + ReportShareBundle.textHash(ReportRecipients.GROUP) + ":" + ReportShareBundle.textHash(text);
    }

    private void shareText() {
        if (!current()) return;
        try {
            String id = textId();
            Intent send = new Intent(Intent.ACTION_SEND); send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT,message.getText().toString().trim());
            handoff(send,id,ReportRecipients.GROUP);
        } catch (Exception e) { error(e); }
    }

    private void handoff(Intent send, String id, String recipient) {
        String old = prefs.getString(id, "");
        new AlertDialog.Builder(this).setTitle("Kiểm tra người nhận")
            .setMessage(("confirmed_by_user".equals(old) ? "Anh đã xác nhận gửi mục này. Gửi lại có thể trùng.\n\n" : "") +
                "Trong Tammi, chọn đúng:\n" + recipient + "\n\nKiểm tra nội dung rồi bấm Gửi. App chỉ ghi nhận hoàn tất khi anh xác nhận.")
            .setNegativeButton("Hủy",null).setPositiveButton("Mở bảng chia sẻ",(d,w) -> {
                if (!current()) return;
                try {
                    startActivity(Intent.createChooser(send,"Chọn Tammi · " + recipient));
                    // Chooser launch is not delivery acknowledgement.
                    prefs.edit().putString(id,"opened").apply(); render();
                } catch (Exception e) { error(e); }
            }).show();
    }

    private void confirm(String id, String recipient) {
        if (!current()) return;
        if (!"opened".equals(prefs.getString(id,""))) {
            Toast.makeText(this,"Hãy chia sẻ mục này trước khi xác nhận.",Toast.LENGTH_LONG).show(); return;
        }
        new AlertDialog.Builder(this).setTitle("Anh đã gửi trong Tammi?")
            .setMessage("Chỉ xác nhận sau khi thấy báo cáo đã gửi đúng tới:\n" + recipient)
            .setNegativeButton("Chưa gửi",null).setPositiveButton("Đã gửi đúng người",(d,w) -> {
                if (!current()) return;
                prefs.edit().putString(id,"confirmed_by_user").apply(); render();
            }).show();
    }

    private boolean current() {
        if (reports == null || !day.equals(ReportRecipients.day(new Date()))) {
            Toast.makeText(this,"Hãy tải lại báo cáo đúng ngày hôm nay.",Toast.LENGTH_LONG).show(); return false;
        }
        return true;
    }
    private String stateLabel(String id) {
        String state = prefs.getString(id,"");
        return "confirmed_by_user".equals(state) ? "✓ Anh xác nhận đã gửi" :
            "opened".equals(state) ? "Đã mở chia sẻ · chưa xác nhận gửi" : "Chưa gửi";
    }
    private void summary() {
        int count = 0;
        try {
            for (String key : reports.files.keySet()) {
                String recipient = ReportRecipients.GROUP;
                for (ReportRecipients.Recipient am : ReportRecipients.AMS) if (am.code.equals(key)) recipient = am.tammiName;
                if ("confirmed_by_user".equals(prefs.getString(reports.itemId(key,recipient),""))) count++;
            }
        } catch (Exception e) { error(e); }
        status.setText("Anh đã xác nhận " + count + "/8 file. Tin chữ được xác nhận riêng.");
    }
    private void preview(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 1;
        while (bounds.outWidth / options.inSampleSize > 1600 || bounds.outHeight / options.inSampleSize > 1600) options.inSampleSize *= 2;
        android.graphics.Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(),options);
        if (bitmap == null) { Toast.makeText(this,"Không đọc được ảnh",Toast.LENGTH_LONG).show(); return; }
        ImageView view = new ImageView(this); view.setImageBitmap(bitmap); view.setAdjustViewBounds(true);
        new AlertDialog.Builder(this).setTitle(file.getName()).setView(view).setPositiveButton("Đóng",null).show();
    }
    private void error(Exception e) { Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout card() {
        LinearLayout view = column(); view.setPadding(dp(14),dp(16),dp(14),dp(16)); view.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,-2); params.topMargin = dp(14); items.addView(view,params); return view;
    }
    private TextView label(LinearLayout parent,String text,int size,boolean bold) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(Color.rgb(18,47,78));
        if (bold) view.setTypeface(null,Typeface.BOLD); view.setPadding(0,dp(4),0,dp(6)); parent.addView(view); return view;
    }
    private Button button(LinearLayout parent,String title,Runnable action) {
        Button button = new Button(this); button.setText(title); button.setAllCaps(false); button.setOnClickListener(v -> action.run()); parent.addView(button); return button;
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
