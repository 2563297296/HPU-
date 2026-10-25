package cn.edu.hpu.powerwidget;

import org.json.JSONException;
import org.json.JSONObject;

final class DailyUsage {
    final String date;
    final double lightingKwh;
    final double lightingCost;
    final double airKwh;
    final double airCost;
    final double totalKwh;
    final double totalCost;
    final boolean recorded;

    DailyUsage(String date, double lightingKwh, double lightingCost, double airKwh,
               double airCost, double totalKwh, double totalCost, boolean recorded) {
        this.date = date;
        this.lightingKwh = lightingKwh;
        this.lightingCost = lightingCost;
        this.airKwh = airKwh;
        this.airCost = airCost;
        this.totalKwh = totalKwh;
        this.totalCost = totalCost;
        this.recorded = recorded;
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("date", date)
                .put("lightingKwh", lightingKwh)
                .put("lightingCost", lightingCost)
                .put("airKwh", airKwh)
                .put("airCost", airCost)
                .put("totalKwh", totalKwh)
                .put("totalCost", totalCost)
                .put("recorded", recorded);
    }

    static DailyUsage fromJson(JSONObject json) throws JSONException {
        return new DailyUsage(
                json.getString("date"),
                json.getDouble("lightingKwh"),
                json.getDouble("lightingCost"),
                json.getDouble("airKwh"),
                json.getDouble("airCost"),
                json.getDouble("totalKwh"),
                json.getDouble("totalCost"),
                json.getBoolean("recorded")
        );
    }
}
