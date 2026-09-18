package vn.viettel.caobang.kpitammi;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.content.FileProvider;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ReportStore {
    private ReportStore() {}

    // Giữ tương thích với renderer WebView cũ nếu cần dùng lại.
    public static String saveZip(Context context, String fileName, String base64) throws Exception {
        byte[] data;
        if (Build.VERSION.SDK_INT >= 26) data = java.util.Base64.getDecoder().decode(base64);
        else data = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
        return saveZipBytes(context, data);
    }

    public static String saveZipBytes(Context context, byte[] data) throws Exception {
        if (data == null || data.length < 10000) throw new Exception("ZIP quá nhỏ, nghi dữ liệu chưa hoàn tất");
        String today = todayDmy();
        validateZipDate(data, today);

        String fileName = "Bao_cao_KPI_ngay_" + today + ".zip";
        File dayDir = new File(context.getFilesDir(), "reports/" + today);
        if (!dayDir.exists() && !dayDir.mkdirs()) throw new Exception("Không tạo được thư mục báo cáo");
        File zipFile = new File(dayDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(zipFile)) {
            fos.write(data);
        }

        File extracted = new File(dayDir, "extracted");
        deleteRecursive(extracted);
        if (!extracted.mkdirs()) throw new Exception("Không tạo được thư mục giải nén");
        unzip(data, extracted);
        File[] files = extracted.listFiles(File::isFile);
        if (files == null || files.length < 2) throw new Exception("ZIP giải nén không đủ file báo cáo");

        savePublicDownload(context, fileName, data);
        context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE).edit()
                .putString("last_zip", zipFile.getAbsolutePath())
                .putString("last_extract_dir", extracted.getAbsolutePath())
                .putString("last_report_date", today)
                .putString("last_message", "Đã tải & giải nén " + fileName)
                .apply();
        return fileName;
    }

    public static void shareLatest(Activity activity) throws Exception {
        String path = activity.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
                .getString("last_extract_dir", "");
        if (path == null || path.isEmpty()) throw new Exception("Chưa có báo cáo đã tải để gửi Tammi.");
        File dir = new File(path);
        File[] fs = dir.listFiles(File::isFile);
        if (fs == null || fs.length == 0) throw new Exception("Không tìm thấy file báo cáo đã giải nén.");
        Arrays.sort(fs, Comparator.comparing(File::getName));

        ArrayList<Uri> uris = new ArrayList<>();
        String authority = activity.getPackageName() + ".fileprovider";
        for (File f : fs) {
            uris.add(FileProvider.getUriForFile(activity, authority, f));
        }

        Intent share = new Intent(Intent.ACTION_SEND_MULTIPLE);
        share.setType("*/*");
        share.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        share.putExtra(Intent.EXTRA_SUBJECT, "Báo cáo KPI Viettel Cao Bằng " + todayDmy());
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        List<ResolveInfo> targets = activity.getPackageManager().queryIntentActivities(share, 0);
        for (ResolveInfo r : targets) {
            CharSequence label = r.loadLabel(activity.getPackageManager());
            String name = label == null ? "" : label.toString().toLowerCase(Locale.ROOT);
            String pkg = r.activityInfo == null ? "" : r.activityInfo.packageName;
            if (name.contains("tammi") || pkg.toLowerCase(Locale.ROOT).contains("tammi")) {
                share.setPackage(pkg);
                activity.startActivity(share);
                return;
            }
        }
        activity.startActivity(Intent.createChooser(share, "Gửi báo cáo qua Tammi"));
    }

    private static void validateZipDate(byte[] data, String today) throws Exception {
        int count = 0;
        boolean hasExcel = false;
        boolean hasTotalPng = false;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                count++;
                String name = new File(e.getName()).getName();
                if (!name.contains(today)) throw new Exception("Nguồn còn file sai ngày: " + name);
                String low = name.toLowerCase(Locale.ROOT);
                if (low.endsWith(".xlsx")) hasExcel = true;
                if (low.startsWith("ketquakpi_ngay_") && low.endsWith(".png")) hasTotalPng = true;
            }
        }
        if (count < 2 || !hasExcel || !hasTotalPng) throw new Exception("ZIP chưa đủ bộ báo cáo ngày " + today);
    }

    private static void unzip(byte[] data, File outDir) throws Exception {
        String root = outDir.getCanonicalPath() + File.separator;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            byte[] buf = new byte[16384];
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                File out = new File(outDir, new File(e.getName()).getName());
                if (!out.getCanonicalPath().startsWith(root)) throw new Exception("ZIP không an toàn");
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    int n;
                    while ((n = zin.read(buf)) > 0) fos.write(buf, 0, n);
                }
            }
        }
    }

    private static void savePublicDownload(Context context, String fileName, byte[] data) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
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
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("Không tạo được thư mục Downloads");
            try (FileOutputStream fos = new FileOutputStream(new File(dir, fileName))) {
                fos.write(data);
            }
        }
    }

    private static String todayDmy() {
        SimpleDateFormat f = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        return f.format(new Date());
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
