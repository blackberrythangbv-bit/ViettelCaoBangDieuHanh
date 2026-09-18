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
        String publicUri = intent.getStringExtra("public_zip_uri");
        String legacyPath = intent.getStringExtra("public_legacy_path");

        if (internalPath != null && !internalPath.isEmpty()) {
            try {
                File f = new File(internalPath);
                if (f.exists()) f.delete();
            } catch (Exception ignored) {}
        }

        if (publicUri != null && !publicUri.isEmpty()) {
            try {
                context.getContentResolver().delete(Uri.parse(publicUri), null, null);
            } catch (Exception ignored) {}
        }

        if (legacyPath != null && !legacyPath.isEmpty()) {
            try {
                File f = new File(legacyPath);
                if (f.exists()) f.delete();
            } catch (Exception ignored) {}
        }

        context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE).edit()
                .putString("last_message", "ZIP báo cáo đã tự xóa sau 24 giờ. File Excel/PNG giải nén vẫn được giữ để gửi Tammi.")
                .remove("last_zip")
                .remove("last_public_zip_uri")
                .remove("last_public_zip_legacy_path")
                .apply();
    }
}
