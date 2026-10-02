package vn.viettel.caobang.kpitammi.v2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class DownloadReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                java.io.File f = ApiClient.downloadLatest(app);
                Scheduler.prefs(app).edit()
                        .putLong("last_auto_ok", System.currentTimeMillis())
                        .putString("last_file", f.getAbsolutePath())
                        .apply();
            } catch (Exception e) {
                Scheduler.prefs(app).edit().putString("last_auto_error", e.getMessage()).apply();
            } finally {
                pending.finish();
            }
        }, "KPI-Tammi-Auto").start();
    }
}
