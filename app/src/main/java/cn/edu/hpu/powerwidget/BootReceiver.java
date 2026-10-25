package cn.edu.hpu.powerwidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        AppLogger.init(context);
        if (PowerRefreshManager.hasWidgets(context)) {
            WidgetJobScheduler.schedule(context);
            WidgetJobScheduler.requestImmediateRefresh(context);
        }
        BackgroundUpdateScheduler.schedule(context);
    }
}