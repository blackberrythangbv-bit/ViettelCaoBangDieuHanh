package vn.viettel.caobang.kpitammi;

import android.net.Uri;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

public final class DirectReportDownloader {
    private DirectReportDownloader() {}

    public static String fetchAndStore(android.content.Context context, String sourceUrl) throws Exception {
        if (sourceUrl == null || sourceUrl.trim().isEmpty()) sourceUrl = AppConfig.DEFAULT_SOURCE_URL;
        String infoUrl = withParam(sourceUrl, "action", "info");
        byte[] first = httpGet(infoUrl);

        if (looksLikeZip(first)) {
            return ReportStore.saveZipBytes(context, first);
        }

        String text = new String(first, java.nio.charset.StandardCharsets.UTF_8).trim();
        JSONObject info = new JSONObject(text);
        if (!info.optBoolean("ok", false)) throw new Exception(info.optString("error", "Nguồn báo cáo trả lỗi"));

        String mode = info.optString("mode", "");
        if ("chunked-base64".equalsIgnoreCase(mode)) {
            int total = info.optInt("totalChunks", 0);
            if (total <= 0) throw new Exception("Nguồn báo cáo không có dữ liệu ZIP");
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(32768, info.optInt("size", 0)));
            for (int i = 0; i < total; i++) {
                String chunkUrl = withParam(withParam(sourceUrl, "action", "chunk"), "index", String.valueOf(i));
                JSONObject c = new JSONObject(new String(httpGet(chunkUrl), java.nio.charset.StandardCharsets.UTF_8));
                if (!c.optBoolean("ok", false)) throw new Exception(c.optString("error", "Lỗi tải chunk " + i));
                String b64 = c.optString("dataBase64", "");
                if (b64.isEmpty()) throw new Exception("Chunk " + i + " rỗng");
                out.write(Base64.getDecoder().decode(b64));
            }
            return ReportStore.saveZipBytes(context, out.toByteArray());
        }

        String directB64 = info.optString("base64", info.optString("dataBase64", ""));
        if (!directB64.isEmpty()) {
            return ReportStore.saveZipBytes(context, Base64.getDecoder().decode(directB64));
        }

        throw new Exception("Nguồn báo cáo không trả ZIP hợp lệ");
    }

    private static String withParam(String raw, String key, String value) {
        Uri u = Uri.parse(raw);
        Uri.Builder b = u.buildUpon().clearQuery();
        java.util.Set<String> names = u.getQueryParameterNames();
        for (String n : names) {
            if (n.equals(key)) continue;
            for (String v : u.getQueryParameters(n)) b.appendQueryParameter(n, v);
        }
        b.appendQueryParameter(key, value);
        return b.build().toString();
    }

    private static byte[] httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(45000);
        c.setRequestProperty("Accept", "application/json, application/zip, text/plain, */*");
        c.setRequestProperty("User-Agent", "Mozilla/5.0 KPI-Tammi/2.1");
        int code = c.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        byte[] data = readAll(in);
        c.disconnect();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        return data;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        if (in == null) return new byte[0];
        try (InputStream x = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = x.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static boolean looksLikeZip(byte[] b) {
        return b != null && b.length >= 4 && b[0] == 'P' && b[1] == 'K';
    }
}
