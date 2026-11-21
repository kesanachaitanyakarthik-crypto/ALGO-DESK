package com.tradingdesk.ui;

/**
 * One market row, already formatted for display.
 *
 * <p>Values are pre-formatted in the snapshot builder so table cells stay dumb and the
 * formatting logic is testable without a JavaFX toolkit.
 *
 * @param symbol        ticker
 * @param last          formatted last price
 * @param changePercent formatted percentage change from session open, with sign
 * @param volume        formatted traded volume
 * @param up            true when the session change is positive
 */
public record MarketRow(
        String symbol,
        String last,
        String changePercent,
        String volume,
        boolean up
) {
}
