package vn.viettel.caobang.kpitammi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.File;

public class ZipCleanupReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String internalPath = intent.getStringExtra("internal_zip_path");
        String dayDirPath = intent.getStringExtra("day_dir_path");
        String publicUri = intent.getStringExtra("public_zip_uri");
        String legacyPath = intent.getStringExtra("public_legacy_path");

        // Xóa toàn bộ dữ liệu nội bộ của báo cáo ngày đó: ZIP + Excel/PNG đã giải nén.
        if (dayDirPath != null && !dayDirPath.isEmpty()) {
            try {
                deleteRecursive(new File(dayDirPath));
            } catch (Exception ignored) {}
        } else if (internalPath != null && !internalPath.isEmpty()) {
            // Tương thích với lịch xóa đã tạo từ bản cũ.
            try {
                File f = new File(internalPath);
                if (f.exists()) f.delete();
            } catch (Exception ignored) {}
        }

        // Xóa ZIP đã lưu ở Download trên Android 10+.
        if (publicUri != null && !publicUri.isEmpty()) {
            try {
                context.getContentResolver().delete(Uri.parse(publicUri), null, null);
            } catch (Exception ignored) {}
        }

        // Xóa ZIP Download kiểu cũ trên Android 9 trở xuống.
        if (legacyPath != null && !legacyPath.isEmpty()) {
            try {
                File f = new File(legacyPath);
                if (f.exists()) f.delete();
            } catch (Exception ignored) {}
        }

        context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE).edit()
                .putString("last_message", "Đã tự xóa toàn bộ báo cáo sau 24 giờ để giải phóng dung lượng.")
                .remove("last_zip")
                .remove("last_extract_dir")
                .remove("last_report_date")
                .remove("last_public_zip_uri")
                .remove("last_public_zip_legacy_path")
                .remove("last_zip_delete_at")
                .apply();
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) deleteRecursive(k);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
