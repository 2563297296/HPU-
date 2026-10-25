package cn.edu.hpu.powerwidget;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Durable local cache. A failed refresh never replaces the last successful snapshot. */
final class PowerCache {
    static final String ACTION_DATA_CHANGED = "cn.edu.hpu.powerwidget.DATA_CHANGED";

    private static final String FILE_PREFIX = "power_cache_";
    private static final String FILE_SUFFIX = ".json";
    private static final String PREFS_NAME = "power_cache_meta";
    private static final String KEY_LAST_ATTEMPT = "last_attempt_at";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final Object LOCK = new Object();

    private PowerCache() {}

    static final class Snapshot {
        final UsageResult data;
        final long lastAttemptAt;
        final String lastError;

        Snapshot(UsageResult data, long lastAttemptAt, String lastError) {
            this.data = data;
            this.lastAttemptAt = lastAttemptAt;
            this.lastError = lastError == null ? "" : lastError;
        }

        boolean hasData() {
            return data != null;
        }
    }

    static Snapshot read(Context context) {
        synchronized (LOCK) {
            Room room = RoomStore.current(context);
            return read(context, room);
        }
    }

    static Snapshot read(Context context, Room room) {
        synchronized (LOCK) {
            SharedPreferences prefs = prefs(context);
            UsageResult data = room == null ? null : readData(context, room);
            return new Snapshot(
                    data,
                    prefs.getLong(KEY_LAST_ATTEMPT, 0L),
                    prefs.getString(KEY_LAST_ERROR, "")
            );
        }
    }

    static void markAttempt(Context context) {
        synchronized (LOCK) {
            prefs(context).edit()
                    .putLong(KEY_LAST_ATTEMPT, System.currentTimeMillis())
                    .apply();
        }
    }

    static void saveSuccess(Context context, Room room, UsageResult result) {
        synchronized (LOCK) {
            writeAtomically(context, room, result);
            prefs(context).edit()
                    .putLong(KEY_LAST_ATTEMPT, System.currentTimeMillis())
                    .putString(KEY_LAST_ERROR, "")
                    .apply();
        }
        notifyChanged(context);
    }

    static void saveFailure(Context context, String message) {
        synchronized (LOCK) {
            prefs(context).edit()
                    .putLong(KEY_LAST_ATTEMPT, System.currentTimeMillis())
                    .putString(KEY_LAST_ERROR, message == null ? "查询失败" : message)
                    .apply();
        }
        notifyChanged(context);
    }

    static void notifyChanged(Context context) {
        Intent changed = new Intent(ACTION_DATA_CHANGED);
        changed.setPackage(context.getPackageName());
        context.sendBroadcast(changed);
    }

    private static UsageResult readData(Context context, Room room) {
        File file = cacheFile(context, room);
        if (!file.isFile()) return null;
        try {
            byte[] bytes = readFile(file);
            JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            return UsageResult.fromJson(root);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void writeAtomically(Context context, Room room, UsageResult result) {
        File target = cacheFile(context, room);
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        FileOutputStream output = null;
        try {
            byte[] bytes = result.toJson().toString().getBytes(StandardCharsets.UTF_8);
            output = new FileOutputStream(temporary, false);
            output.write(bytes);
            output.flush();
            output.getFD().sync();
            output.close();
            output = null;
            if (!temporary.renameTo(target)) {
                throw new IOException("无法替换本地缓存");
            }
        } catch (Exception error) {
            if (output != null) {
                try { output.close(); } catch (IOException ignored) {}
            }
            temporary.delete();
            throw new IllegalStateException("保存用电缓存失败", error);
        }
    }

    private static byte[] readFile(File file) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static File cacheFile(Context context, Room room) {
        String token = room == null || room.id.isEmpty() ? "default" : Room.safeFileToken(room.id);
        return new File(context.getFilesDir(), FILE_PREFIX + token + FILE_SUFFIX);
    }

    static UsageResult readDetailData(Context context, Room room) {
        synchronized (LOCK) {
            File file = detailFile(context, room);
            if (!file.isFile()) return null;
            try {
                byte[] bytes = readFile(file);
                return UsageResult.fromJson(new JSONObject(new String(bytes, StandardCharsets.UTF_8)));
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    static void saveDetail(Context context, Room room, UsageResult result) {
        synchronized (LOCK) {
            try {
                writeStringAtomically(detailFile(context, room), result.toJson().toString());
            } catch (Exception ignored) {
                // Detail cache is best-effort and never blocks the main data path.
            }
        }
    }

    private static File detailFile(Context context, Room room) {
        String token = room == null || room.id.isEmpty() ? "default" : Room.safeFileToken(room.id);
        return new File(context.getFilesDir(), "detail_cache_" + token + FILE_SUFFIX);
    }

    private static void writeStringAtomically(File target, String content) throws IOException {
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        FileOutputStream output = null;
        try {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            output = new FileOutputStream(temporary, false);
            output.write(bytes);
            output.flush();
            output.getFD().sync();
            output.close();
            output = null;
            if (!temporary.renameTo(target)) throw new IOException("无法替换缓存");
        } finally {
            if (output != null) { try { output.close(); } catch (IOException ignored) {} }
            temporary.delete();
        }
    }

    static String dayStartDate(Context context, Room room) {
        return dayStartPrefs(context).getString(dayToken(room) + "_date", "");
    }

    static double dayStartLighting(Context context, Room room) {
        return parseDouble(dayStartPrefs(context).getString(dayToken(room) + "_lighting", ""));
    }

    static double dayStartAir(Context context, Room room) {
        return parseDouble(dayStartPrefs(context).getString(dayToken(room) + "_air", ""));
    }

    static void saveDayStart(Context context, Room room, double lighting, double air, String date) {
        String token = dayToken(room);
        dayStartPrefs(context).edit()
                .putString(token + "_lighting", Double.toString(lighting))
                .putString(token + "_air", Double.toString(air))
                .putString(token + "_date", date)
                .apply();
    }

    private static SharedPreferences dayStartPrefs(Context context) {
        return context.getSharedPreferences("balance_day_start", Context.MODE_PRIVATE);
    }

    private static String dayToken(Room room) {
        return room == null || room.id.isEmpty() ? "default" : Room.safeFileToken(room.id);
    }

    private static double parseDouble(String value) {
        if (value == null || value.isEmpty()) return -1;
        try { return Double.parseDouble(value); } catch (NumberFormatException error) { return -1; }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}