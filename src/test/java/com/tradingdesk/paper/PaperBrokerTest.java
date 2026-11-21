package com.tradingdesk.paper;

import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.InstrumentType;
import com.tradingdesk.model.MarketEvent;
import com.tradingdesk.model.MarketEventType;
import com.tradingdesk.model.OrderStatus;
import com.tradingdesk.model.Side;
import com.tradingdesk.portfolio.DefaultPortfolioManager;
import com.tradingdesk.portfolio.PortfolioManager;
import com.tradingdesk.risk.DefaultRiskManager;
import com.tradingdesk.risk.RiskConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperBrokerTest {

    private static final Instrument AAPL = new Instrument("AAPL", "Apple Inc.", InstrumentType.EQUITY);
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private PaperAccount account;
    private PortfolioManager portfolio;
    private PaperBroker broker;

    @BeforeEach
    void setUp() {
        account = new PaperAccount("acc", "USD", 100_000.0);
        portfolio = new DefaultPortfolioManager(100_000.0);
        DefaultRiskManager risk = new DefaultRiskManager(
                new RiskConfiguration(1_000L, 250_000.0, 25_000.0, 0.5, 750L));
        broker = new PaperBroker(account, portfolio, risk,
                new SimulatedExecutionEngine(ExecutionCosts.frictionless()));
    }

    private static MarketEvent quote(double last, double bid, double ask, long volume) {
        return new MarketEvent(1, AAPL, MarketEventType.QUOTE, last, bid, ask, volume, NOW);
    }

    private static MarketEvent mid() {
        return quote(100.0, 99.9, 100.1, 100_000);
    }

    @Test
    void marketBuyExecutesAtAskAndUpdatesPortfolio() {
        PaperBroker.Submission submission = broker.submit(OrderRequest.market(AAPL, Side.BUY, 100), 100.0);
        assertFalse(submission.rejected());
        assertEquals(OrderStatus.OPEN, submission.order().status());

        broker.onMarketEvent(mid());

        WorkingOrder filled = broker.find(submission.order().id()).orElseThrow();
        assertEquals(OrderStatus.FILLED, filled.status());
        assertEquals(100.10, filled.averagePrice(), 1e-9);
        assertEquals(100, portfolio.position(AAPL).quantity());
        assertEquals(100_000.0 - (100 * 100.10), account.cash(), 1e-6);
    }

    @Test
    void sellExecutesAtBid() {
        PaperBroker.Submission submission = broker.submit(OrderRequest.market(AAPL, Side.SELL, 50), 100.0);
        broker.onMarketEvent(mid());

        WorkingOrder filled = broker.find(submission.order().id()).orElseThrow();
        assertEquals(99.90, filled.averagePrice(), 1e-9);
    }

    @Test
    void marketOrderDoesNotFillBeforeAnyTickArrives() {
        PaperBroker.Submission submission = broker.submit(OrderRequest.market(AAPL, Side.BUY, 10), 100.0);
        assertEquals(OrderStatus.OPEN, submission.order().status());
        assertEquals(0, broker.find(submission.order().id()).orElseThrow().filledQuantity());
    }

    @Test
    void insufficientBalanceIsRejectedWithReason() {
        PaperAccount poor = new PaperAccount("poor", "USD", 500.0);
        PaperBroker tinyBroker = new PaperBroker(poor, portfolio,
                new DefaultRiskManager(new RiskConfiguration(1_000L, 250_000.0, 25_000.0, 0.5, 750L)),
                new SimulatedExecutionEngine(ExecutionCosts.frictionless()));

        PaperBroker.Submission submission = tinyBroker.submit(
                OrderRequest.market(AAPL, Side.BUY, 100), 100.0);

        assertEquals(OrderStatus.REJECTED, submission.order().status());
        assertTrue(submission.order().rejectionReason().contains("Insufficient balance"));
        assertEquals(0, portfolio.position(AAPL).quantity());
    }

    @Test
    void riskLimitsStillReject() {
        DefaultRiskManager tight = new DefaultRiskManager(
                new RiskConfiguration(50L, 250_000.0, 25_000.0, 0.5, 750L));
        PaperBroker tightBroker = new PaperBroker(account, portfolio, tight,
                new SimulatedExecutionEngine(ExecutionCosts.frictionless()));

        PaperBroker.Submission submission = tightBroker.submit(
                OrderRequest.market(AAPL, Side.BUY, 100), 100.0);

        assertEquals(OrderStatus.REJECTED, submission.order().status());
        assertTrue(submission.rejected());
        assertTrue(submission.order().rejectionReason().contains("Maximum order quantity"));
    }

    @Test
    void limitOrderFillsOnlyWhenPriceCrosses() {
        PaperBroker.Submission submission = broker.submit(
                OrderRequest.limit(AAPL, Side.BUY, 10, 95.0), 100.0);

        broker.onMarketEvent(mid());
        assertEquals(0, broker.find(submission.order().id()).orElseThrow().filledQuantity(),
                "a 95 limit must not fill at 100");

        broker.onMarketEvent(quote(94.0, 93.9, 94.1, 10_000));
        WorkingOrder filled = broker.find(submission.order().id()).orElseThrow();
        assertEquals(OrderStatus.FILLED, filled.status());
    }

    @Test
    void stopOrderTriggersWhenPriceHitsStop() {
        PaperBroker.Submission submission = broker.submit(
                OrderRequest.stop(AAPL, Side.BUY, 10, 102.0), 100.0);

        broker.onMarketEvent(mid());
        assertEquals(OrderStatus.OPEN, broker.find(submission.order().id()).orElseThrow().status());

        broker.onMarketEvent(quote(102.5, 102.4, 102.6, 10_000));
        assertEquals(OrderStatus.FILLED, broker.find(submission.order().id()).orElseThrow().status());
    }

    @Test
    void partialFillsAccumulateUntilComplete() {
        PaperBroker partialBroker = new PaperBroker(account, portfolio,
                new DefaultRiskManager(new RiskConfiguration(1_000L, 250_000.0, 25_000.0, 0.5, 750L)),
                new SimulatedExecutionEngine(new ExecutionCosts(0.0, 0.0, 0.5, 0L)));

        PaperBroker.Submission submission = partialBroker.submit(
                OrderRequest.market(AAPL, Side.BUY, 100), 100.0);

        broker = partialBroker;
        partialBroker.onMarketEvent(mid());
        WorkingOrder afterFirst = partialBroker.find(submission.order().id()).orElseThrow();
        assertEquals(OrderStatus.PARTIALLY_FILLED, afterFirst.status());
        assertEquals(50, afterFirst.filledQuantity());

        partialBroker.onMarketEvent(mid());
        assertEquals(OrderStatus.FILLED,
                partialBroker.find(submission.order().id()).orElseThrow().status());
    }

    @Test
    void cancelStopsFurtherExecution() {
        PaperBroker.Submission submission = broker.submit(OrderRequest.market(AAPL, Side.BUY, 10), 100.0);

        assertTrue(broker.cancel(submission.order().id()));
        assertEquals(OrderStatus.CANCELLED, broker.find(submission.order().id()).orElseThrow().status());

        broker.onMarketEvent(mid());
        assertEquals(0, broker.find(submission.order().id()).orElseThrow().filledQuantity());
    }

    @Test
    void cancellingTerminalOrderIsRefused() {
        PaperBroker.Submission submission = broker.submit(OrderRequest.market(AAPL, Side.BUY, 10), 100.0);
        broker.onMarketEvent(mid());

        assertFalse(broker.cancel(submission.order().id()));
        assertFalse(broker.cancel(UUID.randomUUID()));
    }

    @Test
    void modifyUpdatesParametersBeforeAnyFill() {
        PaperBroker.Submission submission = broker.submit(
                OrderRequest.limit(AAPL, Side.BUY, 10, 90.0), 100.0);

        assertTrue(broker.modify(submission.order().id(),
                OrderRequest.limit(AAPL, Side.BUY, 20, 95.0)).isPresent());

        WorkingOrder modified = broker.find(submission.order().id()).orElseThrow();
        assertEquals(20, modified.quantity());
        assertEquals(95.0, modified.request().limitPrice(), 1e-9);
    }

    @Test
    void invalidMarketPriceIsRejected() {
        PaperBroker.Submission submission = broker.submit(OrderRequest.market(AAPL, Side.BUY, 10), 0.0);
        assertEquals(OrderStatus.REJECTED, submission.order().status());
    }

    @Test
    void openOrdersExcludeTerminalOnes() {
        // A limit order far from the market stays open; a market order fills immediately.
        PaperBroker.Submission resting = broker.submit(
                OrderRequest.limit(AAPL, Side.BUY, 10, 50.0), 100.0);
        PaperBroker.Submission filled = broker.submit(
                OrderRequest.market(AAPL, Side.BUY, 10), 100.0);
        broker.onMarketEvent(mid());

        List<WorkingOrder> open = broker.openOrders();
        assertTrue(open.stream().anyMatch(o -> o.id().equals(resting.order().id())),
                "the resting limit order should still be open");
        assertTrue(open.stream().noneMatch(o -> o.id().equals(filled.order().id())),
                "the filled market order must not appear among open orders");
        assertTrue(open.stream().noneMatch(o -> o.status().isTerminal()));
    }

    @Test
    void feesAreVisibleOnTheFilledOrder() {
        PaperBroker feeBroker = new PaperBroker(
                new PaperAccount("fee", "USD", 100_000.0),
                new DefaultPortfolioManager(100_000.0),
                new DefaultRiskManager(new RiskConfiguration(1_000L, 250_000.0, 25_000.0, 0.5, 750L)),
                new SimulatedExecutionEngine(new ExecutionCosts(10.0, 0.0, 1.0, 0L)));

        PaperBroker.Submission submission = feeBroker.submit(
                OrderRequest.market(AAPL, Side.BUY, 100), 100.0);
        feeBroker.onMarketEvent(mid());

        WorkingOrder filled = feeBroker.find(submission.order().id()).orElseThrow();
        assertTrue(filled.feesPaid() > 0.0, "commission must be booked on the order");
        assertEquals(1, filled.fills().size());
    }

    @Test
    void executionsAreExposedForPersistence() {
        PaperBroker.Submission submission = broker.submit(OrderRequest.market(AAPL, Side.BUY, 10), 100.0);
        broker.onMarketEvent(mid());

        assertEquals(1, broker.executionsFor(submission.order().id()).size());
        assertEquals(10, broker.executionsFor(submission.order().id()).get(0).quantity());
    }
}
