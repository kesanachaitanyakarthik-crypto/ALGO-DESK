package com.tradingdesk.marketdata;

import java.time.Instant;

/**
 * Health snapshot of a market data provider, for the UI status panel.
 *
 * @param provider      provider name
 * @param kind          provenance
 * @param connected     whether the provider is currently usable
 * @param lastUpdate    when the last successful data arrived, or null
 * @param detail        human-readable status or error text
 * @param requestsServed number of successful requests since start
 */
public record MarketDataStatus(
        String provider,
        ProviderKind kind,
        boolean connected,
        Instant lastUpdate,
        String detail,
        long requestsServed
) {

    public static MarketDataStatus disconnected(String provider, ProviderKind kind, String detail) {
        return new MarketDataStatus(provider, kind, false, null, detail, 0L);
    }

    /** Text suitable for a status label, always honest about the data source. */
    public String display() {
        String state = connected ? "Connected" : "Disconnected";
        return provider + " · " + kind.displayLabel() + " · " + state
                + (detail == null || detail.isBlank() ? "" : " · " + detail);
    }
}
