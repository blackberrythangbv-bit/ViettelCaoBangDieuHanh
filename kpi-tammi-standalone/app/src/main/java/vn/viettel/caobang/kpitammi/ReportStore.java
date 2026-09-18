package vn.viettel.caobang.kpitammi;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class ReportStore {
    private ReportStore() {}

    public static String saveZip(Context context, String fileName, String base64) throws Exception {
        String today = new SimpleDateFormat("dd-MM-yyyy", Locale.US) {{ setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh")); }}.format(new Date());
        String safe = sanitize(fileName == null || fileName.trim().isEmpty() ? "Bao_cao_KPI_ngay_" + today + ".zip" : fileName);
        if (!safe.toLowerCase(Locale.ROOT).endsWith(".zip")) safe += ".zip";
        if (!safe.contains(today)) throw new Exception("ZIP không đúng ngày hiện tại: " + safe);

        byte[] data;
        if (Build.VERSION.SDK_INT >= 26) data = Base64.getDecoder().decode(base64);
        else data = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
        if (data.length < 10000) throw new Exception("ZIP quá nhỏ, nghi dữ liệu chưa hoàn tất");

        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, safe);
            cv.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
            cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ViettelCaoBang/BaoCaoNgay");
            cv.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) throw new Exception("Không tạo được file trong Downloads");
            try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new Exception("Không mở được file để ghi");
                os.write(data);
            }
            cv.clear();
            cv.put(MediaStore.Downloads.IS_PENDING, 0);
            context.getContentResolver().update(uri, cv, null, null);
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ViettelCaoBang/BaoCaoNgay");
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("Không tạo được thư mục báo cáo");
            try (FileOutputStream fos = new FileOutputStream(new File(dir, safe))) {
                fos.write(data);
            }
        }
        return safe;
    }

    private static String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
