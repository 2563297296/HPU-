package cn.edu.hpu.powerwidget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/** Coordinates direct school-site refreshes for the app and home-screen widget. */
final class PowerRefreshManager {
    interface Callback {
        void onComplete(boolean success, String message);
    }

    private static final String TAG = "PowerRefresh";
    private static final String PREFS_NAME = "power_refresh_meta";
    private static final String KEY_LAST_MANUAL_REQUEST = "last_manual_request_at";
    private static final String KEY_LAST_MANUAL_SUCCESS = "last_manual_success";
    private static final String KEY_QUERY_ALL = "query_all_rooms";
    private static final long MANUAL_COOLDOWN_MS = 60_000L;
    private static final Object LOCK = new Object();

    private static boolean running;
    private static boolean manualRequestStarted;

    private PowerRefreshManager() {}

    static boolean isRefreshing() {
        synchronized (LOCK) {
            return running;
        }
    }

    /**
     * Applies the user-facing refresh cooldown. Background jobs do not call this method.
     */
    static boolean allowManualRequest(Context context) {
        Context app = context.getApplicationContext();
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            if (running) return false;
            SharedPreferences prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long previous = prefs.getLong(KEY_LAST_MANUAL_REQUEST, 0L);
            boolean lastSuccess = prefs.getBoolean(KEY_LAST_MANUAL_SUCCESS, true);
            // 上次手动查询失败时不设冷却，允许立刻再次查询。
            if (lastSuccess && now >= previous && now - previous < MANUAL_COOLDOWN_MS) return false;
            prefs.edit().putLong(KEY_LAST_MANUAL_REQUEST, now).apply();
            manualRequestStarted = true;
            return true;
        }
    }

    static void setQueryAll(Context context, boolean value) {
        context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_QUERY_ALL, value).apply();
    }

    static boolean queryAll(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_QUERY_ALL, false);
    }

    static boolean refresh(Context context) {
        return refresh(context, null, false);
    }

    static boolean refresh(Context context, Callback callback) {
        return refresh(context, callback, false);
    }

    /**
     * Starts one direct HTTPS refresh. The callback is always delivered on the main thread.
     */
    static boolean refresh(Context suppliedContext, final Callback callback, final boolean forceQueryAll) {
        final Context context = suppliedContext.getApplicationContext();
        final boolean manual;
        synchronized (LOCK) {
            if (running) return false;
            running = true;
            manual = manualRequestStarted;
            manualRequestStarted = false;
        }

        PowerCache.markAttempt(context);
        final Handler main = new Handler(Looper.getMainLooper());
        main.post(new Runnable() {
            @Override
            public void run() {
                if (hasWidgets(context)) {
                    WidgetRenderer.updateAll(context, "正在更新…", false);
                }
            }
        });

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean success = false;
                String message;
                final boolean queryAll = forceQueryAll || queryAll(context);
                final List<Room> rooms;
                if (queryAll) {
                    rooms = RoomStore.rooms(context);
                } else {
                    Room cur = RoomStore.current(context);
                    rooms = new ArrayList<>();
                    if (cur != null) rooms.add(cur);
                }
                try {
                    if (rooms == null || rooms.isEmpty()) throw new PowerSiteException("请先选择寝室");
                    int okCount = 0;
                    String firstError = null;
                    UsageResult lastResult = null;
                    for (Room room : rooms) {
                        try {
                            AppLogger.info("开始查询用电 · " + room.displayName());
                            UsageResult result = SchoolPowerClient.fetch(room);
                            String today = PowerDates.today();
                            String startDate = PowerCache.dayStartDate(context, room);
                            double startLight = PowerCache.dayStartLighting(context, room);
                            double startAir = PowerCache.dayStartAir(context, room);
                            if (!today.equals(startDate) || startLight < 0 || startAir < 0
                                    || result.lightingKwh > startLight || result.airKwh > startAir) {
                                startLight = result.lightingKwh;
                                startAir = result.airKwh;
                                PowerCache.saveDayStart(context, room, startLight, startAir, today);
                            }
                            double dLight = startLight > result.lightingKwh ? startLight - result.lightingKwh : 0;
                            double dAir = startAir > result.airKwh ? startAir - result.airKwh : 0;
                            double todayKwh = PowerMath.round4(dLight + dAir);
                            double unitPrice = result.records.isEmpty() ? 0 : result.records.get(0).unitPrice;
                            double todayCost = PowerMath.round2(todayKwh * unitPrice);
                            result = result.withToday(todayKwh, todayCost);
                            PowerCache.saveSuccess(context, room, result);
                            lastResult = result;
                            AppLogger.info("用电查询完成 · " + room.displayName() + " · 照明 " + WidgetRenderer.number(result.lightingKwh)
                                    + " 度 · 空调 " + WidgetRenderer.number(result.airKwh) + " 度");
                            okCount++;
                        } catch (Exception e) {
                            if (firstError == null) firstError = friendlyError(e);
                            AppLogger.error("刷新失败 " + room.displayName() + ": " + friendlyError(e));
                        }
                    }
                    if (okCount == 0) throw new PowerSiteException(firstError == null ? "查询失败" : firstError);
                    Room current = RoomStore.current(context);
                    if (current != null && DetailRangeStore.remembered(context)) {
                        try {
                            String[] range = DetailRangeStore.computeRange(context);
                            UsageResult detail = SchoolPowerClient.fetch(current, range[0], range[1]);
                            PowerCache.saveDetail(context, current, detail);
                        } catch (Exception ignored) {
                            // 明细预取失败不影响主数据更新。
                        }
                    }
                    success = true;
                    message = queryAll
                            ? "更新完成 · " + okCount + "/" + rooms.size() + " 个寝室"
                            : "更新完成 · 照明 " + WidgetRenderer.number(lastResult.lightingKwh)
                                    + " 度 · 空调 " + WidgetRenderer.number(lastResult.airKwh) + " 度";
                } catch (Exception error) {
                    message = friendlyError(error);
                    Log.w(TAG, "直连学校站点失败: " + error.getClass().getSimpleName());
                    AppLogger.error("刷新失败: " + friendlyError(error));
                    PowerCache.saveFailure(context, message);
                }

                final boolean finishedSuccessfully = success;
                final String finishedMessage = message;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (LOCK) {
                            running = false;
                            if (manual) {
                                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                        .edit().putBoolean(KEY_LAST_MANUAL_SUCCESS, finishedSuccessfully)
                                        .apply();
                            }
                        }
                        if (hasWidgets(context)) {
                            WidgetRenderer.updateAll(context, null, !finishedSuccessfully);
                        }
                        if (callback != null) {
                            callback.onComplete(finishedSuccessfully, finishedMessage);
                        }
                    }
                });
            }
        }, "hpu-power-direct");
        worker.start();
        return true;
    }

    static boolean hasWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, PowerWidgetProvider.class));
        int[] small = manager.getAppWidgetIds(new ComponentName(context, PowerWidgetSmallProvider.class));
        return (ids != null && ids.length > 0) || (small != null && small.length > 0);
    }

    private static String friendlyError(Exception error) {
        String detail = error.getMessage();
        if (detail == null || detail.trim().isEmpty()) {
            if (error instanceof PowerSiteException) return "学校站点查询失败";
            if (error instanceof IllegalStateException) return "保存本地缓存失败";
            return "查询失败，请检查网络后重试";
        }
        return detail.trim();
    }
}