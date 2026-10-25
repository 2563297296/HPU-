package cn.edu.hpu.powerwidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class UsageAggregator {
    private UsageAggregator() {}

    static UsageResult aggregate(Room room, MeterUsage lighting, MeterUsage airConditioning,
                                 String rangeStart, String rangeEnd, long fetchedAt)
            throws PowerSiteException {
        List<UsageRecord> records = new ArrayList<>();
        for (UsageRecord record : lighting.records) {
            if (isInRange(record, rangeStart, rangeEnd)) records.add(normalize(record));
        }
        for (UsageRecord record : airConditioning.records) {
            if (isInRange(record, rangeStart, rangeEnd)) records.add(normalize(record));
        }
        Collections.sort(records, new Comparator<UsageRecord>() {
            @Override
            public int compare(UsageRecord left, UsageRecord right) {
                int byDate = right.date.compareTo(left.date);
                return byDate != 0 ? byDate : left.type.compareTo(right.type);
            }
        });

        LinkedHashMap<String, Day> days = new LinkedHashMap<>();
        String cursor = rangeStart;
        while (cursor.compareTo(rangeEnd) <= 0) {
            days.put(cursor, new Day(cursor));
            cursor = PowerDates.shiftDays(cursor, 1);
        }

        for (UsageRecord record : records) {
            Day day = days.get(record.date);
            if (day == null) continue;
            if (UsageRecord.TYPE_LIGHTING.equals(record.type)) {
                day.lightingKwh += record.kwh;
                day.lightingCost += record.cost;
            } else {
                day.airKwh += record.kwh;
                day.airCost += record.cost;
            }
            day.recorded = true;
        }

        List<DailyUsage> daily = new ArrayList<>(days.size());
        Day latest = null;
        Day highest = null;
        int recordedDays = 0;
        double totalKwh = 0;
        double totalCost = 0;
        for (Day day : days.values()) {
            double dayKwh = PowerMath.round4(day.lightingKwh + day.airKwh);
            double dayCost = PowerMath.round2(day.lightingCost + day.airCost);
            DailyUsage item = new DailyUsage(
                    day.date, PowerMath.round4(day.lightingKwh), PowerMath.round2(day.lightingCost),
                    PowerMath.round4(day.airKwh), PowerMath.round2(day.airCost),
                    dayKwh, dayCost, day.recorded);
            daily.add(item);
            if (day.recorded) {
                recordedDays++;
                latest = day;
                if (highest == null || dayCost > highest.cost()) highest = day;
            }
        }
        for (UsageRecord record : records) {
            totalKwh += record.kwh;
            totalCost += record.cost;
        }
        totalKwh = PowerMath.round4(totalKwh);
        totalCost = PowerMath.round2(totalCost);

        String yesterday = PowerDates.shiftDays(rangeEnd, -1);
        String sevenDayStart = PowerDates.shiftDays(rangeEnd, -6);
        Day todayDay = days.get(rangeEnd);
        Day yesterdayDay = days.get(yesterday);
        double sevenDayCost = 0;
        for (DailyUsage item : daily) {
            if (item.date.compareTo(sevenDayStart) >= 0 && item.date.compareTo(rangeEnd) <= 0) {
                sevenDayCost += item.totalCost;
            }
        }

        return new UsageResult(
                room.displayName(), room.id, room.campus, rangeStart, rangeEnd,
                PowerMath.round4(lighting.balanceKwh), PowerMath.round4(airConditioning.balanceKwh),
                todayDay == null ? 0 : todayDay.kwh(),
                todayDay == null ? 0 : todayDay.cost(), yesterdayDay == null ? 0 : yesterdayDay.cost(),
                PowerMath.round2(sevenDayCost),
                latest == null ? null : latest.date,
                latest == null ? 0 : latest.kwh(),
                latest == null ? 0 : latest.cost(),
                highest == null ? null : highest.date,
                highest == null ? 0 : highest.cost(),
                totalKwh, totalCost,
                recordedDays == 0 ? 0 : PowerMath.round2(totalCost / recordedDays),
                recordedDays, lighting.pages, airConditioning.pages, fetchedAt,
                daily, records);
    }

    private static boolean isInRange(UsageRecord record, String rangeStart, String rangeEnd) {
        return record != null && record.date != null
                && record.date.compareTo(rangeStart) >= 0
                && record.date.compareTo(rangeEnd) <= 0;
    }

    private static UsageRecord normalize(UsageRecord record) {
        double kwh = PowerMath.round4(record.kwh);
        return new UsageRecord(
                record.date, record.type, record.room, kwh,
                PowerMath.round4(record.unitPrice), PowerMath.round2(kwh * record.unitPrice));
    }

    private static final class Day {
        final String date;
        double lightingKwh;
        double lightingCost;
        double airKwh;
        double airCost;
        boolean recorded;

        Day(String date) {
            this.date = date;
        }

        double kwh() {
            return PowerMath.round4(lightingKwh + airKwh);
        }

        double cost() {
            return PowerMath.round2(lightingCost + airCost);
        }
    }
}
