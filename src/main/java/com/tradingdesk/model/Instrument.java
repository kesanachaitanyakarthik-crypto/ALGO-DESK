package com.tradingdesk.model;

import java.util.Objects;

public record Instrument(String symbol, String displayName, InstrumentType type) {

    public Instrument {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(type, "type");
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
    }
}
