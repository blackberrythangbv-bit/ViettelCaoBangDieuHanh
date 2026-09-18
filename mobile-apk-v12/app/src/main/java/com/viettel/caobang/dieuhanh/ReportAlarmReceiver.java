package com.viettel.caobang.dieuhanh;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReportAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : String.valueOf(intent.getAction());
        if (ReportScheduler.ACTION_DAILY.equals(action)) {
            ReportScheduler.scheduleNext(context);
        }

        Intent service = new Intent(context, ReportAutoService.class);
        service.putExtra("source", action);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
        else context.startService(service);
    }
}
