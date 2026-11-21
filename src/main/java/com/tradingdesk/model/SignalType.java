package com.tradingdesk.model;

public enum SignalType {
    BUY,
    SELL,
    HOLD;

    public static SignalType fromSide(Side side) {
        return side == Side.BUY ? BUY : SELL;
    }
}
