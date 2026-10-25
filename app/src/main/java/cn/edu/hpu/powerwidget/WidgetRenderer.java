package cn.edu.hpu.powerwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import java.text.DecimalFormat;

/** Renders the last successful direct-download snapshot into the home-screen widget. */
final class WidgetRenderer {
    private WidgetRenderer() {}

    static void updateAll(Context context, String stateText, boolean error) {
        Room room = RoomStore.current(context);
        PowerCache.Snapshot snapshot = PowerCache.read(context, room);
        UsageResult data = snapshot.data;
        boolean failed = error || !snapshot.lastError.isEmpty();

        String status = stateText;
        if (status == null || status.trim().isEmpty()) {
            if (data == null) {
                status = failed ? "连接失败 · 暂无缓存" : "等待首次更新";
            } else if (!snapshot.lastError.isEmpty()) {
                status = "连接失败 · 缓存 " + PowerDates.dateTime(data.fetchedAt);
            } else {
                status = "更新于 " + PowerDates.time(data.fetchedAt);
            }
        }

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_power);
        String roomName = room == null ? "未选择寝室"
                : room.displayName() + " · " + room.campusLabel();
        views.setTextViewText(R.id.widget_room, roomName);
        views.setTextViewText(R.id.widget_lighting,
                data == null ? "-- 度" : number(data.lightingKwh) + " 度");
        views.setTextViewText(R.id.widget_air,
                data == null ? "-- 度" : number(data.airKwh) + " 度");
        views.setTextViewText(R.id.widget_today,
                data == null ? "今日 -- 度 · ¥--"
                        : "今日 " + kwh2(data.todayKwh) + " 度 · ¥" + money(data.todayCost));
        views.setTextViewText(R.id.widget_yesterday,
                data == null ? "-- 元" : money(data.yesterdayCost) + " 元");
        views.setTextViewText(R.id.widget_week,
                data == null ? "-- 元" : money(data.sevenDayCost) + " 元");
        views.setTextViewText(R.id.widget_latest,
                data == null ? "-- 元" : money(data.latestCost) + " 元");
        views.setTextViewText(R.id.widget_latest_date,
                data == null || blank(data.latestDate) ? "最新 --" : "最新 " + shortDate(data.latestDate));
        views.setTextViewText(R.id.widget_status, status);
        views.setTextColor(R.id.widget_status,
                context.getColor(failed ? R.color.widget_danger : R.color.widget_muted));
        views.setViewVisibility(R.id.widget_refresh, View.VISIBLE);

        Intent settingsIntent = new Intent(context, SettingsActivity.class);
        settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent settingsPending = PendingIntent.getActivity(
                context,
                471,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_root, settingsPending);

        Intent refreshIntent = new Intent(context, PowerWidgetProvider.class);
        refreshIntent.setAction(PowerWidgetProvider.ACTION_REFRESH);
        PendingIntent refreshPending = PendingIntent.getBroadcast(
                context,
                472,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending);
        views.setContentDescription(R.id.widget_root,
                (room == null ? "未选择寝室" : room.displayName())
                        + "用电，点按打开详情");

        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, PowerWidgetProvider.class));
        for (int id : ids) manager.updateAppWidget(id, views);
        updateAllSmall(context, null, error);
    }

    static void updateAllSmall(Context context, String stateText, boolean error) {
        Room room = RoomStore.current(context);
        PowerCache.Snapshot snapshot = PowerCache.read(context, room);
        UsageResult data = snapshot.data;
        String status = stateText;
        if (status == null || status.trim().isEmpty()) {
            status = data == null ? "等待首次更新" : "更新于 " + PowerDates.time(data.fetchedAt);
        }
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_power_small);
        views.setTextViewText(R.id.widget_small_room,
                room == null ? "未选择寝室" : room.displayName() + " · " + room.campusLabel());
        views.setTextViewText(R.id.widget_small_lighting,
                data == null ? "照明 --" : "照明 " + number(data.lightingKwh) + " 度");
        views.setTextViewText(R.id.widget_small_air,
                data == null ? "空调 --" : "空调 " + number(data.airKwh) + " 度");
        views.setTextColor(R.id.widget_small_room,
                context.getColor(failed(context, snapshot) ? R.color.widget_danger : R.color.widget_text));
        Intent settingsIntent = new Intent(context, SettingsActivity.class);
        settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent settingsPending = PendingIntent.getActivity(context, 4713, settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_small_root, settingsPending);
        Intent refreshIntent = new Intent(context, PowerWidgetSmallProvider.class);
        refreshIntent.setAction(PowerWidgetSmallProvider.ACTION_REFRESH);
        PendingIntent refreshPending = PendingIntent.getBroadcast(context, 4714, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_small_refresh, refreshPending);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, PowerWidgetSmallProvider.class));
        for (int id : ids) manager.updateAppWidget(id, views);
    }

    private static boolean failed(Context context, PowerCache.Snapshot snapshot) {
        return snapshot.lastError != null && !snapshot.lastError.isEmpty();
    }

    static String kwh2(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "--";
        return new DecimalFormat("0.00").format(value);
    }

    static String money(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "--";
        return new DecimalFormat("0.###").format(value);
    }

    static String number(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "--";
        return new DecimalFormat("0.##").format(value);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty() || "null".equals(value);
    }

    private static String shortDate(String value) {
        if (value != null && value.matches("\\d{4}-\\d{2}-\\d{2}")) return value.substring(5);
        return "--";
    }
}
