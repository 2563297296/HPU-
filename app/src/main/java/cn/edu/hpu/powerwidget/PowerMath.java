package cn.edu.hpu.powerwidget;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class PowerMath {
    private PowerMath() {}

    static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    static double round4(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
