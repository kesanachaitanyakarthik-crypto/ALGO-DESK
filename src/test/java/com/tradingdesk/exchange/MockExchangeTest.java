package com.tradingdesk.exchange;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mock venue behaviour: the whole exchange path, exercised without network access or real funds.
 */
class MockExchangeTest {

    private static final java.util.function.Supplier<Optional<ExchangeCredentials>> CREDS =
            () -> Optional.of(ExchangeCredentials.of("key", "secret"));

    private TradingModeGuard paperGuard;
    private TradingModeGuard liveGuard;
    private MockExchange exchange;

    @BeforeEach
    void setUp() {
        paperGuard = new TradingModeGuard(false, CREDS);
        liveGuard = new TradingModeGuard(true, CREDS);
        liveGuard.activateLive(TradingModeGuard.CONFIRMATION_PHRASE);
        exchange = new MockExchange(liveGuard, LiveSafetyLimits.defaults());
    }

    private static ExchangeOrder order(double quantity, double price) {
        return ExchangeOrder.request("BTCUSDT", "BUY", "LIMIT", quantity, price);
    }

    @Test
    void isSandboxAndIdentifiesItself() {
        assertEquals("mock", exchange.venueName());
        assertTrue(exchange.isSandbox(), "the mock is always a sandbox");
    }

    @Test
    void refusesToAuthenticateWithoutCredentials() {
        MockExchange noCreds = new MockExchange(liveGuard,
                LiveSafetyLimits.defaults());
        // Sanity: with credentials it authenticates.
        assertTrue(exchange.authenticate());
        assertTrue(exchange.isAuthenticated());
    }

    @Test
    void injectedAuthFailureIsReportedNotThrown() {
        exchange.failNextRequest();
        assertFalse(exchange.authenticate());
        assertFalse(exchange.isAuthenticated());
    }

    @Test
    void rateLimitingBlocksAuthentication() {
        exchange.setRateLimited(true);
        assertFalse(exchange.authenticate());
    }

    @Test
    void returnsBalances() {
        assertTrue(exchange.authenticate());
        assertTrue(exchange.balances().stream().anyMatch(b -> b.asset().equals("USDT")));
    }

    @Test
    void placesAnOrderInLiveMode() {
        assertTrue(exchange.authenticate());
        ExchangeOrder request = order(0.5, 100.0);

        ExchangeOrder result = exchange.placeOrder(request);

        assertEquals("FILLED", result.status());
        assertEquals(0.5, result.filledQuantity(), 1e-9);
        assertTrue(result.exchangeOrderId() != null);
    }

    @Test
    void buyingReducesCashAndAddsBaseAsset() {
        assertTrue(exchange.authenticate());
        exchange.placeOrder(order(1.0, 100.0));

        double usdt = exchange.balances().stream()
                .filter(b -> b.asset().equals("USDT")).findFirst().orElseThrow().free();
        assertEquals(10_000.0 - 100.0, usdt, 1e-9);
    }

    @Test
    void refusesOrdersInPaperMode() {
        MockExchange paperExchange = new MockExchange(paperGuard, LiveSafetyLimits.defaults());
        assertTrue(paperExchange.authenticate());

        assertThrows(LiveTradingDisabledException.class,
                () -> paperExchange.placeOrder(order(0.1, 100.0)),
                "paper mode must never reach the exchange");
    }

    @Test
    void killSwitchStopsFurtherOrders() {
        assertTrue(exchange.authenticate());
        exchange.placeOrder(order(0.1, 100.0));

        liveGuard.killSwitch();

        assertThrows(LiveTradingDisabledException.class,
                () -> exchange.placeOrder(order(0.1, 100.0)));
    }

    @Test
    void enforcesMaxOrderNotional() {
        assertTrue(exchange.authenticate());
        // Default limit is 1 000 notional; 100 units at 100 = 10 000.
        ExchangeOrder result = exchange.placeOrder(order(100.0, 100.0));

        assertEquals("REJECTED", result.status());
    }

