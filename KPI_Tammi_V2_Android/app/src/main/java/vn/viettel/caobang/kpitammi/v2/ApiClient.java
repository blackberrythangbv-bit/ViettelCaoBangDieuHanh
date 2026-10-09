package vn.viettel.caobang.kpitammi.v2;

import android.content.Context;
import android.os.Environment;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ApiClient {
    public static final String BASE_URL =
            "https://script.google.com/macros/s/AKfycbz7fmyLNjEUti_8JwIjCRQv-u-Bhe4D2jTwCtBEOZtecQSgqF3q7wSj6qhaPvTdTkas/exec?token=CBG-KPI-TAMMI-2026";

    private ApiClient() {}

    public static JSONObject ping() throws Exception {
        JSONObject o = getJson(BASE_URL + "&action=ping");
        if (!o.optBoolean("ok", false)) throw new Exception(o.optString("error", "Nguồn KPI không phản hồi"));
        return o;
    }

    public static JSONObject info() throws Exception {
        JSONObject o = getJson(BASE_URL + "&action=info");
        if (!o.optBoolean("ok", false)) throw new Exception(o.optString("error", "Không lấy được thông tin báo cáo"));
        return o;
    }

    public static File downloadLatest(Context context) throws Exception {
        String reportDay = reportDay();
        JSONObject info = info();
        int total = info.optInt("totalChunks", 0);
        if (total <= 0 || total > 400) throw new Exception("Nguồn báo cáo không trả số khối hợp lệ");

        int expectedSize = info.optInt("size", 0);
        if (expectedSize <= 0 || expectedSize > 64 * 1024 * 1024) throw new Exception("Kích thước báo cáo không hợp lệ");
        ByteArrayOutputStream all = new ByteArrayOutputStream(Math.max(info.optInt("size", 0), 256 * 1024));
        for (int i = 0; i < total; i++) {
            JSONObject part = getJson(BASE_URL + "&action=chunk&index=" + i);
            if (!part.optBoolean("ok", false)) throw new Exception("Lỗi tải khối " + i + ": " + part.optString("error", ""));
            if (part.optInt("index", -1) != i || part.optInt("totalChunks", -1) != total) throw new Exception("Nguồn báo cáo thay đổi trong lúc tải. Vui lòng tải lại.");
            byte[] bytes = Base64.decode(part.getString("dataBase64"), Base64.DEFAULT);
            if (all.size() + bytes.length > expectedSize) throw new Exception("Dữ liệu báo cáo vượt kích thước nguồn");
            all.write(bytes);
        }

        JSONObject after = info();
        if (all.size() != expectedSize || after.optInt("size", -1) != expectedSize || !info.optString("modifiedTime").equals(after.optString("modifiedTime"))) throw new Exception("Báo cáo thay đổi hoặc tải thiếu dữ liệu. Vui lòng tải lại.");

        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) dir = context.getFilesDir();
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Không tạo được thư mục lưu báo cáo");

        File out = File.createTempFile("KPI_download_", ".zip", dir);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(all.toByteArray());
        }

        int entries;
        try { entries = countZipEntries(out); } catch (Exception e) { out.delete(); throw e; }
        if (entries != 8) { out.delete(); throw new Exception("Hậu kiểm thất bại: nhận " + entries + "/8 file"); }
        if (!reportDay.equals(reportDay())) { out.delete(); throw new Exception("Ngày báo cáo đã thay đổi. Vui lòng tải lại."); }
        File target = new File(dir, "Bao_cao_KPI_ngay_" + reportDay + ".zip");
        java.nio.file.Files.move(out.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    public static int countZipEntries(File zip) throws Exception {
        int n = 0, png = 0, xlsx = 0;
        java.util.HashSet<String> names = new java.util.HashSet<>();
        java.util.HashSet<String> expected = new java.util.HashSet<>();
        String day = reportDay();
        expected.add("Bao_cao_KQ_HoanThanh_KPI_ngay_" + day + ".xlsx");
        expected.add("KetQuaKPI_ngay_" + day + ".png");
        for (String am : new String[]{"HOAIBT4","NUONGPM","THAODP7","LANHT22","HUEHT16","QUYENLTN"})
            expected.add("ChiTieu_" + day + "_" + am + ".png");
        try (ZipInputStream zis = new ZipInputStream(new java.io.FileInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (!e.isDirectory()) {
                    String name = e.getName();
                    if (!expected.contains(name) || !names.add(name) || name.contains("/") || name.contains("\\")) throw new Exception("Tên file ZIP không hợp lệ");
                    if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) png++;
                    else if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) xlsx++;
                    else throw new Exception("Báo cáo chứa định dạng không hợp lệ");
                    byte[] signature = new byte[name.endsWith(".png") ? 8 : 4];
                    int got = 0, nread;
                    while (got < signature.length && (nread = zis.read(signature, got, signature.length-got)) != -1) got += nread;
                    byte[] required = name.endsWith(".png") ? new byte[]{(byte)137,80,78,71,13,10,26,10} : new byte[]{80,75,3,4};
                    if (got != required.length || !java.util.Arrays.equals(signature, required)) throw new Exception("File hỏng hoặc sai định dạng: " + name);
                    long size = got;
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = zis.read(buffer)) != -1) { size += read; if (size > 32 * 1024 * 1024) throw new Exception("File báo cáo quá lớn"); }
                    if (size == 0) throw new Exception("File báo cáo rỗng");
                    n++;
                }
                zis.closeEntry();
            }
        }
        if (!names.equals(expected) || png != 7 || xlsx != 1) throw new Exception("Bộ báo cáo phải có 7 PNG và 1 XLSX");
        return n;
    }

    private static String reportDay() {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        return f.format(new java.util.Date());
    }

    private static JSONObject getJson(String address) throws Exception {
        HttpURLConnection c = null;
        try {
            URL url = new URL(address);
            c = (HttpURLConnection) url.openConnection();
            c.setInstanceFollowRedirects(true);
            c.setUseCaches(false);
            c.setRequestProperty("Cache-Control", "no-cache");
            c.setConnectTimeout(20000);
            c.setReadTimeout(60000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "KPI-Tammi-V2/2.0.4");
            int code = c.getResponseCode();
            InputStream in = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
            if (in == null) throw new Exception("HTTP " + code);
            byte[] buf = readAll(in);
            String text = new String(buf, StandardCharsets.UTF_8).trim();
            JSONObject o = new JSONObject(text);
            if (code < 200 || code >= 400) throw new Exception("HTTP " + code + ": " + o.optString("error", text));
            return o;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = input.read(b)) >= 0) out.write(b, 0, n);
            return out.toByteArray();
        }
    }
}

