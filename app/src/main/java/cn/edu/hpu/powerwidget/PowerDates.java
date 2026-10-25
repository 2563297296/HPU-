package cn.edu.hpu.powerwidget;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class PowerDates {
    static final TimeZone SHANGHAI = TimeZone.getTimeZone("Asia/Shanghai");
    private static final String PATTERN = "yyyy-MM-dd";

    private PowerDates() {}

    static String today() {
        return format(Calendar.getInstance(SHANGHAI).getTime());
    }

    static String shiftDays(String dateText, int days) throws PowerSiteException {
        Calendar calendar = calendarFor(parse(dateText));
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return format(calendar.getTime());
    }

    static Date parse(String dateText) throws PowerSiteException {
        if (dateText == null || !dateText.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new PowerSiteException("日期格式无效：" + dateText);
        }
        SimpleDateFormat formatter = new SimpleDateFormat(PATTERN, Locale.ROOT);
        formatter.setLenient(false);
        formatter.setTimeZone(SHANGHAI);
        try {
            Date parsed = formatter.parse(dateText);
            if (parsed == null || !dateText.equals(formatter.format(parsed))) {
                throw new PowerSiteException("日期格式无效：" + dateText);
            }
            return parsed;
        } catch (ParseException error) {
            throw new PowerSiteException("日期格式无效：" + dateText, error);
        }
    }

    static String format(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat(PATTERN, Locale.ROOT);
        formatter.setTimeZone(SHANGHAI);
        return formatter.format(date);
    }

    static String time(long timestamp) {
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm", Locale.CHINA);
        formatter.setTimeZone(SHANGHAI);
        return formatter.format(new Date(timestamp));
    }

    static String dateTime(long timestamp) {
        SimpleDateFormat formatter = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
        formatter.setTimeZone(SHANGHAI);
        return formatter.format(new Date(timestamp));
    }

    static Calendar calendarFor(Date date) {
        Calendar calendar = Calendar.getInstance(SHANGHAI);
        calendar.setTime(date);
        return calendar;
    }
}