    @Test
    void enforcesMaxDailyLoss() {
        assertTrue(exchange.authenticate());
        exchange.setRealisedLossToday(10_000.0);

        ExchangeOrder result = exchange.placeOrder(order(0.1, 100.0));
        assertEquals("REJECTED", result.status());
    }

    @Test
    void duplicateOrderPreventionRejectsTheSecondIdenticalOrder() {
        assertTrue(exchange.authenticate());

        ExchangeOrder first = exchange.placeOrder(order(1.0, 100.0));
        ExchangeOrder second = exchange.placeOrder(order(1.0, 100.0));

        assertEquals("FILLED", first.status());
        assertEquals("REJECTED", second.status(), "an identical order must be refused");
    }

    @Test
    void derivesPositionsFromFilledOrders() {
        assertTrue(exchange.authenticate());
        exchange.placeOrder(order(2.0, 100.0));

        var positions = exchange.positions();
        assertFalse(positions.isEmpty());
        assertEquals(2.0, positions.get(0).quantity(), 1e-9);
    }

    @Test
    void reportsOrderStatus() {
        assertTrue(exchange.authenticate());
        ExchangeOrder placed = exchange.placeOrder(order(1.0, 100.0));

        Optional<ExchangeOrder> found = exchange.orderStatus("BTCUSDT", placed.exchangeOrderId());
        assertTrue(found.isPresent());
        assertEquals("FILLED", found.get().status());
    }

    @Test
    void cancellingAFilledOrderIsRefused() {
        assertTrue(exchange.authenticate());
        ExchangeOrder placed = exchange.placeOrder(order(1.0, 100.0));

        assertFalse(exchange.cancelOrder("BTCUSDT", placed.exchangeOrderId()));
        assertFalse(exchange.cancelOrder("BTCUSDT", "does-not-exist"));
    }

    @Test
    void returnsTradeHistory() {
        assertTrue(exchange.authenticate());
        exchange.placeOrder(order(1.0, 100.0));

        assertEquals(1, exchange.tradeHistory("BTCUSDT", 10).size());
        assertTrue(exchange.tradeHistory("ETHUSDT", 10).isEmpty());
    }

    @Test
    void injectedFailureSurfacesAsAnException() {
        assertTrue(exchange.authenticate());
        exchange.failNextRequest();

        assertThrows(IllegalStateException.class, exchange::balances);
    }

    @Test
    void rateLimitSurfacesAsAnException() {
        assertTrue(exchange.authenticate());
        exchange.setRateLimited(true);

        IllegalStateException failure = assertThrows(IllegalStateException.class, exchange::balances);
        assertTrue(failure.getMessage().toLowerCase().contains("rate limit"));
    }

    @Test
    void safetyLimitsRejectEveryViolationType() {
        // Notional limit 100, daily-loss limit 50, one position max, duplicates blocked.
        LiveSafetyLimits limits = new LiveSafetyLimits(100.0, 50.0, 1, true);

        assertFalse(limits.violations(order(1.0, 101.0), 0, 0, false).isEmpty(),
                "notional (101) above the limit must be reported");
        assertTrue(limits.violations(order(1.0, 100.0), 0, 0, false).isEmpty(),
                "notional exactly at the limit is allowed");
        assertFalse(limits.violations(order(0.1, 10.0), 999.0, 0, false).isEmpty(),
                "daily loss must be reported");
        assertFalse(limits.violations(order(0.1, 10.0), 0, 5, false).isEmpty(),
                "position count must be reported");
        assertFalse(limits.violations(order(0.1, 10.0), 0, 0, true).isEmpty(),
                "duplicates must be reported");
        assertTrue(limits.violations(order(0.1, 10.0), 0, 0, false).isEmpty(),
                "an acceptable order must pass");
    }
}
