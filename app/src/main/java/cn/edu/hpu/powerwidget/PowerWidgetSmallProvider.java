package cn.edu.hpu.powerwidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

public final class PowerWidgetSmallProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "cn.edu.hpu.powerwidget.ACTION_REFRESH_SMALL";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        WidgetJobScheduler.schedule(context);
        WidgetRenderer.updateAllSmall(context, null, false);
        if (appWidgetIds != null && appWidgetIds.length > 0) WidgetJobScheduler.requestImmediateRefresh(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent == null ? null : intent.getAction();
        if (!ACTION_REFRESH.equals(action)) return;
        if (!PowerRefreshManager.allowManualRequest(context)) {
            WidgetRenderer.updateAllSmall(context, "刚刚更新过", false);
            return;
        }
        if (PowerRefreshManager.refresh(context)) WidgetRenderer.updateAllSmall(context, "正在更新…", false);
        else WidgetRenderer.updateAllSmall(context, "正在更新", false);
    }

    @Override
    public void onEnabled(Context context) {
        WidgetJobScheduler.schedule(context);
        WidgetJobScheduler.requestImmediateRefresh(context);
    }
}
