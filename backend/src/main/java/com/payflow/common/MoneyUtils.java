package com.payflow.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtils {

    public static final int FINANCIAL_SCALE = 4;
    public static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;

    private MoneyUtils() {}

    public static BigDecimal format(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(FINANCIAL_SCALE, DEFAULT_ROUNDING);
        }
        return amount.setScale(FINANCIAL_SCALE, DEFAULT_ROUNDING);
    }

    public static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isZero(BigDecimal amount) {
        return amount == null || amount.compareTo(BigDecimal.ZERO) == 0;
    }
}

