package cn.edu.hpu.powerwidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Locale;

public final class SettingsActivity extends Activity {
    private static final int REQ_ONBOARD = 4719;
    private TextView lightingView;
    private TextView airView;
    private TextView totalCostView;
    private TextView totalKwhView;
    private TextView averageView;
    private TextView rangeView;
    private TextView statusView;
    private TextView errorView;
    private Button refreshButton;
    private boolean receiverRegistered;
    private float downX;
    private float downY;
    private boolean trackingHorizontal;
    private int touchSlop;

    private final BroadcastReceiver dataChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            renderCache();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        initSwipe();
        initPing();
        initQueryAllCheckbox();
        initAnnouncements();

        lightingView = findViewById(R.id.lighting_balance);
        airView = findViewById(R.id.air_balance);
        totalCostView = findViewById(R.id.total_cost);
        totalKwhView = findViewById(R.id.total_kwh);
        averageView = findViewById(R.id.average_cost);
        rangeView = findViewById(R.id.range_text);
        statusView = findViewById(R.id.connection_status);
        errorView = findViewById(R.id.error_detail);
        refreshButton = findViewById(R.id.button_refresh);
        Button switchRoomButton = findViewById(R.id.button_switch_room);
        Button settingsButton = findViewById(R.id.button_settings);
        Button detailButton = findViewById(R.id.button_details);
        Button addWidgetButton = findViewById(R.id.button_add_widget);
        Button rechargeButton = findViewById(R.id.button_recharge);

        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { startManualRefresh(); }
        });
        switchRoomButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivityForResult(new Intent(SettingsActivity.this, RoomListActivity.class), 4712);
            }
        });
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { startActivity(new Intent(SettingsActivity.this, PreferencesActivity.class)); }
        });
        detailButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivity(new Intent(SettingsActivity.this, UsageDetailActivity.class));
            }
        });
        addWidgetButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { requestPinWidget(); }
        });
        rechargeButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { startActivity(new Intent(SettingsActivity.this, RechargeActivity.class)); }
        });
        AppLogger.init(this);
        if (!RoomStore.appOnboarded(this)) {
            RoomStore.setAppOnboarded(this, true);
            startActivityForResult(new Intent(this, OnboardingActivity.class), REQ_ONBOARD);
        } else {
            ensureRoomSelection();
        }
        renderCache();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(PowerCache.ACTION_DATA_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(dataChangedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(dataChangedReceiver, filter);
            }
            receiverRegistered = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderCache();
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(dataChangedReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    private void ensureRoomSelection() {
        if (RoomStore.hasSelection(this)) return;
        Intent intent = new Intent(this, RoomChooserActivity.class);
        intent.putExtra(RoomChooserActivity.EXTRA_ADD_MODE, false);
        startActivityForResult(intent, 4711);
    }

    private void chooseRoom(boolean addMode) {
        Intent intent = new Intent(this, RoomChooserActivity.class);
        intent.putExtra(RoomChooserActivity.EXTRA_ADD_MODE, addMode);
        startActivityForResult(intent, 4711);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ONBOARD) {
            ensureRoomSelection();
            renderCache();
        } else if (requestCode == 4711 || requestCode == 4712) {
            WidgetRenderer.updateAll(this, null, false);
            renderCache();
        }
    }

    private void initAnnouncements() {
        final View banner = findViewById(R.id.announcement_banner);
        final TextView title = findViewById(R.id.announcement_title);
        final TextView content = findViewById(R.id.announcement_content);
        RemoteApiClient.fetch(this, new RemoteApiClient.Callback() {
            @Override public void onLoaded(RemoteConfig config, String error) {
                if (isFinishing()) return;
                checkUpdate(config);
                final RemoteConfig.Announcement ann = config == null ? null : config.latest();
                if (ann == null || ann.title.isEmpty()) {
                    banner.setVisibility(View.GONE);
                } else {
                    title.setText((ann.time.isEmpty() ? "" : ann.time + " ") + ann.title);
                    content.setText(ann.content);
                    banner.setVisibility(View.VISIBLE);
                    banner.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            new AlertDialog.Builder(SettingsActivity.this)
                                    .setTitle(ann.title)
                                    .setMessage((ann.time.isEmpty() ? "" : ann.time + "\n") + ann.content)
                                    .setPositiveButton("关闭", null)
                                    .show();
                        }
                    });
                }
            }
        });
    }

    private void checkUpdate(RemoteConfig config) {
        if (config == null) return;
        int current = currentVersionCode();
        if (config.latestVersionCode <= current && !config.forceUpdate) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("发现新版本")
                .setMessage("最新版本 " + config.latestVersion
                        + (config.forceUpdate ? "，必须更新后才能继续使用。" : "，建议更新。"));
        final String dl = config.downloadUrl;
        if (!dl.isEmpty()) {
            builder.setPositiveButton("去更新", new android.content.DialogInterface.OnClickListener() {
                @Override public void onClick(android.content.DialogInterface dialog, int which) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(dl))); }
                    catch (Exception e) { Toast.makeText(SettingsActivity.this, "无法打开下载链接", Toast.LENGTH_SHORT).show(); }
                }
            });
        }
        if (config.forceUpdate) {
            builder.setCancelable(false);
        } else {
            builder.setNegativeButton("稍后", null);
        }
        builder.show();
    }

    private int currentVersionCode() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception e) { return 0; }
    }

    private void initQueryAllCheckbox() {
        CheckBox checkBox = findViewById(R.id.check_query_all);
        if (checkBox == null) return;
        checkBox.setChecked(PowerRefreshManager.queryAll(this));
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PowerRefreshManager.setQueryAll(SettingsActivity.this, isChecked);
            }
        });
    }

    private void initPing() {
        final Button pingButton = findViewById(R.id.button_ping);
        if (pingButton == null) return;
        pingButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                final TextView result = findViewById(R.id.ping_result);
                result.setText("测试中…");
                result.setTextColor(getColor(R.color.widget_muted));
                final Room current = RoomStore.current(SettingsActivity.this);
                final String campus = current == null ? SchoolPowerClient.Campus.NORTH : current.campus;
                new Thread(new Runnable() {
                    @Override public void run() {
                        final long ms = SchoolPowerClient.pingLatency(campus);
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override public void run() {
                                if (isFinishing()) return;
                                if (ms < 0) {
                                    result.setText("超时/失败");
                                    result.setTextColor(getColor(R.color.danger));
                                } else {
                                    result.setText(ms + "ms");
                                    int color = ms < 100 ? R.color.success : (ms <= 500 ? R.color.yellow : R.color.danger);
                                    result.setTextColor(getColor(color));
                                }
                            }
                        });
                    }
                }).start();
            }
        });
    }

    private void initSwipe() {
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        final View scroll = findViewById(R.id.settings_scroll);
        if (scroll == null) return;
        scroll.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getX();
                        downY = event.getY();
                        trackingHorizontal = false;
                        return false;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = event.getX() - downX;
                        float dy = event.getY() - downY;
                        if (!trackingHorizontal && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                            trackingHorizontal = true;
                        }
                        if (trackingHorizontal) {
                            View content = findViewById(R.id.room_pager_content);
                            if (content != null) content.setTranslationX(dx);
                            return true;
                        }
                        return false;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        if (trackingHorizontal) {
                            trackingHorizontal = false;
                            float dx = event.getX() - downX;
                            View content = findViewById(R.id.room_pager_content);
                            int width = content == null ? 0 : content.getWidth();
                            boolean enough = width > 0 && Math.abs(dx) > width / 3f;
                            boolean hasMultiple = RoomStore.rooms(SettingsActivity.this).size() >= 2;
                            if (enough && hasMultiple) {
                                switchRoom(dx < 0 ? 1 : -1);
                            } else if (content != null) {
                                content.animate().translationX(0f).setDuration(120).start();
                            }
                            return true;
                        }
                        return false;
                    }
                }
                return false;
            }
        });
    }

    private void switchRoom(final int direction) {
        final List<Room> rooms = RoomStore.rooms(this);
        if (rooms.size() < 2) return;
        Room current = RoomStore.current(this);
        int index = 0;
        for (int i = 0; i < rooms.size(); i++) {
            if (rooms.get(i).id.equals(current == null ? "" : current.id)) { index = i; break; }
        }
        final int next = (index + direction + rooms.size()) % rooms.size();
        final View content = findViewById(R.id.room_pager_content);
        if (content == null || content.getWidth() <= 0) {
            applySwitch(next, rooms);
            return;
        }
        final int width = content.getWidth();
        // 向左滑(下一页)：内容向左滑出，新内容从右侧进入；向右滑则相反。
        final float outX = direction > 0 ? -width : width;
        final float inX = direction > 0 ? width : -width;
        content.animate().translationX(outX).alpha(0f).setDuration(150)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        applySwitch(next, rooms);
                        content.setTranslationX(inX);
                        content.animate().translationX(0f).alpha(1f).setDuration(150).start();
                    }
                }).start();
    }

    private void applySwitch(int next, List<Room> rooms) {
        Room target = rooms.get(next);
        RoomStore.setCurrent(this, target.id);
        WidgetRenderer.updateAll(this, null, false);
        renderCache();
    }

    private void updateDots() {
        LinearLayout dots = findViewById(R.id.page_dots);
        if (dots == null) return;
        dots.removeAllViews();
        List<Room> rooms = RoomStore.rooms(this);
        Room current = RoomStore.current(this);
        int active = 0;
        for (int i = 0; i < rooms.size(); i++) {
            if (rooms.get(i).id.equals(current == null ? "" : current.id)) { active = i; break; }
        }
        if (rooms.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText("未添加寝室");
            hint.setTextColor(getColor(R.color.widget_muted));
            hint.setTextSize(10f);
            dots.addView(hint);
            return;
        }
        for (int i = 0; i < rooms.size(); i++) {
            TextView dot = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(9), dp(9));
            lp.setMargins(dp(5), 0, dp(5), 0);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(i == active ? R.drawable.page_dot : R.drawable.page_dot_inactive);
            dots.addView(dot);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void startManualRefresh() {
        if (!PowerRefreshManager.allowManualRequest(this)) {
            Toast.makeText(this, "请稍候，正在更新或刚刚刷新过", Toast.LENGTH_SHORT).show();
            renderCache();
            return;
        }
        refreshButton.setEnabled(false);
        refreshButton.setText("正在连接学校站点…");
        final boolean started = PowerRefreshManager.refresh(this, new PowerRefreshManager.Callback() {
            @Override
            public void onComplete(boolean success, String message) {
                if (isFinishing()) return;
                refreshButton.setEnabled(true);
                refreshButton.setText("立即查询");
                renderCache();
                Toast.makeText(SettingsActivity.this,
                        success ? "用电数据已更新" : message,
                        success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
            }
        });
        if (!started) {
            refreshButton.setEnabled(true);
            refreshButton.setText("立即查询");
            Toast.makeText(this, "已有查询正在进行，请稍候", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderCache() {
        Room room = RoomStore.current(this);
        PowerCache.Snapshot snapshot = PowerCache.read(this, room);
        TextView titleView = findViewById(R.id.dashboard_room_title);
        titleView.setText(room == null ? "请先选择寝室" : room.displayName() + " · " + room.campusLabel());
        UsageResult data = snapshot.data;
        TextView unitPriceView = findViewById(R.id.unit_price_text);
        double unitPrice = 0;
        if (data != null) {
            for (UsageRecord record : data.records) {
                if (record.unitPrice > 0) { unitPrice = record.unitPrice; break; }
            }
        }
        unitPriceView.setText("固定电量单价 ¥" + money3(unitPrice) + " / 度");
        TextView todaySummary = findViewById(R.id.today_summary);
        todaySummary.setText("今日用电 " + kwh2(data == null ? 0 : data.todayKwh)
                + " 度 · 今日费用 ¥" + money3(data == null ? 0 : data.todayCost));
        boolean refreshing = PowerRefreshManager.isRefreshing();
        boolean failed = !snapshot.lastError.isEmpty();

        if (data == null) {
            lightingView.setText("--");
            airView.setText("--");
            totalCostView.setText("--");
            totalKwhView.setText("--");
            averageView.setText("--");
            rangeView.setText(room == null ? "请先选择寝室" : "最近 90 天 · 暂无数据");
            if (refreshing) {
                statusView.setText("正在连接学校站点…");
                statusView.setTextColor(getColor(R.color.white));
            } else if (failed) {
                statusView.setText("查询失败 · 暂无缓存");
                statusView.setTextColor(getColor(R.color.widget_danger));
            } else {
                statusView.setText(room == null ? "请先选择寝室" : "点击“立即查询”获取数据");
                statusView.setTextColor(getColor(R.color.widget_muted));
            }
            showError(failed ? snapshot.lastError : "", data);
        } else {
            lightingView.setText(format2(data.lightingKwh));
            airView.setText(format2(data.airKwh));
            totalCostView.setText(money3(data.totalCost));
            totalKwhView.setText(format2(data.totalKwh));
            averageView.setText(money3(data.averageDailyCost));
            rangeView.setText(data.rangeStart + " 至 " + data.rangeEnd);

            if (refreshing) {
                statusView.setText("正在更新 · 当前显示上次缓存");
                statusView.setTextColor(getColor(R.color.widget_muted));
            } else if (failed) {
                statusView.setText("连接失败 · 显示上次缓存");
                statusView.setTextColor(getColor(R.color.widget_danger));
            } else {
                statusView.setText("更新于 " + PowerDates.dateTime(data.fetchedAt));
                statusView.setTextColor(getColor(R.color.white));
            }
            showError(failed ? snapshot.lastError : "", data);
        }

        refreshButton.setEnabled(!refreshing);
        refreshButton.setText(refreshing ? "正在连接学校站点…" : "立即查询");
        updateDots();
    }

    private void showError(String error, UsageResult data) {
        if (error == null || error.trim().isEmpty()) {
            errorView.setVisibility(View.GONE);
            return;
        }
        String detail = error.trim();
        if (data != null) {
            detail += "\n上次成功更新：" + PowerDates.dateTime(data.fetchedAt)
                    + "。仍可查看已缓存的 90 天记录。";
        } else {
            detail += "\n请检查校园网/VPN、学校站点状态后重试。";
        }
        errorView.setText(detail);
        errorView.setVisibility(View.VISIBLE);
    }

    private void requestPinWidget() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                AppWidgetManager manager = AppWidgetManager.getInstance(this);
                if (manager.isRequestPinAppWidgetSupported()) {
                    ComponentName provider = new ComponentName(this, PowerWidgetProvider.class);
                    boolean requested = manager.requestPinAppWidget(provider, null, null);
                    Toast.makeText(this,
                            requested ? "已请求添加到桌面" : "当前桌面不支持直接添加，请长按桌面操作",
                            Toast.LENGTH_LONG).show();
                    return;
                }
            } catch (RuntimeException ignored) {
                // Fall through to the manual instructions below.
            }
        }
        Toast.makeText(this,
                "请长按桌面空白处 → 小组件 → 找到“HPU 用电”",
                Toast.LENGTH_LONG).show();
    }

    private static String format2(double value) {
        return new DecimalFormat("0.##").format(value);
    }

    private static String money3(double value) {
        return new DecimalFormat("0.###").format(value);
    }

    private static String kwh2(double value) {
        return new DecimalFormat("0.00").format(value);
    }
}
