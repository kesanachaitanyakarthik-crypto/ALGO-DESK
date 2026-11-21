package com.tradingdesk.model;

import java.time.Instant;
import java.util.Objects;

public record SystemMetric(String name, double value, String unit, Instant recordedAt) {

    public SystemMetric {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(recordedAt, "recordedAt");
    }
}
