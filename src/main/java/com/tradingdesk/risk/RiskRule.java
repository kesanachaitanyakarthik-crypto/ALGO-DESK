package com.tradingdesk.risk;

public enum RiskRule {
    MAX_ORDER_QUANTITY("Maximum order quantity"),
    MAX_POSITION_SIZE("Maximum position size"),
    DAILY_MAX_LOSS("Daily maximum loss"),
    PRICE_DEVIATION("Price deviation"),
    DUPLICATE_ORDER("Duplicate order"),
    INSUFFICIENT_BALANCE("Insufficient balance"),
    MAX_OPEN_POSITIONS("Maximum open positions"),
    MAX_DAILY_ORDER_COUNT("Maximum daily order count");

    private final String label;

    RiskRule(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
