package com.tradingdesk.exchange;

import java.util.List;
import java.util.Optional;

/**
 * Venue-independent execution contract.
 *
 * <p>Implementations must never place a real order unless the {@link TradingModeGuard} reports live
 * mode as permitted. The guard is supplied at construction so adapters cannot opt out of it.
 */
public interface ExchangeClient extends AutoCloseable {

    /** Venue name, e.g. "mock" or "binance". */
    String venueName();

    /** True when this adapter targets a sandbox/testnet rather than production. */
    boolean isSandbox();

    /** Verifies credentials and connectivity. Returns false (never throws) on failure. */
    boolean authenticate();

    /** True when authenticated and usable. */
    boolean isAuthenticated();

    /** Account balances. */
    List<ExchangeBalance> balances();

    /** Open positions. */
    List<ExchangePosition> positions();

    /** Places an order. Throws {@link LiveTradingDisabledException} when live mode is refused. */
    ExchangeOrder placeOrder(ExchangeOrder request);

    /** Cancels an order by exchange id. */
    boolean cancelOrder(String symbol, String exchangeOrderId);

    /** Current status of an order. */
    Optional<ExchangeOrder> orderStatus(String symbol, String exchangeOrderId);

    /** Recent trades for a symbol. */
    List<ExchangeOrder> tradeHistory(String symbol, int limit);

    @Override
    default void close() {
        // stateless adapters need no teardown
    }

    /** Shared: the guard must be consulted before any state-changing call. */
    default void requireLiveAllowed(TradingModeGuard guard) {
        if (guard == null) {
            throw new LiveTradingDisabledException("No trading mode guard configured");
        }
        guard.requireLivePermitted();
    }
}
