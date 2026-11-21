package com.tradingdesk.model;

import com.tradingdesk.indicators.IndicatorSnapshot;

import java.time.Instant;
import java.util.Objects;

public record TradeSignal(
        Instrument instrument,
        SignalType type,
        double confidence,
        String rationale,
        IndicatorSnapshot indicators,
        Instant generatedAt
) {

    public TradeSignal {
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(rationale, "rationale");
        Objects.requireNonNull(indicators, "indicators");
        Objects.requireNonNull(generatedAt, "generatedAt");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be within [0, 1]");
        }
    }
}
