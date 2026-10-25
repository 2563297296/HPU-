package cn.edu.hpu.powerwidget;

import org.json.JSONException;
import org.json.JSONObject;

final class UsageRecord {
    static final String TYPE_LIGHTING = "lighting";
    static final String TYPE_AIR = "airConditioning";

    final String date;
    final String type;
    final String room;
    final double kwh;
    final double unitPrice;
    final double cost;

    UsageRecord(String date, String type, String room, double kwh, double unitPrice, double cost) {
        this.date = date;
        this.type = type;
        this.room = room;
        this.kwh = kwh;
        this.unitPrice = unitPrice;
        this.cost = cost;
    }

    String typeLabel() {
        return TYPE_AIR.equals(type) ? "空调" : "照明";
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("date", date)
                .put("type", type)
                .put("room", room)
                .put("kwh", kwh)
                .put("unitPrice", unitPrice)
                .put("cost", cost);
    }

    static UsageRecord fromJson(JSONObject json) throws JSONException {
        return new UsageRecord(
                json.getString("date"),
                json.getString("type"),
                json.optString("room", ""),
                json.getDouble("kwh"),
                json.getDouble("unitPrice"),
                json.getDouble("cost")
        );
    }
}
