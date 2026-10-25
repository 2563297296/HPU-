package cn.edu.hpu.powerwidget;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

final class LowBatteryStore {
    private static final String PREFS = "low_battery";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LIGHTING_THRESHOLD = "lighting_threshold";
    private static final String KEY_AIR_THRESHOLD = "air_threshold";
    private static final String KEY_LIGHTING = "lighting";
    private static final String KEY_AIR = "air";
    private static final String KEY_ROOMS = "rooms";

    private LowBatteryStore() {}

    static boolean enabled(Context ctx) { return prefs(ctx).getBoolean(KEY_ENABLED, false); }
    static void setEnabled(Context ctx, boolean value) { prefs(ctx).edit().putBoolean(KEY_ENABLED, value).apply(); }
    static double lightingThreshold(Context ctx) { return prefs(ctx).getFloat(KEY_LIGHTING_THRESHOLD, 10f); }
    static void setLightingThreshold(Context ctx, double value) { prefs(ctx).edit().putFloat(KEY_LIGHTING_THRESHOLD, (float) value).apply(); }
    static double airThreshold(Context ctx) { return prefs(ctx).getFloat(KEY_AIR_THRESHOLD, 10f); }
    static void setAirThreshold(Context ctx, double value) { prefs(ctx).edit().putFloat(KEY_AIR_THRESHOLD, (float) value).apply(); }
    static boolean lighting(Context ctx) { return prefs(ctx).getBoolean(KEY_LIGHTING, true); }
    static void setLighting(Context ctx, boolean value) { prefs(ctx).edit().putBoolean(KEY_LIGHTING, value).apply(); }
    static boolean air(Context ctx) { return prefs(ctx).getBoolean(KEY_AIR, true); }
    static void setAir(Context ctx, boolean value) { prefs(ctx).edit().putBoolean(KEY_AIR, value).apply(); }

    static List<String> roomIds(Context ctx) {
        List<String> result = new ArrayList<>();
        try {
            JSONArray rows = new JSONArray(prefs(ctx).getString(KEY_ROOMS, "[]"));
            for (int i = 0; i < rows.length(); i++) result.add(rows.getString(i));
        } catch (JSONException ignored) {}
        return result;
    }
    static void setRoomIds(Context ctx, List<String> ids) {
        JSONArray rows = new JSONArray();
        for (String id : ids) rows.put(id);
        prefs(ctx).edit().putString(KEY_ROOMS, rows.toString()).apply();
    }

    private static SharedPreferences prefs(Context ctx) { return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
}
