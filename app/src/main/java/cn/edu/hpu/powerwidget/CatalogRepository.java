package cn.edu.hpu.powerwidget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;

/** Bundled-first catalog repository with a first-launch online attempt. */
final class CatalogRepository {
    interface Callback { void onLoaded(RoomCatalog catalog, boolean refreshed, String message); }
    private static final Object LOCK = new Object();
    private static boolean downloading;
    private CatalogRepository() {}

    static boolean isDownloading() { synchronized (LOCK) { return downloading; } }

    static RoomCatalog bundled(Context context) {
        try { return RoomCatalog.fromAsset(context); }
        catch (IOException | JSONException error) {
            AppLogger.error("读取内置寝室目录失败: " + error.getMessage());
            return new RoomCatalog(1, "", new ArrayList<Room>());
        }
    }

    static void loadFirstLaunch(final Context supplied, final Callback callback) {
        final Context context = supplied.getApplicationContext();
        if (isDownloading()) { deliver(callback, bundled(context), false, "正在加载寝室列表"); return; }
        synchronized (LOCK) { downloading = true; }
        new Thread(new Runnable() { @Override public void run() {
            long deadline = System.currentTimeMillis() + 5000L;
            // The school site has no one-shot catalog API. This bounded GET verifies reachability;
            // the complete pre-fetched catalog remains available offline if it times out.
            boolean reachable = false;
            try { reachable = SchoolPowerClient.checkCatalogSource(SchoolPowerClient.Campus.NORTH, (int) Math.max(1000L, deadline - System.currentTimeMillis())); }
            catch (Exception error) { AppLogger.info("首次联网拉取寝室列表失败: " + error.getMessage()); }
            RoomCatalog catalog = bundled(context);
            if (reachable) RoomStore.markCatalogRefresh(context, catalog.entries.size());
            deliver(callback, catalog, reachable, reachable
                    ? "寝室列表已联网校验" : "联网拉取超时，已使用内置寝室列表");
        }}, "room-catalog-first-launch").start();
    }

    static void refresh(final Context supplied, final Callback callback) {
        final Context context = supplied.getApplicationContext();
        if (isDownloading()) { deliver(callback, bundled(context), false, "正在拉取寝室列表，请稍候"); return; }
        synchronized (LOCK) { downloading = true; }
        AppLogger.info("手动拉取寝室列表开始");
        new Thread(new Runnable() { @Override public void run() {
            boolean north = false, south = false;
            try { north = SchoolPowerClient.checkCatalogSource(SchoolPowerClient.Campus.NORTH, 15000); } catch (Exception ignored) {}
            try { south = SchoolPowerClient.checkCatalogSource(SchoolPowerClient.Campus.SOUTH, 15000); } catch (Exception ignored) {}
            RoomCatalog catalog = bundled(context);
            boolean success = north || south;
            if (success) RoomStore.markCatalogRefresh(context, catalog.entries.size());
            String message = success ? "寝室列表已联网校验（" + catalog.entries.size() + " 个寝室）"
                    : "寝室列表拉取失败，已保留内置列表";
            AppLogger.info("手动拉取寝室列表结束: " + message);
            deliver(callback, catalog, success, message);
        }}, "room-catalog-refresh").start();
    }

    private static void deliver(final Callback callback, final RoomCatalog catalog,
            final boolean refreshed, final String message) {
        new Handler(Looper.getMainLooper()).post(new Runnable() { @Override public void run() {
            synchronized (LOCK) { downloading = false; }
            if (callback != null) callback.onLoaded(catalog, refreshed, message);
        }});
    }
}
