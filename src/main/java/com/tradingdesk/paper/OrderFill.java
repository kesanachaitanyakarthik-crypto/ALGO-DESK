package com.tradingdesk.paper;

import java.time.Instant;
import java.util.Objects;

/**
 * One execution against a paper order.
 *
 * @param quantity  units filled in this execution
 * @param price     price actually achieved (already includes spread and slippage)
 * @param fee       commission charged for this fill
 * @param slippage  adverse price movement in currency terms, for transparency in the UI
 */
public record OrderFill(long quantity, double price, double fee, double slippage, Instant filledAt) {

    public OrderFill {
        Objects.requireNonNull(filledAt, "filledAt");
        if (quantity <= 0) {
            throw new IllegalArgumentException("fill quantity must be positive");
        }
        if (!Double.isFinite(price) || price <= 0.0) {
            throw new IllegalArgumentException("fill price must be positive");
        }
        if (fee < 0.0) {
            throw new IllegalArgumentException("fee must not be negative");
        }
    }

    /** Cash value of the fill, excluding fees. */
    public double notional() {
        return quantity * price;
    }
}
