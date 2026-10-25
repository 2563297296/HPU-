package cn.edu.hpu.powerwidget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Parses a bundled or downloaded room catalog. */
final class RoomCatalog {
    final int version;
    final String capturedAt;
    final List<Room> entries;

    RoomCatalog(int version, String capturedAt, List<Room> entries) {
        this.version = version;
        this.capturedAt = capturedAt == null ? "" : capturedAt;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    static RoomCatalog parse(byte[] bytes) throws JSONException {
        JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        int version = root.getInt("version");
        if (version < 1) throw new JSONException("寝室目录版本无效");
        JSONArray rows = root.getJSONArray("entries");
        List<Room> rooms = new ArrayList<>(rows.length());
        for (int i = 0; i < rows.length(); i++) {
            Room room = Room.fromJson(rows.getJSONObject(i));
            if (!room.id.isEmpty() && !room.campus.isEmpty()) rooms.add(room);
        }
        JSONObject north = root.optJSONObject("sources") != null
                ? root.getJSONObject("sources").optJSONObject("north") : null;
        String capturedAt = north == null ? "" : north.optString("capturedAt", "");
        return new RoomCatalog(version, capturedAt, rooms);
    }

    static RoomCatalog fromAsset(android.content.Context context) throws java.io.IOException, JSONException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = context.getAssets().open("room_catalog.json")) {
            byte[] buffer = new byte[16384]; int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
        return parse(output.toByteArray());
    }

    List<String> buildings(String campus) {
        List<String> result = new ArrayList<>();
        for (Room room : entries) {
            if (room.campus.equals(campus) && !result.contains(room.building)) result.add(room.building);
        }
        return result;
    }

    List<Room> inBuilding(String campus, String building) {
        List<Room> result = new ArrayList<>();
        for (Room room : entries) if (room.campus.equals(campus) && room.building.equals(building)) result.add(room);
        return result;
    }

    Room byId(String id) { for (Room room : entries) if (room.id.equals(id)) return room; return null; }
}
