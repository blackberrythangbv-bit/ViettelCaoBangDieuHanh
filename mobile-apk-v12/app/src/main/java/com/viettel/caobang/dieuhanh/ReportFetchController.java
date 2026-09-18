package com.viettel.caobang.dieuhanh;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ReportFetchController {
    public interface Callback {
        void onSuccess(String fileName);
        void onError(String message);
    }

    static final String REPORT_URL = "https://script.google.com/macros/s/AKfycbxwDT_LV1D49SKfZkv0_CfmBcRcpubbJGnd9TFBL5b1y0AHQ-a1zbRQf83CBWDeRkaApQ/exec?view=report&embed=1";
    static final String PREFS = "vt_report_auto";
    static final String PREF_LAST_OK_DATE = "last_ok_date";
    static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<WebView> ACTIVE = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private ReportFetchController() {}

    public static String todayIso() {
        return LocalDate.now(VN_ZONE).toString();
    }

    public static String todayFileDate() {
        return LocalDate.now(VN_ZONE).format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
    }

    public static boolean alreadyAutoDownloadedToday(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return todayIso().equals(p.getString(PREF_LAST_OK_DATE, ""));
    }

    public static void fetch(Context context, Callback callback) {
        final Context webContext = (context instanceof Activity) ? context : context.getApplicationContext();
        final WebView w = new WebView(webContext);
        ACTIVE.add(w);

        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setUserAgentString(s.getUserAgentString() + " ViettelCaoBangReportFetcher/1.2.4");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) cm.setAcceptThirdPartyCookies(w, true);

        final NativeBridge bridge = new NativeBridge(context.getApplicationContext(), w, callback);
        w.addJavascriptInterface(bridge, "ReportNative");
        w.setWebChromeClient(new WebChromeClient());
        w.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(buildAutoScript(), null);
            }
        });

        w.loadUrl(REPORT_URL);
    }

    private static String buildAutoScript() {
        return "(function(){" +
                "if(window.__VT_AUTO_REPORT_FETCH__)return;window.__VT_AUTO_REPORT_FETCH__=1;" +
                "var started=Date.now(),forced=false,zipStarted=false;" +
                "function today(){var d=new Date();return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');}" +
                "function sendBlob(blob,name){try{var r=new FileReader();r.onloadend=function(){try{var x=String(r.result||'');var i=x.indexOf(',');ReportNative.saveBase64(name||'Bao_cao_KPI_ngay.zip',blob.type||'application/zip',i>=0?x.slice(i+1):x);}catch(e){ReportNative.error('Lỗi chuyển ZIP: '+e);}};r.readAsDataURL(blob);}catch(e){ReportNative.error('Lỗi đọc ZIP: '+e);}}" +
                "window.downloadBlob=function(blob,name){sendBlob(blob,name);};" +
                "function tick(){try{" +
                "if(!forced&&typeof loadReport==='function'&&typeof TOKEN!=='undefined'&&TOKEN){forced=true;try{loadReport(true);}catch(e){}}" +
                "var ready=(typeof REPORT!=='undefined'&&REPORT&&REPORT.reportDate===today()&&typeof downloadZip==='function');" +
                "if(ready&&!zipStarted){zipStarted=true;Promise.resolve(downloadZip()).catch(function(e){ReportNative.error('Lỗi tạo ZIP: '+e);});return;}" +
                "if(Date.now()-started>150000){ReportNative.error('Quá thời gian chờ báo cáo. Hãy mở app và đăng nhập Web App một lần.');return;}" +
                "setTimeout(tick,900);" +
                "}catch(e){if(Date.now()-started>150000){ReportNative.error(String(e));}else setTimeout(tick,900);}}" +
                "setTimeout(tick,700);" +
                "})();";
    }

    private static void destroy(WebView w) {
        try {
            ACTIVE.remove(w);
            w.stopLoading();
            w.loadUrl("about:blank");
            w.removeAllViews();
            w.destroy();
        } catch (Exception ignored) {}
    }

    private static final class NativeBridge {
        private final Context context;
        private final WebView webView;
        private final Callback callback;
        private boolean finished;

        NativeBridge(Context context, WebView webView, Callback callback) {
            this.context = context;
            this.webView = webView;
            this.callback = callback;
        }

        @JavascriptInterface
        public void saveBase64(String fileName, String mimeType, String base64) {
            if (finished) return;
            try {
                byte[] data;
                if (Build.VERSION.SDK_INT >= 26) data = Base64.getDecoder().decode(base64);
                else data = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);

                String safe = sanitize(fileName);
                if (!safe.toLowerCase().endsWith(".zip")) safe += ".zip";
                if (!safe.contains(todayFileDate())) {
                    throw new Exception("ZIP không đúng ngày " + todayFileDate() + ": " + safe);
                }

                saveFile(context, safe, mimeType, data);
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putString(PREF_LAST_OK_DATE, todayIso()).apply();
                finished = true;
                if (callback != null) callback.onSuccess(safe);
            } catch (Exception e) {
                finished = true;
                if (callback != null) callback.onError(e.getMessage() == null ? String.valueOf(e) : e.getMessage());
            } finally {
                webView.postDelayed(() -> destroy(webView), 700);
            }
        }

        @JavascriptInterface
        public void error(String message) {
            if (finished) return;
            finished = true;
            if (callback != null) callback.onError(message == null ? "Không lấy được báo cáo" : message);
            webView.postDelayed(() -> destroy(webView), 500);
        }
    }

    private static String sanitize(String name) {
        String n = (name == null || name.trim().isEmpty())
                ? "Bao_cao_KPI_ngay_" + todayFileDate() + ".zip"
                : name.trim();
        return n.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static void saveFile(Context context, String fileName, String mimeType, byte[] data) throws Exception {
        String mt = (mimeType == null || mimeType.isEmpty()) ? "application/zip" : mimeType;
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            cv.put(MediaStore.Downloads.MIME_TYPE, mt);
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
            return;
        }

        if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            throw new Exception("Chưa có quyền lưu tệp");
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ViettelCaoBang/BaoCaoNgay");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Không tạo được thư mục báo cáo");
        try (FileOutputStream fos = new FileOutputStream(new File(dir, fileName))) {
            fos.write(data);
        }
    }
}
