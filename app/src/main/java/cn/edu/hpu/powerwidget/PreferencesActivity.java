package cn.edu.hpu.powerwidget;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.List;

public final class PreferencesActivity extends Activity {
    private static final String FEEDBACK_EMAIL = "your-email@example.com"; // TODO: 替换为你的反馈邮箱

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLogger.init(this);
        setContentView(R.layout.activity_preferences);
        TextView status = findViewById(R.id.catalog_status);
        RoomCatalog catalog = CatalogRepository.bundled(this);
        long refreshedAt = RoomStore.catalogRefreshedAt(this);
        String text = "内置寝室目录 · " + catalog.entries.size() + " 个寝室";
        if (refreshedAt > 0) text += "\n最近联网更新：" + PowerDates.dateTime(refreshedAt);
        status.setText(text);

        bind(R.id.button_refresh_catalog, "refresh");
        bind(R.id.button_add_room, "add");
        bind(R.id.button_export_usage, "export");
        bind(R.id.button_export_log, "log");
        bind(R.id.button_feedback, "feedback");
        findViewById(R.id.button_refresh_catalog).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { refreshCatalog(); }});
        findViewById(R.id.button_add_room).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Intent intent = new Intent(PreferencesActivity.this, RoomChooserActivity.class);
                intent.putExtra(RoomChooserActivity.EXTRA_ADD_MODE, true);
                startActivity(intent);
            }});
        findViewById(R.id.button_manage_rooms).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivity(new Intent(PreferencesActivity.this, RoomListActivity.class));
            }});
        findViewById(R.id.button_export_usage).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { exportUsage(); }});
        findViewById(R.id.button_export_log).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivity(new Intent(PreferencesActivity.this, LogActivity.class));
            }});
        findViewById(R.id.button_feedback).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { feedback(); }});
        findViewById(R.id.button_detail_range).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                DetailRangePrompt.show(PreferencesActivity.this, new DetailRangePrompt.Listener() {
                    @Override public void onApplied() { updateDetailStatus(); }
                });
            }});
        findViewById(R.id.button_detail_range_reset).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                DetailRangeStore.reset(PreferencesActivity.this);
                Toast.makeText(PreferencesActivity.this, "已重置时间区间设置", Toast.LENGTH_SHORT).show();
                updateDetailStatus();
            }});
        initBackgroundSettings();
        initUpdateSource();
    }

    @Override protected void onResume() {
        super.onResume();
        updateDetailStatus();
    }

    private void initBackgroundSettings() {
        final CheckBox autoCheck = findViewById(R.id.check_auto_refresh);
        final EditText intervalInput = findViewById(R.id.input_refresh_interval);
        final TextView autoHint = findViewById(R.id.auto_refresh_hint);
        final CheckBox lowCheck = findViewById(R.id.check_low_battery);
        final EditText lightingThresholdInput = findViewById(R.id.input_lighting_threshold);
        final EditText airThresholdInput = findViewById(R.id.input_air_threshold);
        final CheckBox lightingCheck = findViewById(R.id.check_low_lighting);
        final CheckBox airCheck = findViewById(R.id.check_low_air);
        final TextView roomsText = findViewById(R.id.low_battery_rooms);
        final TextView sectionHeader = findViewById(R.id.button_background_section);
        final View section = findViewById(R.id.background_section);
        if (sectionHeader != null && section != null) {
            sectionHeader.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    if (section.getVisibility() == View.VISIBLE) {
                        section.setVisibility(View.GONE);
                        sectionHeader.setText("后台与通知 ▸");
                    } else {
                        section.setVisibility(View.VISIBLE);
                        sectionHeader.setText("后台与通知 ▾");
                    }
                }
            });
        }

        autoCheck.setChecked(AutoRefreshStore.enabled(this));
        intervalInput.setText(String.valueOf(AutoRefreshStore.intervalMinutes(this)));
        autoHint.setVisibility(AutoRefreshStore.enabled(this) ? View.VISIBLE : View.GONE);

        lowCheck.setChecked(LowBatteryStore.enabled(this));
        lightingThresholdInput.setText(trimNumber(LowBatteryStore.lightingThreshold(this)));
        airThresholdInput.setText(trimNumber(LowBatteryStore.airThreshold(this)));
        lightingCheck.setChecked(LowBatteryStore.lighting(this));
        airCheck.setChecked(LowBatteryStore.air(this));
        updateRemindRoomsText(roomsText);

        autoCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                AutoRefreshStore.setEnabled(PreferencesActivity.this, isChecked);
                autoHint.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                BackgroundUpdateScheduler.schedule(PreferencesActivity.this);
                if (isChecked) showBatteryGuidance();
            }
        });

        findViewById(R.id.button_save_interval).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                int min = parseInterval(intervalInput.getText().toString());
                if (min < 15) {
                    Toast.makeText(PreferencesActivity.this, "间隔最少 15 分钟", Toast.LENGTH_SHORT).show();
                    return;
                }
                AutoRefreshStore.setIntervalMinutes(PreferencesActivity.this, min);
                BackgroundUpdateScheduler.schedule(PreferencesActivity.this);
                Toast.makeText(PreferencesActivity.this, "已保存更新间隔", Toast.LENGTH_SHORT).show();
            }
        });

        lowCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                LowBatteryStore.setEnabled(PreferencesActivity.this, isChecked);
                if (isChecked) {
                    requestNotificationPermission();
                    Toast.makeText(PreferencesActivity.this, "软件后台占用可忽略不计，通知权限为必要项", Toast.LENGTH_LONG).show();
                }
                BackgroundUpdateScheduler.schedule(PreferencesActivity.this);
            }
        });

        lightingCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                LowBatteryStore.setLighting(PreferencesActivity.this, isChecked);
            }
        });
        airCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                LowBatteryStore.setAir(PreferencesActivity.this, isChecked);
            }
        });

        lightingThresholdInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    double t = parseThreshold(lightingThresholdInput.getText().toString());
                    if (t > 0) LowBatteryStore.setLightingThreshold(PreferencesActivity.this, t);
                }
            }
        });
        airThresholdInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    double t = parseThreshold(airThresholdInput.getText().toString());
                    if (t > 0) LowBatteryStore.setAirThreshold(PreferencesActivity.this, t);
                }
            }
        });

        findViewById(R.id.button_select_remind_rooms).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showRemindRoomDialog(roomsText); }
        });
    }

    private void showBatteryGuidance() {
        new AlertDialog.Builder(this)
                .setTitle("建议开启后台权限")
                .setMessage("为使后台自动更新更稳定，建议在系统设置中允许本应用“后台自启动”，并将电池策略设为“无限制”。此为非强制授权；若不授予，系统可能在后台拦截更新导致失败。")
                .setPositiveButton("去应用详情", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface dialog, int which) {
                        try {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        } catch (Exception error) {
                            Toast.makeText(PreferencesActivity.this, "无法打开应用详情", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("稍后", null)
                .show();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 200);
            }
        }
    }

    private void showRemindRoomDialog(final TextView roomsText) {
        final List<Room> all = RoomStore.rooms(this);
        if (all.isEmpty()) {
            Toast.makeText(this, "请先添加寝室", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] names = new String[all.size()];
        final boolean[] checked = new boolean[all.size()];
        final List<String> selected = LowBatteryStore.roomIds(this);
        for (int i = 0; i < all.size(); i++) {
            names[i] = all.get(i).displayName();
            checked[i] = selected.contains(all.get(i).id);
        }
        new AlertDialog.Builder(this)
                .setTitle("选择提醒寝室")
                .setMultiChoiceItems(names, checked, new android.content.DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(android.content.DialogInterface dialog, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface dialog, int which) {
                        List<String> ids = new ArrayList<>();
                        for (int i = 0; i < all.size(); i++) if (checked[i]) ids.add(all.get(i).id);
                        LowBatteryStore.setRoomIds(PreferencesActivity.this, ids);
                        updateRemindRoomsText(roomsText);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateRemindRoomsText(TextView roomsText) {
        List<Room> all = RoomStore.rooms(this);
        List<String> selected = LowBatteryStore.roomIds(this);
        int count = 0;
        for (String id : selected) for (Room r : all) if (r.id.equals(id)) { count++; break; }
        roomsText.setText(count == 0 ? "已选寝室：未选择" : "已选寝室：" + count + " 个");
    }

    private int parseInterval(String value) {
        try { return Integer.parseInt(value.trim()); } catch (Exception e) { return 0; }
    }

    private double parseThreshold(String value) {
        try { return Double.parseDouble(value.trim()); } catch (Exception e) { return 0; }
    }

    private String trimNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) return String.valueOf((long) value);
        return String.valueOf(value);
    }

    private void initUpdateSource() {
        final EditText urlInput = findViewById(R.id.input_source_url);
        urlInput.setText(UpdateSourceStore.url(this));
        findViewById(R.id.button_save_source).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                String url = urlInput.getText().toString().trim();
                if (url.isEmpty()) {
                    UpdateSourceStore.setUrl(PreferencesActivity.this, "");
                    Toast.makeText(PreferencesActivity.this, "已清空更新源", Toast.LENGTH_SHORT).show();
                } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    Toast.makeText(PreferencesActivity.this, "地址需以 http:// 或 https:// 开头", Toast.LENGTH_SHORT).show();
                } else {
                    UpdateSourceStore.setUrl(PreferencesActivity.this, url);
                    Toast.makeText(PreferencesActivity.this, "已保存更新源", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void updateDetailStatus() {
        TextView view = findViewById(R.id.detail_range_status);
        if (view != null) {
            view.setText(DetailRangeStore.configured(this)
                    ? "查看明细：" + DetailRangeStore.describe(this)
                    : "查看明细：当前未选择时间区间");
        }
    }

    private void bind(int id, String tag) { ((Button) findViewById(id)).setTag(tag); }

    private void refreshCatalog() {
        Toast.makeText(this, "开始联网拉取寝室列表…", Toast.LENGTH_SHORT).show();
        CatalogRepository.refresh(this, new CatalogRepository.Callback() {
            @Override public void onLoaded(RoomCatalog catalog, boolean refreshed, String message) {
                if (isFinishing()) return;
                Toast.makeText(PreferencesActivity.this, message, refreshed ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                TextView status = findViewById(R.id.catalog_status);
                if (refreshed) status.setText("寝室目录 · " + catalog.entries.size() + " 个寝室\n最近联网更新：刚刚");
            }
        });
    }

    private void exportUsage() {
        final Room room = RoomStore.current(this);
        if (room == null) { Toast.makeText(this, "请先选择寝室", Toast.LENGTH_SHORT).show(); return; }
        final String start;
        final String end;
        try {
            String[] range = DetailRangeStore.computeRange(this);
            start = range[0];
            end = range[1];
        } catch (Exception error) {
            Toast.makeText(this, "日期配置有误，请到设置中修改", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "正在生成导出…", Toast.LENGTH_SHORT).show();
        DetailDataLoader.load(this, room, start, end, new DetailDataLoader.Callback() {
            @Override public void onLoaded(UsageResult result, String error) {
                if (isFinishing()) return;
                if (result == null) {
                    Toast.makeText(PreferencesActivity.this,
                            "导出失败：" + (error == null ? "未知错误" : error), Toast.LENGTH_LONG).show();
                    return;
                }
                writeUsageCsv(room, result);
            }
        });
    }

    private void writeUsageCsv(Room room, UsageResult data) {
        StringBuilder csv = new StringBuilder();
        csv.append("\uFEFF类型,日期,寝室,用电量(度),单价(元/度),费用(元)\n");
        for (UsageRecord row : data.records) {
            csv.append(csv(row.typeLabel())).append(',').append(csv(row.date)).append(',').append(csv(row.room))
               .append(',').append(kwh2(row.kwh)).append(',').append(price3(row.unitPrice)).append(',').append(money3(row.cost)).append('\n');
        }
        csv.append("\n类型,日期,照明度数,照明费用,空调度数,空调费用,总度数,总费用\n");
        for (DailyUsage row : data.daily) {
            csv.append(csv("汇总")).append(',').append(csv(row.date)).append(',').append(kwh2(row.lightingKwh))
               .append(',').append(money3(row.lightingCost)).append(',').append(kwh2(row.airKwh)).append(',')
               .append(money3(row.airCost)).append(',').append(kwh2(row.totalKwh)).append(',').append(money3(row.totalCost)).append('\n');
        }
        File file = new File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS),
                "HPU-usage-" + Room.safeFileToken(room.id) + ".csv");
        write(file, csv.toString().getBytes(StandardCharsets.UTF_8), "用电记录已导出", file);
    }

    private void write(File target, byte[] bytes, String success, File shown) {
        try {
            target.getParentFile().mkdirs();
            FileOutputStream output = new FileOutputStream(target, false);
            try { output.write(bytes); } finally { output.close(); }
            Toast.makeText(this, success + ": " + shown.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException error) { Toast.makeText(this, "导出失败: " + error.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void feedback() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + FEEDBACK_EMAIL));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[] {FEEDBACK_EMAIL});
        intent.putExtra(Intent.EXTRA_SUBJECT, "HPU 用电小组件反馈");
        try { startActivity(intent); }
        catch (ActivityNotFoundException error) { Toast.makeText(this, "未找到邮件应用，邮箱：" + FEEDBACK_EMAIL, Toast.LENGTH_LONG).show(); }
    }

    private static String csv(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
    private static String kwh2(double value) { return new java.text.DecimalFormat("0.##").format(value); }
    private static String price3(double value) { return new java.text.DecimalFormat("0.###").format(value); }
    private static String money3(double value) { return new java.text.DecimalFormat("0.###").format(value); }
}
