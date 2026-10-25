package cn.edu.hpu.powerwidget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class UsageResult {
    static final int CACHE_VERSION = 3;

    final String room;
    final String roomId;
    final String campus;
    final String rangeStart;
    final String rangeEnd;
    final double lightingKwh;
    final double airKwh;
    final double todayKwh;
    final double todayCost;
    final double yesterdayCost;
    final double sevenDayCost;
    final String latestDate;
    final double latestKwh;
    final double latestCost;
    final String highestDate;
    final double highestCost;
    final double totalKwh;
    final double totalCost;
    final double averageDailyCost;
    final int recordedDays;
    final int lightingPages;
    final int airPages;
    final long fetchedAt;
    final List<DailyUsage> daily;
    final List<UsageRecord> records;

    UsageResult(String room, String roomId, String campus, String rangeStart, String rangeEnd,
                double lightingKwh, double airKwh, double todayKwh,
                double todayCost, double yesterdayCost, double sevenDayCost,
                String latestDate, double latestKwh, double latestCost,
                String highestDate, double highestCost,
                double totalKwh, double totalCost, double averageDailyCost,
                int recordedDays, int lightingPages, int airPages, long fetchedAt,
                List<DailyUsage> daily, List<UsageRecord> records) {
        this.room = room;
        this.roomId = roomId == null ? "" : roomId;
        this.campus = campus == null ? "" : campus;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
        this.lightingKwh = lightingKwh;
        this.airKwh = airKwh;
        this.todayKwh = todayKwh;
        this.todayCost = todayCost;
        this.yesterdayCost = yesterdayCost;
        this.sevenDayCost = sevenDayCost;
        this.latestDate = latestDate;
        this.latestKwh = latestKwh;
        this.latestCost = latestCost;
        this.highestDate = highestDate;
        this.highestCost = highestCost;
        this.totalKwh = totalKwh;
        this.totalCost = totalCost;
        this.averageDailyCost = averageDailyCost;
        this.recordedDays = recordedDays;
        this.lightingPages = lightingPages;
        this.airPages = airPages;
        this.fetchedAt = fetchedAt;
        this.daily = Collections.unmodifiableList(new ArrayList<>(daily));
        this.records = Collections.unmodifiableList(new ArrayList<>(records));
    }

    UsageResult withToday(double newTodayKwh, double newTodayCost) {
        return new UsageResult(room, roomId, campus, rangeStart, rangeEnd,
                lightingKwh, airKwh, newTodayKwh,
                newTodayCost, yesterdayCost, sevenDayCost,
                latestDate, latestKwh, latestCost,
                highestDate, highestCost,
                totalKwh, totalCost, averageDailyCost,
                recordedDays, lightingPages, airPages, fetchedAt,
                daily, records);
    }

    JSONObject toJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("version", CACHE_VERSION);
        root.put("room", room);
        root.put("roomId", roomId);
        root.put("campus", campus);
        root.put("rangeStart", rangeStart);
        root.put("rangeEnd", rangeEnd);
        root.put("lightingKwh", lightingKwh);
        root.put("airKwh", airKwh);
        root.put("todayKwh", todayKwh);
        root.put("todayCost", todayCost);
        root.put("yesterdayCost", yesterdayCost);
        root.put("sevenDayCost", sevenDayCost);
        root.put("latestDate", latestDate == null ? JSONObject.NULL : latestDate);
        root.put("latestKwh", latestKwh);
        root.put("latestCost", latestCost);
        root.put("highestDate", highestDate == null ? JSONObject.NULL : highestDate);
        root.put("highestCost", highestCost);
        root.put("totalKwh", totalKwh);
        root.put("totalCost", totalCost);
        root.put("averageDailyCost", averageDailyCost);
        root.put("recordedDays", recordedDays);
        root.put("lightingPages", lightingPages);
        root.put("airPages", airPages);
        root.put("fetchedAt", fetchedAt);
        JSONArray dailyJson = new JSONArray();
        for (DailyUsage item : daily) dailyJson.put(item.toJson());
        root.put("daily", dailyJson);
        JSONArray recordsJson = new JSONArray();
        for (UsageRecord item : records) recordsJson.put(item.toJson());
        root.put("records", recordsJson);
        return root;
    }

    static UsageResult fromJson(JSONObject root) throws JSONException {
        if (root.getInt("version") != CACHE_VERSION) return null;
        String latest = root.isNull("latestDate") ? null : root.getString("latestDate");
        String highest = root.isNull("highestDate") ? null : root.getString("highestDate");
        JSONArray dailyJson = root.getJSONArray("daily");
        List<DailyUsage> daily = new ArrayList<>(dailyJson.length());
        for (int i = 0; i < dailyJson.length(); i++) {
            daily.add(DailyUsage.fromJson(dailyJson.getJSONObject(i)));
        }
        JSONArray recordsJson = root.getJSONArray("records");
        List<UsageRecord> records = new ArrayList<>(recordsJson.length());
        for (int i = 0; i < recordsJson.length(); i++) {
            records.add(UsageRecord.fromJson(recordsJson.getJSONObject(i)));
        }
        return new UsageResult(
                root.getString("room"), root.optString("roomId", ""), root.optString("campus", ""),
                root.getString("rangeStart"), root.getString("rangeEnd"),
                root.getDouble("lightingKwh"), root.getDouble("airKwh"), root.optDouble("todayKwh", 0),
                root.getDouble("todayCost"), root.getDouble("yesterdayCost"), root.getDouble("sevenDayCost"),
                latest, root.getDouble("latestKwh"), root.getDouble("latestCost"),
                highest, root.getDouble("highestCost"),
                root.getDouble("totalKwh"), root.getDouble("totalCost"), root.getDouble("averageDailyCost"),
                root.getInt("recordedDays"), root.getInt("lightingPages"), root.getInt("airPages"),
                root.getLong("fetchedAt"), daily, records
        );
    }
}
