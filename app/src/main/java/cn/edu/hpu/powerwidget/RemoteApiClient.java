package cn.edu.hpu.powerwidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Fetches announcements/config from the configured update source URL. */
final class RemoteApiClient {
    interface Callback { void onLoaded(RemoteConfig config, String error); }

    private static final String PREFS = "remote_config";
    private static final String KEY_JSON = "cached_json";
    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_BYTES = 128 * 1024;

    private RemoteApiClient() {}

    static void fetch(final Context context, final Callback callback) {
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                RemoteConfig config = null;
                String error = null;
                try {
                    config = fetchOnce(app);
                    save(app, config);
                } catch (Exception e) {
                    error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    config = cached(app);
                }
                final RemoteConfig result = config;
                final String err = error;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override public void run() {
                        if (callback != null) callback.onLoaded(result, err);
                    }
                });
            }
        }, "remote-config").start();
    }

    static RemoteConfig cached(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_JSON, "");
        if (json.isEmpty()) return null;
        try { return RemoteConfig.parse(json); } catch (Exception e) { return null; }
    }

    private static RemoteConfig fetchOnce(Context context) throws Exception {
        String url = UpdateSourceStore.url(context);
        if (url.isEmpty()) throw new Exception("未设置更新源");
        byte[] bytes = getBytes(url);
        return RemoteConfig.parse(new String(bytes, StandardCharsets.UTF_8));
    }

    private static byte[] getBytes(String urlText) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlText).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "HPU-Power-Widget");
            connection.setInstanceFollowRedirects(true);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new Exception("HTTP " + status);
            try (InputStream input = connection.getInputStream()) {
                return readLimited(input);
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static byte[] readLimited(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read, total = 0;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BYTES) throw new Exception("响应过大");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void save(Context context, RemoteConfig config) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_JSON, toJson(config)).apply();
        } catch (Exception ignored) {}
    }

    private static String toJson(RemoteConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"announcements\":[");
        for (int i = 0; i < config.announcements.size(); i++) {
            RemoteConfig.Announcement a = config.announcements.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"id\":\"").append(esc(a.id)).append("\",\"title\":\"").append(esc(a.title))
              .append("\",\"content\":\"").append(esc(a.content)).append("\",\"time\":\"").append(esc(a.time))
              .append("\",\"level\":\"").append(esc(a.level)).append("\"}");
        }
        sb.append("],\"notice\":\"").append(esc(config.notice)).append("\",\"latestVersion\":\"")
          .append(esc(config.latestVersion)).append("\",\"latestVersionCode\":").append(config.latestVersionCode)
          .append(",\"forceUpdate\":").append(config.forceUpdate)
          .append(",\"downloadUrl\":\"").append(esc(config.downloadUrl)).append("\"}");
        return sb.toString();
    }

    private static String esc(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
