package com.tradingdesk.exchange;

import java.time.Instant;
import java.util.Objects;

/** A balance held at the exchange. */
public record ExchangeBalance(String asset, double free, double locked) {

    public ExchangeBalance {
        Objects.requireNonNull(asset, "asset");
        if (free < 0.0 || locked < 0.0) {
            throw new IllegalArgumentException("balances must not be negative");
        }
    }

    public double total() {
        return free + locked;
    }
}
