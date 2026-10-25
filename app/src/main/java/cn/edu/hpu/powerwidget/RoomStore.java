package cn.edu.hpu.powerwidget;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** Persistently stores selected rooms and the active room. */
final class RoomStore {
    private static final String PREFS_NAME = "room_store";
    private static final String KEY_ROOMS = "rooms";
    private static final String KEY_CURRENT = "current_room_id";
    private static final String KEY_CATALOG_AT = "catalog_refreshed_at";
    private static final String KEY_CATALOG_COUNT = "catalog_entry_count";
    private static final String KEY_ONBOARDING_DONE = "onboarding_done";
    private static final String KEY_APP_ONBOARDED = "app_onboarded";

    private RoomStore() {}

    static List<Room> rooms(Context context) {
        List<Room> result = new ArrayList<>();
        try {
            JSONArray rows = new JSONArray(prefs(context).getString(KEY_ROOMS, "[]"));
            for (int i = 0; i < rows.length(); i++) {
                Room room = Room.fromJson(rows.getJSONObject(i));
                if (!room.id.isEmpty()) result.add(room);
            }
        } catch (JSONException ignored) {
            AppLogger.error("已保存寝室列表解析失败");
        }
        return result;
    }

    static boolean addRoom(Context context, Room room) {
        if (room == null || room.id.isEmpty()) return false;
        List<Room> current = rooms(context);
        for (Room item : current) if (item.id.equals(room.id)) return false;
        current.add(room); saveRooms(context, current, room.id); return true;
    }

    static void removeRoom(Context context, String roomId) {
        List<Room> current = rooms(context), next = new ArrayList<>();
        for (Room room : current) if (!room.id.equals(roomId)) next.add(room);
        String currentId = currentRoomId(context);
        saveRooms(context, next, currentId.equals(roomId) && !next.isEmpty() ? next.get(0).id
                : currentId.equals(roomId) ? "" : currentId);
    }

    static void setCurrent(Context context, String roomId) { saveRooms(context, rooms(context), roomId); }
    static Room current(Context context) {
        String id = currentRoomId(context);
        for (Room room : rooms(context)) if (room.id.equals(id)) return room;
        return null;
    }
    static String currentRoomId(Context context) { return prefs(context).getString(KEY_CURRENT, ""); }
    static boolean hasSelection(Context context) { return current(context) != null; }
    static boolean onboardingDone(Context context) { return prefs(context).getBoolean(KEY_ONBOARDING_DONE, false); }
    static void setOnboardingDone(Context context, boolean done) { prefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, done).apply(); }
    static boolean appOnboarded(Context context) { return prefs(context).getBoolean(KEY_APP_ONBOARDED, false); }
    static void setAppOnboarded(Context context, boolean done) { prefs(context).edit().putBoolean(KEY_APP_ONBOARDED, done).apply(); }
    static void markCatalogRefresh(Context context, int count) { prefs(context).edit().putLong(KEY_CATALOG_AT, System.currentTimeMillis()).putInt(KEY_CATALOG_COUNT, count).apply(); }
    static long catalogRefreshedAt(Context context) { return prefs(context).getLong(KEY_CATALOG_AT, 0L); }
    static int catalogEntryCount(Context context) { return prefs(context).getInt(KEY_CATALOG_COUNT, 0); }
    private static void saveRooms(Context context, List<Room> rooms, String currentId) {
        JSONArray rows = new JSONArray();
        for (Room room : rooms) try { rows.put(room.toJson()); } catch (JSONException ignored) {}
        prefs(context).edit().putString(KEY_ROOMS, rows.toString()).putString(KEY_CURRENT, currentId == null ? "" : currentId).apply();
    }
    private static SharedPreferences prefs(Context context) { return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE); }
}
