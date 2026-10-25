package cn.edu.hpu.powerwidget;

import android.content.Context;
import android.content.SharedPreferences;

final class AutoRefreshStore {
    private static final String PREFS = "auto_refresh";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_INTERVAL = "interval_minutes";

    private AutoRefreshStore() {}

    static boolean enabled(Context ctx) { return prefs(ctx).getBoolean(KEY_ENABLED, false); }
    static void setEnabled(Context ctx, boolean value) { prefs(ctx).edit().putBoolean(KEY_ENABLED, value).apply(); }
    static int intervalMinutes(Context ctx) { return prefs(ctx).getInt(KEY_INTERVAL, 60); }
    static void setIntervalMinutes(Context ctx, int value) { prefs(ctx).edit().putInt(KEY_INTERVAL, value).apply(); }

    private static SharedPreferences prefs(Context ctx) { return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
}
