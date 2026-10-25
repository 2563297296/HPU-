package cn.edu.hpu.powerwidget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Remote announcement / configuration payload pushed from the server. */
final class RemoteConfig {
    final List<Announcement> announcements;
    final String notice;
    final String latestVersion;
    final int latestVersionCode;
    final boolean forceUpdate;
    final String downloadUrl;

    RemoteConfig(List<Announcement> announcements, String notice, String latestVersion,
                 int latestVersionCode, boolean forceUpdate, String downloadUrl) {
        this.announcements = Collections.unmodifiableList(new ArrayList<>(announcements));
        this.notice = notice == null ? "" : notice;
        this.latestVersion = latestVersion == null ? "" : latestVersion;
        this.latestVersionCode = latestVersionCode;
        this.forceUpdate = forceUpdate;
        this.downloadUrl = downloadUrl == null ? "" : downloadUrl;
    }

    Announcement latest() {
        return announcements.isEmpty() ? null : announcements.get(0);
    }

    static RemoteConfig parse(String json) throws JSONException {
        if (json == null || json.trim().isEmpty()) {
            return new RemoteConfig(new ArrayList<Announcement>(), "", "", 0, false, "");
        }
        JSONObject root = new JSONObject(json);
        JSONArray arr = root.optJSONArray("announcements");
        List<Announcement> list = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Announcement(
                        o.optString("id", ""),
                        o.optString("title", ""),
                        o.optString("content", ""),
                        o.optString("time", ""),
                        o.optString("level", "info")));
            }
        }
        return new RemoteConfig(list, root.optString("notice", ""),
                root.optString("latestVersion", ""),
                root.optInt("latestVersionCode", 0),
                root.optBoolean("forceUpdate", false),
                root.optString("downloadUrl", ""));
    }

    static final class Announcement {
        final String id;
        final String title;
        final String content;
        final String time;
        final String level;

        Announcement(String id, String title, String content, String time, String level) {
            this.id = id == null ? "" : id;
            this.title = title == null ? "" : title;
            this.content = content == null ? "" : content;
            this.time = time == null ? "" : time;
            this.level = level == null ? "info" : level;
        }
    }
}
