package vn.viettel.caobang.kpitammi;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class ReportClient {
    static final String SOURCE_URL = "https://script.google.com/macros/s/AKfycbzjwYws7Sx9YNrr67IowsaaaAYaGA3zr8GID1f-p6e5_Wx4qbrmShtBhbbbpyU06chz/exec?token=CBG-KPI-TAMMI-2026";
    static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String PREFS = "kpi_tammi_v130";
    private static final String KEY_LATEST_DIR = "latest_dir";
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US);

    static final class Info {
        final String name;
        final long size;
        final int totalChunks;
        final Instant modifiedTime;
        final LocalDate reportDate;

        Info(String name, long size, int totalChunks, Instant modifiedTime, LocalDate reportDate) {
            this.name = name;
            this.size = size;
            this.totalChunks = totalChunks;
            this.modifiedTime = modifiedTime;
            this.reportDate = reportDate;
        }
    }

    static final class StaleReportException extends Exception {
        final LocalDate sourceDate;
        StaleReportException(LocalDate sourceDate, String message) {
            super(message);
            this.sourceDate = sourceDate;
        }
    }

    static Info checkSource() throws Exception {
        JSONObject obj = getJson(withAction("info", null));
        if (!obj.optBoolean("ok", false)) {
            throw new Exception("Nguồn KPI trả lỗi: " + obj.optString("error", "không xác định"));
        }

        String name = obj.optString("name", "KPI_ngay_latest.zip");
        long size = obj.optLong("size", 0L);
        int totalChunks = obj.optInt("totalChunks", 0);
        String modified = obj.optString("modifiedTime", "");
        if (size <= 0 || totalChunks <= 0 || modified.isEmpty()) {
            throw new Exception("Nguồn KPI thiếu thông tin file ZIP hợp lệ.");
        }

        Instant instant = Instant.parse(modified);
        LocalDate sourceDate = instant.atZone(VN_ZONE).toLocalDate();
        LocalDate today = LocalDate.now(VN_ZONE);
        if (!sourceDate.equals(today)) {
            throw new StaleReportException(sourceDate,
                    "Nguồn báo cáo chưa cập nhật ngày hôm nay. Nguồn hiện tại: " + sourceDate.format(FILE_DATE)
                            + "; hôm nay: " + today.format(FILE_DATE) + ".");
        }

        return new Info(name, size, totalChunks, instant, sourceDate);
    }

    static List<File> downloadToday(Context context) throws Exception {
        Info info = checkSource();
        ByteArrayOutputStream out = new ByteArrayOutputStream((int)Math.min(info.size, Integer.MAX_VALUE));

        for (int i = 0; i < info.totalChunks; i++) {
            JSONObject chunk = getJson(withAction("chunk", i));
            if (!chunk.optBoolean("ok", false)) {
                throw new Exception("Lỗi tải chunk " + i + ": " + chunk.optString("error", "không xác định"));
            }
            String b64 = chunk.optString("dataBase64", "");
            if (b64.isEmpty()) throw new Exception("Chunk " + i + " không có dữ liệu.");
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            out.write(bytes);
        }

        byte[] zipBytes = out.toByteArray();
        if (zipBytes.length < 10000) throw new Exception("ZIP tải về quá nhỏ: " + zipBytes.length + " byte.");

        LocalDate today = LocalDate.now(VN_ZONE);
        String tag = today.format(FILE_DATE);
        File root = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "KPI_Tammi");
        File dayDir = new File(root, tag);
        deleteRecursively(dayDir);
        if (!dayDir.mkdirs() && !dayDir.isDirectory()) throw new Exception("Không tạo được thư mục báo cáo.");

        File zipFile = new File(dayDir, "KPI_ngay_" + tag + ".zip");
        try (FileOutputStream fos = new FileOutputStream(zipFile)) {
            fos.write(zipBytes);
        }

        File extracted = new File(dayDir, "extracted");
        if (!extracted.mkdirs() && !extracted.isDirectory()) throw new Exception("Không tạo được thư mục giải nén.");

        List<File> files = unzipAndValidate(zipBytes, extracted, tag);
        if (files.isEmpty()) throw new Exception("ZIP không có file báo cáo hợp lệ.");

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LATEST_DIR, extracted.getAbsolutePath()).apply();
        return files;
    }

    static List<File> latestFiles(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String path = p.getString(KEY_LATEST_DIR, "");
        if (path == null || path.isEmpty()) return new ArrayList<>();
        File dir = new File(path);
        ArrayList<File> out = new ArrayList<>();
        collectFiles(dir, out);
        return out;
    }

    private static List<File> unzipAndValidate(byte[] zipBytes, File outDir, String todayTag) throws Exception {
        ArrayList<File> result = new ArrayList<>();
        boolean hasToday = false;
        boolean hasWrongReportDate = false;
        String rootCanonical = outDir.getCanonicalPath() + File.separator;

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new ByteArrayInputStream(zipBytes)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String rawName = entry.getName();
                if (rawName == null || rawName.trim().isEmpty()) continue;
                String lower = rawName.toLowerCase(Locale.ROOT);
                if (lower.startsWith("__macosx/") || lower.endsWith(".ds_store")) continue;

                File dest = new File(outDir, rawName);
                String destCanonical = dest.getCanonicalPath();
                if (!destCanonical.startsWith(rootCanonical)) throw new Exception("ZIP chứa đường dẫn không an toàn: " + rawName);

                if (entry.isDirectory()) {
                    if (!dest.mkdirs() && !dest.isDirectory()) throw new Exception("Không tạo được thư mục: " + rawName);
                    continue;
                }

                File parent = dest.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) throw new Exception("Không tạo được thư mục con.");

                try (FileOutputStream fos = new FileOutputStream(dest)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = zis.read(buf)) > 0) fos.write(buf, 0, n);
                }

                String fileName = dest.getName();
                String ext = extension(fileName);
                if (isReportExtension(ext)) {
                    result.add(dest);
                    if (fileName.contains(todayTag)) hasToday = true;
                    if ((fileName.startsWith("Bao_cao_KQ_HoanThanh_KPI_ngay_")
                            || fileName.startsWith("KetQuaKPI_ngay_")
                            || fileName.startsWith("ChiTieu_")) && !fileName.contains(todayTag)) {
                        hasWrongReportDate = true;
                    }
                }
            }
        }

        if (!hasToday || hasWrongReportDate) {
            deleteRecursively(outDir);
            throw new StaleReportException(null,
                    "ZIP nguồn không đúng ngày " + todayTag + ". App đã chặn để không gửi nhầm báo cáo cũ.");
        }
        return result;
    }

    private static String extension(String name) {
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static boolean isReportExtension(String ext) {
        return ext.equals("xlsx") || ext.equals("xls") || ext.equals("png") || ext.equals("jpg")
                || ext.equals("jpeg") || ext.equals("pdf") || ext.equals("docx");
    }

    private static void collectFiles(File f, List<File> out) {
        if (f == null || !f.exists()) return;
        if (f.isFile()) {
            if (isReportExtension(extension(f.getName()))) out.add(f);
            return;
        }
        File[] list = f.listFiles();
        if (list != null) for (File x : list) collectFiles(x, out);
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursively(c);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static String withAction(String action, Integer index) {
        StringBuilder sb = new StringBuilder(SOURCE_URL)
                .append("&action=").append(action)
                .append("&_ts=").append(System.currentTimeMillis());
        if (index != null) sb.append("&index=").append(index);
        return sb.toString();
    }

    private static JSONObject getJson(String url) throws Exception {
        String text = httpGetText(url);
        return new JSONObject(text);
    }

    private static String httpGetText(String urlText) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlText);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 KPI-Tammi-AutoDate/1.3.0");
            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            if (in == null) throw new Exception("HTTP " + code + " không có nội dung.");
            try (BufferedInputStream bis = new BufferedInputStream(in); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = bis.read(buf)) > 0) out.write(buf, 0, n);
                String text = out.toString("UTF-8");
                if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + text);
                return text;
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private ReportClient() {}
}
