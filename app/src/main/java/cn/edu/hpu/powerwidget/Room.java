package cn.edu.hpu.powerwidget;

import org.json.JSONException;
import org.json.JSONObject;

/** One selectable room and the exact school-site meter option values. */
final class Room {
    final String id;
    final String campus;
    final String name;
    final String building;
    final String floor;
    final String lightingBuilding;
    final String lightingFloor;
    final String lightingRoom;
    final String airBuilding;
    final String airFloor;
    final String airRoom;

    Room(String id, String campus, String name, String building, String floor,
         String lightingBuilding, String lightingFloor, String lightingRoom,
         String airBuilding, String airFloor, String airRoom) {
        this.id = safe(id);
        this.campus = safe(campus);
        this.name = display(name);
        this.building = display(building);
        this.floor = display(floor);
        this.lightingBuilding = safe(lightingBuilding);
        this.lightingFloor = safe(lightingFloor);
        this.lightingRoom = safe(lightingRoom);
        this.airBuilding = safe(airBuilding);
        this.airFloor = safe(airFloor);
        this.airRoom = safe(airRoom);
    }

    static Room fromJson(JSONObject root) throws JSONException {
        return new Room(root.getString("id"), root.getString("campus"), root.getString("name"),
                root.getString("building"), root.getString("floor"),
                root.getJSONObject("lighting").getString("building"),
                root.getJSONObject("lighting").getString("floor"),
                root.getJSONObject("lighting").getString("room"),
                root.getJSONObject("air").getString("building"),
                root.getJSONObject("air").getString("floor"),
                root.getJSONObject("air").getString("room"));
    }

    JSONObject toJson() throws JSONException {
        JSONObject lighting = new JSONObject();
        lighting.put("building", lightingBuilding);
        lighting.put("floor", lightingFloor);
        lighting.put("room", lightingRoom);
        JSONObject air = new JSONObject();
        air.put("building", airBuilding);
        air.put("floor", airFloor);
        air.put("room", airRoom);
        JSONObject root = new JSONObject();
        root.put("id", id); root.put("campus", campus); root.put("name", name);
        root.put("building", building); root.put("floor", floor);
        root.put("lighting", lighting); root.put("air", air);
        return root;
    }

    String displayName() { return name.isEmpty() ? "未选择寝室" : name; }
    String campusLabel() { return "south".equals(campus) ? "南校区" : "北校区"; }
    static String safeFileToken(String value) {
        String token = value == null ? "" : value.replaceAll("[^A-Za-z0-9_-]", "_");
        return token.length() > 80 ? token.substring(0, 80) : token;
    }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static String display(String value) { return value == null ? "" : value.trim(); }
}
