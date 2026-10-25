package cn.edu.hpu.powerwidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.InputType;
import android.view.View;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Date;

final class DetailRangePrompt {
    interface Listener { void onApplied(); }

    private DetailRangePrompt() {}

    static void show(final Activity activity, final Listener listener) {
        final float density = activity.getResources().getDisplayMetrics().density;
        final int idDays = View.generateViewId();
        final int idRange = View.generateViewId();

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 22), dp(activity, 18), dp(activity, 22), dp(activity, 6));
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(activity);
        title.setText("查看明细时间范围");
        title.setTextColor(activity.getColor(R.color.navy));
        title.setTextSize(18f);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));

        TextView subtitle = new TextView(activity);
        subtitle.setText("选择查看方式，并设置对应的时间参数");
        subtitle.setTextColor(activity.getColor(R.color.text_muted));
        subtitle.setTextSize(12f);

        RadioGroup group = new RadioGroup(activity);
        group.setOrientation(RadioGroup.VERTICAL);

        final RadioButton modeDays = new RadioButton(activity);
        modeDays.setId(idDays);
        modeDays.setText("模式一 · 最近 N 天（至今）");
        modeDays.setTextColor(activity.getColor(R.color.text_primary));
        modeDays.setTextSize(15f);
        modeDays.setButtonTintList(ColorStateList.valueOf(activity.getColor(R.color.accent)));
        modeDays.setPadding(0, dp(activity, 4), 0, dp(activity, 4));

        final RadioButton modeRange = new RadioButton(activity);
        modeRange.setId(idRange);
        modeRange.setText("模式二 · 固定起始 / 结束日期");
        modeRange.setTextColor(activity.getColor(R.color.text_primary));
        modeRange.setTextSize(15f);
        modeRange.setButtonTintList(ColorStateList.valueOf(activity.getColor(R.color.accent)));
        modeRange.setPadding(0, dp(activity, 4), 0, dp(activity, 4));

        group.addView(modeDays);
        group.addView(modeRange);

        final EditText daysInput = new EditText(activity);
        styleEdit(activity, daysInput, "查看天数，例如 90");
        daysInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        final EditText startInput = new EditText(activity);
        styleDateField(activity, startInput, "点击选择起始日期");

        final EditText endInput = new EditText(activity);
        styleDateField(activity, endInput, "点击选择结束日期");

        final CheckBox remember = new CheckBox(activity);
        remember.setText("记住我的选择");
        remember.setTextColor(activity.getColor(R.color.text_primary));
        remember.setTextSize(14f);
        remember.setButtonTintList(ColorStateList.valueOf(activity.getColor(R.color.accent)));

        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gap.topMargin = dp(activity, 14);

        root.addView(title);
        root.addView(subtitle);
        root.addView(group, gap);
        root.addView(daysInput, gap);
        root.addView(startInput, gap);
        root.addView(endInput, gap);
        root.addView(remember, gap);

        int currentMode = DetailRangeStore.mode(activity);
        group.check(currentMode == DetailRangeStore.MODE_RANGE ? idRange : idDays);
        daysInput.setText(String.valueOf(DetailRangeStore.days(activity)));
        startInput.setText(DetailRangeStore.start(activity));
        endInput.setText(DetailRangeStore.end(activity));

        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
                boolean rangeMode = checkedId == idRange;
                daysInput.setVisibility(rangeMode ? View.GONE : View.VISIBLE);
                startInput.setVisibility(rangeMode ? View.VISIBLE : View.GONE);
                endInput.setVisibility(rangeMode ? View.VISIBLE : View.GONE);
            }
        });
        modeDays.post(new Runnable() {
            @Override public void run() {
                boolean rangeMode = group.getCheckedRadioButtonId() == idRange;
                daysInput.setVisibility(rangeMode ? View.GONE : View.VISIBLE);
                startInput.setVisibility(rangeMode ? View.VISIBLE : View.GONE);
                endInput.setVisibility(rangeMode ? View.VISIBLE : View.GONE);
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setView(root);
        builder.setNegativeButton("取消", null);
        builder.setPositiveButton("确定", null);
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface dialogInterface) {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        boolean rangeMode = group.getCheckedRadioButtonId() == idRange;
                        int mode = rangeMode ? DetailRangeStore.MODE_RANGE : DetailRangeStore.MODE_DAYS;
                        int days = 90;
                        String start = "";
                        String end = "";
                        if (rangeMode) {
                            start = startInput.getText().toString().trim();
                            end = endInput.getText().toString().trim();
                            if (!start.matches("\\d{4}-\\d{2}-\\d{2}") || !end.matches("\\d{4}-\\d{2}-\\d{2}")) {
                                Toast.makeText(activity, "日期格式应为 yyyy-MM-dd", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            if (start.compareTo(end) > 0) {
                                Toast.makeText(activity, "起始日期不能晚于结束日期", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        } else {
                            try { days = Integer.parseInt(daysInput.getText().toString().trim()); }
                            catch (NumberFormatException e) { days = 0; }
                            if (days < 1) {
                                Toast.makeText(activity, "请输入有效的查看天数", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                        DetailRangeStore.save(activity, mode, days, start, end, remember.isChecked());
                        dialog.dismiss();
                        if (listener != null) listener.onApplied();
                    }
                });
            }
        });
        dialog.show();
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static void styleEdit(Activity activity, EditText edit, String hint) {
        edit.setHint(hint);
        edit.setHintTextColor(activity.getColor(R.color.text_muted));
        edit.setTextColor(activity.getColor(R.color.text_primary));
        edit.setTextSize(15f);
        edit.setBackgroundResource(R.drawable.input_background);
        edit.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10));
        edit.setSingleLine(true);
    }

    private static void styleDateField(final Activity activity, final EditText edit, String hint) {
        styleEdit(activity, edit, hint);
        edit.setInputType(InputType.TYPE_NULL);
        edit.setKeyListener(null);
        edit.setFocusable(false);
        edit.setClickable(true);
        edit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showDatePicker(activity, edit); }
        });
    }

    private static void showDatePicker(final Activity activity, final EditText edit) {
        Calendar calendar = Calendar.getInstance(PowerDates.SHANGHAI);
        String current = edit.getText().toString().trim();
        if (current.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try {
                Date parsed = PowerDates.parse(current);
                calendar.setTime(parsed);
            } catch (Exception ignored) {}
        }
        DatePickerDialog picker = new DatePickerDialog(activity, new DatePickerDialog.OnDateSetListener() {
            @Override public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                edit.setText(String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", year, month + 1, dayOfMonth));
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        picker.show();
    }
}
