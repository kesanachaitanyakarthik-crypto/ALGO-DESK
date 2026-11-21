package com.tradingdesk.ui;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of everything the UI can display, captured once per shell tick.
 *
 * <p>Views are pure renderers of this record: they never touch {@code TradingService}
 * themselves, which keeps them free of concurrency concerns and makes the mapping testable.
 */
public record ViewSnapshot(
        long timestamp,
        boolean running,
        String databaseStatus,
        List<MarketRow> markets,
        Map<String, Double> prices,
        double averageSpread,
        PortfolioView portfolio,
        List<SignalRow> signals,
        SignalCounts signalCounts,
        List<OrderRow> openOrders,
        List<OrderRow> orderHistory,
        List<AlertRow> alerts,
        OrderSummary orderSummary,
        SystemStats system,
        IndicatorView indicators,
        List<String> logs
) {

    /** Portfolio figures, formatted for display. */
    public record PortfolioView(
            String totalEquity,
            String cash,
            String realizedPnl,
            String unrealizedPnl,
            String returnPercent,
            boolean pnlPositive
    ) {
    }

    /** A single generated trading signal. */
    public record SignalRow(
            String symbol,
            String type,
            String confidence,
            String rationale,
            String generatedAt
    ) {
    }

    /** BUY/SELL/HOLD totals for the distribution chart. */
    public record SignalCounts(long buy, long sell, long hold) {

        public long total() {
            return buy + sell + hold;
        }
    }

    /** A single order, open or historical. */
    public record OrderRow(
            String id,
            String symbol,
            String side,
            String quantity,
            String status,
            String reason,
            String createdAt
    ) {
    }

    /** A risk alert raised by the risk manager. */
    public record AlertRow(String rule, String symbol, String message, String severity, String raisedAt) {
    }

    /** Order counters. */
    public record OrderSummary(long submitted, long executed, long rejected, String rejectionRate) {
    }

    /** Concurrency and throughput metrics. */
    public record SystemStats(
            long eventsProcessed,
            long eventsDropped,
            int queueDepth,
            int queueCapacity,
            int activeWorkers,
            String averageLatency,
            String peakLatency,
            String throughput
    ) {
    }

    /** Indicator values for the analytics symbol. */
    public record IndicatorView(
            String symbol,
            String sma,
            String ema,
            String roc,
            String volatility,
            String imbalance,
            int samples,
            boolean reliable
    ) {
    }
}
