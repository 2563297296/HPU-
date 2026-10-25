package cn.edu.hpu.powerwidget;

/** One recharge (purchase) record row from the school site. */
final class RechargeRecord {
    final String time;
    final String room;
    final double kwh;
    final double amount;
    final String operator;

    RechargeRecord(String time, String room, double kwh, double amount, String operator) {
        this.time = time == null ? "" : time.trim();
        this.room = room == null ? "" : room.trim();
        this.kwh = kwh;
        this.amount = amount;
        this.operator = operator == null ? "" : operator.trim();
    }
}
