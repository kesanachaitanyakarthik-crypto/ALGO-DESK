package com.tradingdesk.model;

import java.util.Objects;

public enum OrderStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXECUTED,
    CANCELLED;

    public boolean isTerminal() {
        return this == REJECTED || this == EXECUTED || this == CANCELLED;
    }

    public boolean isOpen() {
        return this == PENDING || this == APPROVED;
    }
}
