package vn.viettel.caobang.kpitammi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int attempt = intent != null ? intent.getIntExtra("attempt", 0) : 0;
        AutoReportService.startNow(context, attempt, false);
    }
}
