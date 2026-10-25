package cn.edu.hpu.powerwidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

public final class PowerWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "cn.edu.hpu.powerwidget.ACTION_REFRESH";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        WidgetJobScheduler.schedule(context);
        WidgetRenderer.updateAll(context, null, false);
        if (appWidgetIds != null && appWidgetIds.length > 0) {
            WidgetJobScheduler.requestImmediateRefresh(context);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent == null ? null : intent.getAction();
        if (!ACTION_REFRESH.equals(action)) return;

        if (!PowerRefreshManager.allowManualRequest(context)) {
            WidgetRenderer.updateAll(context, "刚刚更新过 · 请稍候", false);
            return;
        }
        if (PowerRefreshManager.refresh(context)) {
            WidgetRenderer.updateAll(context, "正在更新…", false);
        } else {
            WidgetRenderer.updateAll(context, "正在更新 · 请稍候", false);
        }
    }

    @Override
    public void onEnabled(Context context) {
        WidgetJobScheduler.schedule(context);
        WidgetJobScheduler.requestImmediateRefresh(context);
    }

    @Override
    public void onDisabled(Context context) {
        WidgetJobScheduler.cancel(context);
    }
}