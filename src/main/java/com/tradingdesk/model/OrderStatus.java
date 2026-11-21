package com.tradingdesk.model;

/**
 * Order lifecycle status.
 *
 * <p>The original five constants ({@code PENDING}, {@code APPROVED}, {@code REJECTED},
 * {@code EXECUTED}, {@code CANCELLED}) are unchanged and still drive the automated signal path.
 * The paper-trading engine additionally uses the richer {@code NEW → OPEN → PARTIALLY_FILLED →
 * FILLED} progression; {@code FILLED} is treated as equivalent to {@code EXECUTED} by
 * {@link #isFilled()} so both paths agree on what "done" means.
 */
public enum OrderStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXECUTED,
    CANCELLED,

    /** Created but not yet accepted onto the book. */
    NEW,
    /** Working on the book, awaiting execution. */
    OPEN,
    /** Partially executed; still working. */
    PARTIALLY_FILLED,
    /** Fully executed (paper engine). */
    FILLED;

    /** True when the order is completely done, by either lifecycle vocabulary. */
    public boolean isFilled() {
        return this == EXECUTED || this == FILLED;
    }

    public boolean isTerminal() {
        return this == REJECTED || this == EXECUTED || this == FILLED || this == CANCELLED;
    }

    public boolean isOpen() {
        return this == PENDING || this == APPROVED || this == NEW
                || this == OPEN || this == PARTIALLY_FILLED;
    }
}
