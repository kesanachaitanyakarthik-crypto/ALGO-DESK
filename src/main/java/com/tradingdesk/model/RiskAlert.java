package com.tradingdesk.model;

import java.time.Instant;
import java.util.Objects;

public record RiskAlert(Instrument instrument, AlertSeverity severity, String rule, String message, Instant raisedAt) {

    public RiskAlert {
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(raisedAt, "raisedAt");
    }
}
