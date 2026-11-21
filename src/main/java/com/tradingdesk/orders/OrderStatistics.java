package com.tradingdesk.orders;

public record OrderStatistics(long submitted, long approved, long rejected, long executed,
                              long cancelled, long open) {

    public double rejectionRate() {
        return submitted == 0 ? 0.0 : (double) rejected / submitted;
    }
}
