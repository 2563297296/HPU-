package cn.edu.hpu.powerwidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class MeterUsage {
    final String type;
    final String label;
    final double balanceKwh;
    final int pages;
    final List<UsageRecord> records;

    MeterUsage(String type, String label, double balanceKwh, int pages, List<UsageRecord> records) {
        this.type = type;
        this.label = label;
        this.balanceKwh = balanceKwh;
        this.pages = pages;
        this.records = Collections.unmodifiableList(new ArrayList<>(records));
    }
}
