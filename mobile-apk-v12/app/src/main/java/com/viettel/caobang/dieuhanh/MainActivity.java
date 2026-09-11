package com.viettel.caobang.dieuhanh;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String HOME_URL = "https://script.google.com/macros/s/AKfycbxwDT_LV1D49SKfZkv0_CfmBcRcpubbJGnd9TFBL5b1y0AHQ-a1zbRQf83CBWDeRkaApQ/exec";
    private static final int STORAGE_REQ = 1201;
    private WebView webView;

    private static final String MOBILE_UI_PATCH = """
(function(){
  if(window.__VT_MOBILE_UI__) return;
  window.__VT_MOBILE_UI__ = true;
  const css = `
  @media (max-width:760px){
    html{-webkit-text-size-adjust:82% !important;text-size-adjust:82% !important}
    .tabs,.tabbar,.nav-tabs,[role="tablist"]{gap:2px !important;padding-left:3px !important;padding-right:3px !important;overflow-x:hidden !important}
    .tabs>.tab,.tabs>button,.tabbar>button,.nav-tabs>button,[role="tab"]{
      flex:1 1 0 !important;min-width:0 !important;max-width:none !important;
      font-size:10px !important;line-height:1.08 !important;padding:6px 3px !important;
      white-space:normal !important;text-align:center !important;overflow:hidden !important;
    }
    header button,.top button,.toolbar button,.actions button,.iconbtn{
      font-size:10px !important;line-height:1.1 !important;padding:6px 6px !important;
    }
  }`;
  function apply(){
    try{
      if(document.getElementById('vt-mobile-ui-style')) return;
      const st=document.createElement('style');
      st.id='vt-mobile-ui-style';st.textContent=css;
      (document.head||document.documentElement).appendChild(st);
    }catch(e){}
  }
  apply();
  document.addEventListener('DOMContentLoaded',apply,{once:true});
})();
""";

    private static final String DOWNLOAD_PATCH = """
(function(){
  if(window.__VT_NATIVE_DL__) return;
  window.__VT_NATIVE_DL__=true;

  function sendBlob(blob,name){
    try{
      const reader=new FileReader();
      reader.onloadend=function(){
        try{
          const result=String(reader.result||'');
          const comma=result.indexOf(',');
          const b64=comma>=0?result.slice(comma+1):result;
          AndroidDownload.saveBase64(name||'bao_cao',blob.type||'application/octet-stream',b64);
        }catch(e){ console.error('VT saveBase64',e); }
      };
      reader.readAsDataURL(blob);
    }catch(e){ console.error('VT sendBlob',e); }
  }

  window.__VT_downloadBlobUrl=function(url,name,mime){
    fetch(url).then(r=>r.blob()).then(b=>sendBlob(b,name||'bao_cao')).catch(e=>console.error('VT blob url',e));
  };
  window.__VT_saveBlob=sendBlob;

  try{
    const nativeClick=HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click=function(){
      try{
        const href=String(this.href||'');
        if(href.startsWith('blob:')){
          const name=this.download||'bao_cao';
          fetch(href).then(r=>r.blob()).then(b=>sendBlob(b,name)).catch(e=>console.error('VT anchor blob',e));
          return;
        }
      }catch(e){}
      return nativeClick.call(this);
    };
  }catch(e){}

  try{
    if(typeof window.downloadBlob==='function'){
      window.downloadBlob=function(blob,name){ sendBlob(blob,name); };
    }
  }catch(e){}

  document.addEventListener('click',function(ev){
    try{
      const a=ev.target&&ev.target.closest?ev.target.closest('a'):null;
      if(a&&String(a.href||'').startsWith('blob:')){
        ev.preventDefault(); ev.stopImmediatePropagation();
        fetch(a.href).then(r=>r.blob()).then(b=>sendBlob(b,a.download||'bao_cao'));
      }
    }catch(e){}
  },true);
})();
""";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setTextZoom(82);
        s.setUserAgentString(s.getUserAgentString() + " ViettelCaoBangApp/1.2.3");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new DownloadBridge(this), "AndroidDownload");

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            Set<String> origins = new HashSet<>();
            origins.add("*");
            WebViewCompat.addDocumentStartJavaScript(webView, MOBILE_UI_PATCH, origins);
            WebViewCompat.addDocumentStartJavaScript(webView, DOWNLOAD_PATCH, origins);
        }

        webView.setWebChromeClient(new WebChromeClient());

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u=request.getUrl();
                String host=u.getHost()==null?"":u.getHost();
                if(host.endsWith("script.google.com") ||
                   host.endsWith("script.googleusercontent.com") ||
                   host.endsWith("github.io")) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                    return true;
                } catch(Exception e) {
                    return false;
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view,url);
                view.evaluateJavascript(MOBILE_UI_PATCH, null);
                view.evaluateJavascript(DOWNLOAD_PATCH, null);
            }
        });

        webView.setDownloadListener((url,userAgent,contentDisposition,mimeType,contentLength) -> {
            String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
            if(url!=null && url.startsWith("blob:")) {
                String js="window.__VT_downloadBlobUrl && window.__VT_downloadBlobUrl("+
                    jsonQuote(url)+","+jsonQuote(name)+","+jsonQuote(mimeType)+");";
                webView.evaluateJavascript(js,null);
                return;
            }
            downloadHttp(url,userAgent,mimeType,name);
        });

        if(Build.VERSION.SDK_INT<=28 &&
           checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_REQ);
        }

        webView.loadUrl(HOME_URL);
    }

    private static String jsonQuote(String s) {
        if(s==null) return "null";
        return "\"" + s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r") + "\"";
    }

    private void downloadHttp(String url,String userAgent,String mimeType,String fileName) {
        try {
            DownloadManager.Request req=new DownloadManager.Request(Uri.parse(url));
            req.setMimeType(mimeType);
            req.addRequestHeader("User-Agent", userAgent);
            String cookie=CookieManager.getInstance().getCookie(url);
            if(cookie!=null) req.addRequestHeader("Cookie",cookie);
            req.setTitle(fileName);
            req.setDescription("Đang tải báo cáo Viettel Cao Bằng");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(req);
            Toast.makeText(this,"Đang tải: "+fileName,Toast.LENGTH_SHORT).show();
        } catch(Exception e) {
            Toast.makeText(this,"Không tải được file: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if(webView!=null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    public static class DownloadBridge {
        private final Activity activity;
        DownloadBridge(Activity a) { this.activity=a; }

        @JavascriptInterface
        public void saveBase64(String fileName,String mimeType,String base64) {
            try {
                byte[] data;
                if(Build.VERSION.SDK_INT>=26) data=Base64.getDecoder().decode(base64);
                else data=android.util.Base64.decode(base64, android.util.Base64.DEFAULT);

                String safe=(fileName==null||fileName.trim().isEmpty())?"bao_cao":fileName.replaceAll("[\\\\/:*?\"<>|]","_");
                if(!safe.contains(".")) {
                    String ext=MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                    if(ext!=null&&!ext.isEmpty()) safe += "."+ext;
                }

                if(Build.VERSION.SDK_INT>=29) {
                    ContentValues cv=new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME,safe);
                    cv.put(MediaStore.Downloads.MIME_TYPE,(mimeType==null||mimeType.isEmpty())?"application/octet-stream":mimeType);
                    cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ViettelCaoBang");
                    cv.put(MediaStore.Downloads.IS_PENDING,1);
                    Uri uri=activity.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cv);
                    if(uri==null) throw new Exception("Không tạo được file");
                    try(OutputStream os=activity.getContentResolver().openOutputStream(uri)) {
                        if(os==null) throw new Exception("Không mở được file");
                        os.write(data);
                    }
                    cv.clear();
                    cv.put(MediaStore.Downloads.IS_PENDING,0);
                    activity.getContentResolver().update(uri,cv,null,null);
                } else {
                    if(activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED) {
                        activity.runOnUiThread(() -> Toast.makeText(activity,"Hãy cho phép quyền lưu tệp rồi tải lại.",Toast.LENGTH_LONG).show());
                        return;
                    }
                    File dir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"ViettelCaoBang");
                    if(!dir.exists()) dir.mkdirs();
                    try(FileOutputStream fos=new FileOutputStream(new File(dir,safe))) {
                        fos.write(data);
                    }
                }

                final String msg="Đã lưu: Download/ViettelCaoBang/"+safe;
                activity.runOnUiThread(() -> Toast.makeText(activity,msg,Toast.LENGTH_LONG).show());
            } catch(Exception e) {
                final String msg="Lỗi lưu báo cáo: "+e.getMessage();
                activity.runOnUiThread(() -> Toast.makeText(activity,msg,Toast.LENGTH_LONG).show());
            }
        }
    }
}
