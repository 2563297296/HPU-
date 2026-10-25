package cn.edu.hpu.powerwidget;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Queries and displays recharge (purchase) records for the current room. */
public final class RechargeActivity extends Activity {
    private ListView list;
    private TextView empty;
    private TextView summary;
    private Room room;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLogger.init(this);
        setContentView(R.layout.activity_recharge);
        list = findViewById(R.id.recharge_list);
        empty = findViewById(R.id.recharge_empty);
        summary = findViewById(R.id.recharge_summary);
        list.setEmptyView(empty);
        findViewById(R.id.recharge_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        });

        room = RoomStore.current(this);
        TextView title = findViewById(R.id.recharge_room_title);
        title.setText(room == null ? "请先选择寝室" : room.displayName() + " · " + room.campusLabel());

        if (room == null) {
            summary.setText("请先返回主页选择寝室");
            empty.setText("请先返回主页选择寝室");
            return;
        }
        load();
    }

    private void load() {
        final String end = PowerDates.today();
        final String start;
        try { start = PowerDates.shiftDays(end, -364); }
        catch (Exception e) { summary.setText("日期计算失败"); return; }

        summary.setText("正在查询充值记录（近 365 天）…");
        new Thread(new Runnable() {
            @Override public void run() {
                final List<RechargeRecord>[] holder = new List[1];
                final String[] errHolder = new String[1];
                AppLogger.info("查询充值记录 · " + room.displayName());
                try {
                    holder[0] = SchoolPowerClient.fetchRecharge(room, start, end);
                    errHolder[0] = null;
                } catch (Exception e) {
                    holder[0] = new ArrayList<RechargeRecord>();
                    errHolder[0] = e.getMessage() == null || e.getMessage().trim().isEmpty()
                            ? e.getClass().getSimpleName() : e.getMessage().trim();
                    AppLogger.error("充值记录查询失败: " + errHolder[0]);
                }
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override public void run() {
                        if (isFinishing()) return;
                        if (errHolder[0] != null) {
                            summary.setText("查询失败");
                            empty.setText("未能读取充值记录\n" + errHolder[0]);
                            list.setAdapter(null);
                            return;
                        }
                        render(holder[0]);
                    }
                });
            }
        }, "recharge-loader").start();
    }

    private void render(List<RechargeRecord> records) {
        double totalAmount = 0;
        double totalKwh = 0;
        for (RechargeRecord r : records) { totalAmount += r.amount; totalKwh += r.kwh; }
        summary.setText("共 " + records.size() + " 条 · 充值金额 ¥" + money(totalAmount)
                + " · 充值电量 " + kwh(totalKwh) + " 度");
        List<Map<String, String>> rows = new ArrayList<>(records.size());
        for (RechargeRecord r : records) {
            Map<String, String> row = new HashMap<>();
            row.put("time", r.time);
            row.put("amount", "¥" + money(r.amount));
            row.put("detail", "充值电量 " + kwh(r.kwh) + " 度"
                    + (r.operator.isEmpty() ? "" : " · 充值人 " + r.operator));
            rows.add(row);
        }
        list.setAdapter(new SimpleAdapter(this, rows, R.layout.item_recharge,
                new String[] {"time", "amount", "detail"},
                new int[] {R.id.recharge_time, R.id.recharge_amount, R.id.recharge_detail}));
    }

    private static String money(double value) { return new DecimalFormat("0.###").format(value); }
    private static String kwh(double value) { return new DecimalFormat("0.##").format(value); }
}
