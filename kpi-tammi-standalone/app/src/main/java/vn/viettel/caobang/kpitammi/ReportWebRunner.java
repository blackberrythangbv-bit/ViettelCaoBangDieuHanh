package vn.viettel.caobang.kpitammi;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.concurrent.atomic.AtomicBoolean;

public class ReportWebRunner {
    public interface Callback {
        void onSuccess(String fileName);
        void onError(String message);
    }

    private static final String REPORT_URL = LoginActivity.HOME_URL + "?view=report&nativeAuto=1";
    private final Context context;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private WebView webView;

    public ReportWebRunner(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
    }

    public void start() {
        handler.post(() -> {
            try {
                Context themed = new ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_Light_NoActionBar);
                webView = new WebView(themed);
                WebSettings s = webView.getSettings();
                s.setJavaScriptEnabled(true);
                s.setDomStorageEnabled(true);
                s.setDatabaseEnabled(true);
                s.setAllowFileAccess(false);
                s.setAllowContentAccess(true);
                s.setLoadsImagesAutomatically(true);
                s.setUserAgentString(s.getUserAgentString() + " KPI-Tammi-Auto/2.0");

                CookieManager cm = CookieManager.getInstance();
                cm.setAcceptCookie(true);
                cm.setAcceptThirdPartyCookies(webView, true);

                webView.addJavascriptInterface(new Bridge(), "AndroidReport");
                webView.setWebChromeClient(new WebChromeClient());
                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);
                        if (url == null) return;
                        if (!url.contains("view=report")) {
                            handler.postDelayed(() -> {
                                if (!finished.get() && webView != null && webView.getUrl() != null && !webView.getUrl().contains("view=report")) {
                                    fail("NEED_LOGIN");
                                }
                            }, 3500);
                            return;
                        }
                        injectAutoScript();
                    }
                });
                webView.loadUrl(REPORT_URL);
                handler.postDelayed(() -> fail("Quá thời gian 120 giây khi tạo báo cáo"), 120000);
            } catch (Throwable t) {
                fail(t.getMessage() == null ? t.toString() : t.getMessage());
            }
        });
    }

    private void injectAutoScript() {
        if (webView == null) return;
        String js = """
(function(){
  if(window.__KPI_TAMMI_NATIVE_RUNNING__) return;
  window.__KPI_TAMMI_NATIVE_RUNNING__=true;
  function err(x){ try{ AndroidReport.onError(String(x&&x.message?x.message:x)); }catch(e){} }
  function waitReady(n){
    try{
      var token='';
      try{ token=localStorage.getItem('DNS_AUTH_TOKEN_V16')||''; }catch(e){}
      if(!token){ AndroidReport.onError('NEED_LOGIN'); return; }
      if(typeof loadReport!=='function' || typeof downloadZip!=='function'){
        if(n>80){ AndroidReport.onError('Không tìm thấy hàm tạo báo cáo trên Web App'); return; }
        setTimeout(function(){waitReady(n+1)},500); return;
      }
      window.downloadBlob=function(blob,name){
        try{
          var r=new FileReader();
          r.onloadend=function(){
            try{
              var s=String(r.result||'');
              var p=s.indexOf(',');
              AndroidReport.saveBase64(String(name||'Bao_cao_KPI_ngay.zip'), p>=0?s.slice(p+1):s);
            }catch(e){ err(e); }
          };
          r.onerror=function(){ AndroidReport.onError('Không đọc được ZIP từ Web App'); };
          r.readAsDataURL(blob);
        }catch(e){ err(e); }
      };
      var d=new Date();
      var iso=d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');
      var input=document.getElementById('reportDate');
      if(input) input.value=iso;
      Promise.resolve(loadReport(true)).then(function(){
        if(!window.REPORT) throw new Error('Web App chưa tạo xong dữ liệu báo cáo');
        if(String(REPORT.reportDate||'')!==iso) throw new Error('Ngày báo cáo không khớp: '+String(REPORT.reportDate||''));
        return downloadZip();
      }).catch(err);
    }catch(e){ err(e); }
  }
  waitReady(0);
})();
""";
        webView.evaluateJavascript(js, null);
    }

    private void success(String fileName) {
        if (!finished.compareAndSet(false, true)) return;
        destroy();
        callback.onSuccess(fileName);
    }

    private void fail(String message) {
        if (!finished.compareAndSet(false, true)) return;
        destroy();
        callback.onError(message == null ? "Lỗi không xác định" : message);
    }

    private void destroy() {
        handler.post(() -> {
            if (webView != null) {
                try { webView.stopLoading(); webView.removeJavascriptInterface("AndroidReport"); webView.destroy(); } catch (Exception ignored) {}
                webView = null;
            }
        });
    }

    private class Bridge {
        @JavascriptInterface
        public void saveBase64(String fileName, String base64) {
            handler.post(() -> {
                try {
                    String saved = ReportStore.saveZip(context, fileName, base64);
                    success(saved);
                } catch (Exception e) {
                    fail(e.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void onError(String message) {
            handler.post(() -> fail(message));
        }
    }
}
