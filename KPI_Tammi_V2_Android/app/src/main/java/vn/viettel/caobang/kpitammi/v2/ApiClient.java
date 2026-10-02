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
        JSONObject info = info();
        int total = info.optInt("totalChunks", 0);
        if (total <= 0) throw new Exception("Nguồn báo cáo không trả số khối hợp lệ");

        ByteArrayOutputStream all = new ByteArrayOutputStream(Math.max(info.optInt("size", 0), 256 * 1024));
        for (int i = 0; i < total; i++) {
            JSONObject part = getJson(BASE_URL + "&action=chunk&index=" + i);
            if (!part.optBoolean("ok", false)) throw new Exception("Lỗi tải khối " + i + ": " + part.optString("error", ""));
            byte[] bytes = Base64.decode(part.getString("dataBase64"), Base64.DEFAULT);
            all.write(bytes);
        }

        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) dir = context.getFilesDir();
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Không tạo được thư mục lưu báo cáo");

        File out = new File(dir, "KPI_ngay_latest.zip");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(all.toByteArray());
        }

        int entries = countZipEntries(out);
        if (entries != 8) throw new Exception("Hậu kiểm thất bại: nhận " + entries + "/8 file");
        return out;
    }

    public static int countZipEntries(File zip) throws Exception {
        int n = 0;
        try (ZipInputStream zis = new ZipInputStream(new java.io.FileInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (!e.isDirectory()) n++;
                zis.closeEntry();
            }
        }
        return n;
    }

    private static JSONObject getJson(String address) throws Exception {
        HttpURLConnection c = null;
        try {
            URL url = new URL(address);
            c = (HttpURLConnection) url.openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(20000);
            c.setReadTimeout(60000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "KPI-Tammi-V2/2.0");
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
