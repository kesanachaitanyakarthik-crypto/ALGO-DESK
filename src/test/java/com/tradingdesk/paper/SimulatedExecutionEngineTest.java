package com.tradingdesk.paper;

import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.InstrumentType;
import com.tradingdesk.model.MarketEvent;
import com.tradingdesk.model.MarketEventType;
import com.tradingdesk.model.Side;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatedExecutionEngineTest {

    private static final Instrument AAPL = new Instrument("AAPL", "Apple Inc.", InstrumentType.EQUITY);
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    /** last=100, bid=99.9, ask=100.1, volume 10_000. */
    private static MarketEvent quote(double last, double bid, double ask, long volume) {
        return new MarketEvent(1, AAPL, MarketEventType.QUOTE, last, bid, ask, volume, NOW);
    }

    private static MarketEvent standardQuote() {
        return quote(100.0, 99.90, 100.10, 10_000);
    }

    private static WorkingOrder openMarket(Side side, long quantity) {
        return WorkingOrder.open(OrderRequest.market(AAPL, side, quantity), NOW).markOpen(NOW);
    }

    @Test
    void buyPaysAskAndSellReceivesBid() {
        SimulatedExecutionEngine engine = new SimulatedExecutionEngine(ExecutionCosts.frictionless());

        OrderFill buy = engine.match(openMarket(Side.BUY, 100), standardQuote(), NOW).orElseThrow();
        OrderFill sell = engine.match(openMarket(Side.SELL, 100), standardQuote(), NOW).orElseThrow();

        assertEquals(100.10, buy.price(), 1e-9, "a buy must cross the spread and pay the ask");
        assertEquals(99.90, sell.price(), 1e-9, "a sell must hit the bid");
    }

    @Test
    void spreadIsNeverIgnored() {
        SimulatedExecutionEngine engine = new SimulatedExecutionEngine(ExecutionCosts.frictionless());
        MarketEvent wide = quote(100.0, 95.0, 105.0, 10_000);

        double buy = engine.match(openMarket(Side.BUY, 10), wide, NOW).orElseThrow().price();
        double sell = engine.match(openMarket(Side.SELL, 10), wide, NOW).orElseThrow().price();

        assertTrue(buy - sell > 9.0, "a 10-wide spread must show up in the fills");
    }

    @Test
    void slippageIsAdverse() {
        SimulatedExecutionEngine engine = new SimulatedExecutionEngine(
                new ExecutionCosts(0.0, 50.0, 1.0, 0L)); // 50 bps

        double buy = engine.match(openMarket(Side.BUY, 10), standardQuote(), NOW).orElseThrow().price();
        double sell = engine.match(openMarket(Side.SELL, 10), standardQuote(), NOW).orElseThrow().price();

        assertTrue(buy > 100.10, "slippage must push a buy price up");
        assertTrue(sell < 99.90, "slippage must push a sell price down");
    }

    @Test
    void commissionIsChargedOnNotional() {
        SimulatedExecutionEngine engine = new SimulatedExecutionEngine(
                new ExecutionCosts(10.0, 0.0, 1.0, 0L)); // 10 bps

        OrderFill fill = engine.match(openMarket(Side.BUY, 100), standardQuote(), NOW).orElseThrow();

        double expected = fill.notional() * 0.001;
        assertEquals(expected, fill.fee(), 1e-6);
    }

    @Test
    void frictionlessCostsProduceNoFeeAndNoSlippage() {
        SimulatedExecutionEngine engine = new SimulatedExecutionEngine(ExecutionCosts.frictionless());
        OrderFill fill = engine.match(openMarket(Side.BUY, 100), standardQuote(), NOW).orElseThrow();

        assertEquals(0.0, fill.fee(), 1e-9);
        assertEquals(0.0, fill.slippage(), 1e-9);
    }

    @Test
    void partialFillsAreProducedByFillFraction() {
        SimulatedExecutionEngine engine = new SimulatedExecutionEngine(
                new ExecutionCosts(0.0, 0.0, 0.25, 0L)); // at most 25% per tick

        WorkingOrder order = openMarket(Side.BUY, 100);
        OrderFill fill = engine.match(order, standardQuote(), NOW).orElseThrow();

        assertEquals(25, fill.quantity(), "a 0.25 fraction of 100 must fill only 25 units");
    }

    @Test
    void liquidityStopsFillsBeyondAvailableVolume() {
        SimulatedExecutionEngine engine = new SimulatedExecutionEngine(
                new ExecutionCosts(0.0, 0.0, 1.0, 40L));

        OrderFill fill = engine.match(openMarket(Side.BUY, 100), standardQuote(), NOW).orElseThrow();
        assertEquals(40, fill.quantity());
    }

    @Test
    void quoteVolumeCapsFillSize() {
        SimulatedExecutionEngine engine = new SimulatedExecutionEngine(ExecutionCosts.frictionless());
        MarketEvent thin = quote(100.0, 99.9, 100.1, 5);

        OrderFill fill = engine.match(openMarket(Side.BUY, 100), thin, NOW).orElseThrow();
        assertEquals(5, fill.quantity(), "you cannot fill more than the book offers");
    }

    @Test
    void limitBuyOnlyFillsAtOrBelowLimit() {
        SimulatedExecutionEngine engine = new SimulatedExecutionEngine(ExecutionCosts.frictionless());

        WorkingOrder belowMarket = WorkingOrder.open(
                OrderRequest.limit(AAPL, Side.BUY, 10, 99.00), NOW).markOpen(NOW);
        assertEquals(Optional.empty(), engine.match(belowMarket, standardQuote(), NOW));

        WorkingOrder atMarket = WorkingOrder.open(
                OrderRequest.limit(AAPL, Side.BUY, 10, 101.00), NOW).markOpen(NOW);
        assertTrue(engine.match(atMarket, standardQuote(), NOW).isPresent());
    }

    @Test
    void stopBuyTriggersOnlyWhenPriceRisesToStop() {
        SimulatedExecutionEngine engine = new SimulatedExecutionEngine(ExecutionCosts.frictionless());
        WorkingOrder stop = WorkingOrder.open(
                OrderRequest.stop(AAPL, Side.BUY, 10, 101.0), NOW).markOpen(NOW);

        assertFalse(engine.isTriggered(stop, quote(100.0, 99.9, 100.1, 1000)));
        assertTrue(engine.isTriggered(stop, quote(101.5, 101.4, 101.6, 1000)));
    }

    @Test
    void stopSellTriggersOnlyWhenPriceFallsToStop() {
        SimulatedExecutionEngine engine = new SimulatedExecutionEngine(ExecutionCosts.frictionless());
        WorkingOrder stop = WorkingOrder.open(
                OrderRequest.stop(AAPL, Side.SELL, 10, 99.0), NOW).markOpen(NOW);

        assertFalse(engine.isTriggered(stop, quote(100.0, 99.9, 100.1, 1000)));
        assertTrue(engine.isTriggered(stop, quote(98.5, 98.4, 98.6, 1000)));
    }

    @Test
    void takeProfitTriggersAtTarget() {
        SimulatedExecutionEngine engine = new SimulatedExecutionEngine(ExecutionCosts.frictionless());
        WorkingOrder tp = WorkingOrder.open(
                OrderRequest.takeProfit(AAPL, Side.SELL, 10, 105.0), NOW).markOpen(NOW);

        assertFalse(engine.isTriggered(tp, quote(100.0, 99.9, 100.1, 1000)));
        assertTrue(engine.isTriggered(tp, quote(105.5, 105.4, 105.6, 1000)));
    }

    @Test
    void stopLimitRequiresBothConditions() {
        SimulatedExecutionEngine engine = new SimulatedExecutionEngine(ExecutionCosts.frictionless());
        // Buy triggered above 101, but only executes at or below 102.
        WorkingOrder order = WorkingOrder.open(
                OrderRequest.stopLimit(AAPL, Side.BUY, 10, 101.0, 102.0), NOW).markOpen(NOW);

        assertFalse(engine.isTriggered(order, quote(100.0, 99.9, 100.1, 1000)), "stop not hit");
        assertTrue(engine.isTriggered(order, quote(101.5, 101.4, 101.6, 1000)), "stop and limit hit");
        assertFalse(engine.isTriggered(order, quote(103.0, 102.9, 103.1, 1000)),
                "stop hit but ask above the limit");
    }

    @Test
    void terminalOrdersNeverMatch() {
        SimulatedExecutionEngine engine = new SimulatedExecutionEngine(ExecutionCosts.frictionless());
        WorkingOrder cancelled = openMarket(Side.BUY, 10).cancelled(NOW);

        assertEquals(Optional.empty(), engine.match(cancelled, standardQuote(), NOW));
    }
}
