package cn.edu.hpu.powerwidget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

final class DetailDataLoader {
    interface Callback { void onLoaded(UsageResult result, String error); }

    private DetailDataLoader() {}

    static void load(final Context context, final Room room, final String start, final String end, final Callback callback) {
        final Context app = context.getApplicationContext();
        UsageResult cached = PowerCache.readDetailData(app, room);
        if (cached != null && start.equals(cached.rangeStart) && end.equals(cached.rangeEnd)) {
            deliver(callback, cached, null);
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                AppLogger.info("查询明细 · " + room.displayName() + " · " + start + " 至 " + end);
                UsageResult result = null;
                String error = null;
                try {
                    result = SchoolPowerClient.fetch(room, start, end);
                    PowerCache.saveDetail(app, room, result);
                } catch (Exception e) {
                    error = friendly(e);
                    AppLogger.error("明细查询失败: " + error);
                }
                deliver(callback, result, error);
            }
        }, "detail-loader").start();
    }

    private static void deliver(final Callback callback, final UsageResult result, final String error) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                if (callback != null) callback.onLoaded(result, error);
            }
        });
    }

    private static String friendly(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message.trim();
    }
}
