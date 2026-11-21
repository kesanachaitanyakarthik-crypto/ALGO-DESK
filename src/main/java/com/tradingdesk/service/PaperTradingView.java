package com.tradingdesk.service;

import java.util.List;

/**
 * Read-only view of the paper trading account for the UI.
 *
 * @param accountId    virtual account identifier
 * @param cash         available virtual cash, formatted
 * @param usedMargin   margin posted against open positions, formatted
 * @param equity       total virtual equity, formatted
 * @param realizedPnl  realised profit and loss, formatted
 * @param orders       recent paper orders, newest first
 * @param openCount    number of orders still working
 */
public record PaperTradingView(
        String accountId,
        String cash,
        String usedMargin,
        String equity,
        String realizedPnl,
        List<Row> orders,
        long openCount
) {

    /** One paper order row, pre-formatted for display. */
    public record Row(
            String id,
            String symbol,
            String side,
            String kind,
            String quantity,
            String filled,
            String averagePrice,
            String status,
            String reason
    ) {
    }

    public PaperTradingView {
        orders = List.copyOf(orders);
    }
}
