package cn.edu.hpu.powerwidget;

import android.content.Context;
import android.content.SharedPreferences;

final class DetailRangeStore {
    static final int MODE_DAYS = 1;
    static final int MODE_RANGE = 2;

    private static final String PREFS = "detail_range";
    private static final String KEY_MODE = "mode";
    private static final String KEY_DAYS = "days";
    private static final String KEY_START = "start";
    private static final String KEY_END = "end";
    private static final String KEY_REMEMBERED = "remembered";
    private static final String KEY_CONFIGURED = "configured";

    private DetailRangeStore() {}

    static int mode(Context ctx) { return prefs(ctx).getInt(KEY_MODE, MODE_DAYS); }
    static int days(Context ctx) { return prefs(ctx).getInt(KEY_DAYS, 90); }
    static String start(Context ctx) { return prefs(ctx).getString(KEY_START, ""); }
    static String end(Context ctx) { return prefs(ctx).getString(KEY_END, ""); }
    static boolean remembered(Context ctx) { return prefs(ctx).getBoolean(KEY_REMEMBERED, false); }
    static boolean configured(Context ctx) { return prefs(ctx).getBoolean(KEY_CONFIGURED, false); }

    static void save(Context ctx, int mode, int days, String start, String end, boolean remembered) {
        prefs(ctx).edit().putInt(KEY_MODE, mode).putInt(KEY_DAYS, days)
                .putString(KEY_START, start == null ? "" : start)
                .putString(KEY_END, end == null ? "" : end)
                .putBoolean(KEY_REMEMBERED, remembered)
                .putBoolean(KEY_CONFIGURED, true).apply();
    }

    static void resetPrompt(Context ctx) { prefs(ctx).edit().putBoolean(KEY_REMEMBERED, false).apply(); }
    static void reset(Context ctx) { prefs(ctx).edit().clear().apply(); }

    static String[] computeRange(Context ctx) throws PowerSiteException {
        String today = PowerDates.today();
        if (mode(ctx) == MODE_RANGE) {
            String s = start(ctx);
            String e = end(ctx);
            if (s == null || s.isEmpty() || e == null || e.isEmpty()) {
                s = PowerDates.shiftDays(today, -89);
                e = today;
            }
            if (s.compareTo(e) > 0) { String t = s; s = e; e = t; }
            return new String[] {s, e};
        }
        int days = days(ctx);
        if (days < 1) days = 1;
        String s = PowerDates.shiftDays(today, -(days - 1));
        return new String[] {s, today};
    }

    static String describe(Context ctx) {
        try {
            String[] r = computeRange(ctx);
            return (mode(ctx) == MODE_RANGE ? "固定日期" : "最近 " + days(ctx) + " 天")
                    + " · " + r[0] + " 至 " + r[1];
        } catch (Exception error) {
            return "最近 90 天";
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
