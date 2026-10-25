package cn.edu.hpu.powerwidget;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Detail view whose time range follows DetailRangeStore (configurable in settings). */
public final class UsageDetailActivity extends Activity {
    private static final int TAB_DAILY = 0;
    private static final int TAB_RECORDS = 1;

    private ListView listView;
    private TextView emptyView;
    private TextView summaryView;
    private TextView rangeView;
    private TextView updateView;
    private Button dailyTab;
    private Button recordsTab;
    private UsageResult data;
    private int selectedTab = TAB_DAILY;
    private Room room;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usage_detail);

        listView = findViewById(R.id.usage_list);
        emptyView = findViewById(R.id.usage_empty);
        summaryView = findViewById(R.id.usage_summary);
        rangeView = findViewById(R.id.usage_range);
        updateView = findViewById(R.id.usage_updated);
        dailyTab = findViewById(R.id.tab_daily);
        recordsTab = findViewById(R.id.tab_records);
        TextView backButton = findViewById(R.id.button_back);

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        });
        dailyTab.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { selectTab(TAB_DAILY); }
        });
        recordsTab.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { selectTab(TAB_RECORDS); }
        });

        listView.setEmptyView(emptyView);
        showTabState();
        start();
    }

    private void start() {
        room = RoomStore.current(this);
        TextView title = findViewById(R.id.usage_room_title);
        title.setText((room == null ? "寝室" : room.displayName()) + " · 用电明细");
        if (room == null) {
            showEmpty("请先返回主页选择寝室。", "");
            return;
        }
        if (!DetailRangeStore.remembered(this)) {
            DetailRangePrompt.show(this, new DetailRangePrompt.Listener() {
                @Override public void onApplied() { loadDetail(); }
            });
        } else {
            loadDetail();
        }
    }

    private void loadDetail() {
        final String start;
        final String end;
        try {
            String[] range = DetailRangeStore.computeRange(this);
            start = range[0];
            end = range[1];
        } catch (Exception error) {
            showEmpty("日期配置有误，请到设置中修改。", String.valueOf(error.getMessage()));
            return;
        }
        summaryView.setText("正在查询 " + start + " 至 " + end + " …");
        rangeView.setText(DetailRangeStore.describe(this));
        updateView.setText("正在连接学校站点…");
        listView.setAdapter(null);
        emptyView.setText("正在查询…");

        DetailDataLoader.load(this, room, start, end, new DetailDataLoader.Callback() {
            @Override public void onLoaded(UsageResult result, String error) {
                if (isFinishing()) return;
                if (result == null) {
                    showEmpty("查询失败", error == null ? "请检查网络后重试" : error);
                    return;
                }
                data = result;
                renderDetail();
            }
        });
    }

    private void showEmpty(String title, String detail) {
        summaryView.setText(title);
        rangeView.setText("");
        updateView.setText(detail);
        listView.setAdapter(null);
        emptyView.setText(title + (detail.isEmpty() ? "" : "\n" + detail));
    }

    private void renderDetail() {
        if (data == null) return;
        summaryView.setText("总费用 ¥" + money(data.totalCost)
                + "  ·  总用电 " + kwh(data.totalKwh) + " 度"
                + "\n有效 " + data.recordedDays + " 天  ·  日均 ¥" + money(data.averageDailyCost));
        rangeView.setText(data.rangeStart + " 至 " + data.rangeEnd
                + "（含首尾 " + data.daily.size() + " 个日期）");
        updateView.setText("更新于 " + PowerDates.dateTime(data.fetchedAt));
        emptyView.setText("暂无明细记录");
        renderRows();
    }

    private void selectTab(int tab) {
        if (selectedTab == tab) return;
        selectedTab = tab;
        showTabState();
        renderRows();
    }

    private void showTabState() {
        styleTab(dailyTab, selectedTab == TAB_DAILY);
        styleTab(recordsTab, selectedTab == TAB_RECORDS);
    }

    private void styleTab(Button button, boolean selected) {
        button.setBackgroundResource(selected
                ? R.drawable.button_primary_background
                : R.drawable.button_secondary_background);
        button.setTextColor(getColor(selected ? R.color.white : R.color.navy));
    }

    private void renderRows() {
        if (data == null) return;
        if (selectedTab == TAB_DAILY) renderDailyRows(); else renderRecordRows();
    }

    private void renderDailyRows() {
        List<DailyUsage> items = new ArrayList<>(data.daily);
        Collections.sort(items, new Comparator<DailyUsage>() {
            @Override public int compare(DailyUsage left, DailyUsage right) { return right.date.compareTo(left.date); }
        });
        double lightRemaining = data.lightingKwh;
        double airRemaining = data.airKwh;
        List<Map<String, String>> rows = new ArrayList<>(items.size());
        for (DailyUsage item : items) {
            Map<String, String> row = new HashMap<>();
            row.put("date", item.date + (item.recorded ? "" : " · 无记录"));
            row.put("total", "当日 ¥" + money(item.totalCost));
            row.put("lighting", "照明  " + kwh(item.lightingKwh) + " 度 · ¥" + money(item.lightingCost));
            row.put("air", "空调  " + kwh(item.airKwh) + " 度 · ¥" + money(item.airCost));
            row.put("remaining", "当日剩余 · 照明 " + kwh(lightRemaining) + " 度 · 空调 " + kwh(airRemaining) + " 度");
            lightRemaining += item.lightingKwh;
            airRemaining += item.airKwh;
            rows.add(row);
        }
        listView.setAdapter(new SimpleAdapter(this, rows, R.layout.item_usage_daily,
                new String[] {"date", "total", "lighting", "air", "remaining"},
                new int[] {R.id.daily_date, R.id.daily_total, R.id.daily_lighting, R.id.daily_air, R.id.daily_remaining}));
    }

    private void renderRecordRows() {
        List<UsageRecord> items = new ArrayList<>(data.records);
        Collections.sort(items, new Comparator<UsageRecord>() {
            @Override public int compare(UsageRecord left, UsageRecord right) {
                int byDate = right.date.compareTo(left.date);
                return byDate != 0 ? byDate : left.type.compareTo(right.type);
            }
        });
        List<Map<String, String>> rows = new ArrayList<>(items.size());
        for (UsageRecord item : items) {
            Map<String, String> row = new HashMap<>();
            row.put("date", item.date);
            row.put("type", item.typeLabel());
            row.put("cost", "¥" + money(item.cost));
            row.put("usage", "用量  " + kwh(item.kwh) + " 度");
            row.put("price", "单价  ¥" + price(item.unitPrice) + " / 度");
            rows.add(row);
        }
        listView.setAdapter(new SimpleAdapter(this, rows, R.layout.item_usage_record,
                new String[] {"date", "type", "cost", "usage", "price"},
                new int[] {R.id.record_date, R.id.record_type, R.id.record_cost,
                        R.id.record_kwh, R.id.record_price}));
    }

    private static String money(double value) { return new DecimalFormat("0.###").format(value); }
    private static String kwh(double value) { return new DecimalFormat("0.##").format(value); }
    private static String price(double value) { return new DecimalFormat("0.###").format(value); }
}
