package vn.viettel.caobang.kpitammi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ReportScheduler.scheduleNext0730(context);
    }
}
