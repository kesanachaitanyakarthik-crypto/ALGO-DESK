package com.tradingdesk.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class Formatters {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private Formatters() {
    }

    public static String money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static String price(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    public static String percent(double fraction) {
        return BigDecimal.valueOf(fraction * 100.0).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    public static String time(Instant instant) {
        return instant == null ? "-" : TIME_FORMAT.format(instant);
    }
}
