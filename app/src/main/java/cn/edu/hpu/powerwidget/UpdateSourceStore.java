package cn.edu.hpu.powerwidget;

import android.content.Context;
import android.content.SharedPreferences;

final class UpdateSourceStore {
    private static final String PREFS = "update_source";
    private static final String KEY_URL = "url";

    private UpdateSourceStore() {}

    static String url(Context ctx) { return prefs(ctx).getString(KEY_URL, ""); }
    static void setUrl(Context ctx, String url) { prefs(ctx).edit().putString(KEY_URL, url == null ? "" : url.trim()).apply(); }

    private static SharedPreferences prefs(Context ctx) { return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
}
