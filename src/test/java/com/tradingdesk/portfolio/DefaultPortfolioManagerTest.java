package com.tradingdesk.portfolio;

import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.InstrumentType;
import com.tradingdesk.model.PortfolioSnapshot;
import com.tradingdesk.model.Side;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPortfolioManagerTest {

    private static final Instrument AAPL = new Instrument("AAPL", "Apple Inc.", InstrumentType.EQUITY);

    @Test
    void buyingAddsPositionAndReducesCash() {
        DefaultPortfolioManager manager = new DefaultPortfolioManager(10_000.0);
        manager.applyFill(AAPL, Side.BUY, 10, 100.0);
        assertEquals(10, manager.position(AAPL).quantity());
        assertEquals(9_000.0, manager.cash(), 1e-9);
    }

    @Test
    void averagePriceReflectsAdditionalBuys() {
        DefaultPortfolioManager manager = new DefaultPortfolioManager(10_000.0);
        manager.applyFill(AAPL, Side.BUY, 10, 100.0);
        manager.applyFill(AAPL, Side.BUY, 10, 200.0);
        assertEquals(20, manager.position(AAPL).quantity());
        assertEquals(150.0, manager.position(AAPL).averagePrice(), 1e-9);
    }

    @Test
    void sellingRealizesProfitLoss() {
        DefaultPortfolioManager manager = new DefaultPortfolioManager(10_000.0);
        manager.applyFill(AAPL, Side.BUY, 10, 100.0);
        manager.applyFill(AAPL, Side.SELL, 10, 120.0);
        assertEquals(0, manager.position(AAPL).quantity());
        assertTrue(manager.position(AAPL).isFlat());
        assertEquals(200.0, manager.realizedProfitLoss(), 1e-9);
        assertEquals(10_200.0, manager.cash(), 1e-9);
    }

    @Test
    void sellingWithoutAPositionOpensAShortInsteadOfPhantomEquity() {
        DefaultPortfolioManager manager = new DefaultPortfolioManager(10_000.0);
        manager.applyFill(AAPL, Side.SELL, 10, 100.0);

        assertTrue(manager.position(AAPL).isShort());
        assertEquals(-10, manager.position(AAPL).quantity());
        assertEquals(11_000.0, manager.cash(), 1e-9);

        PortfolioSnapshot snapshot = manager.snapshot(Map.of("AAPL", 100.0));
        assertEquals(10_000.0, snapshot.totalEquity(), 1e-9,
                "opening a short must not create equity out of nothing");
    }

    @Test
    void shortPositionGainsWhenPriceFalls() {
        DefaultPortfolioManager manager = new DefaultPortfolioManager(10_000.0);
        manager.applyFill(AAPL, Side.SELL, 10, 100.0);

        PortfolioSnapshot snapshot = manager.snapshot(Map.of("AAPL", 90.0));
        assertEquals(100.0, snapshot.unrealizedProfitLoss(), 1e-9,
                "short 10 @ 100 marked at 90 is a 100 gain");
        assertEquals(10_100.0, snapshot.totalEquity(), 1e-9);
    }

    @Test
    void coveringAShortAtLowerPriceRealizesProfit() {
        DefaultPortfolioManager manager = new DefaultPortfolioManager(10_000.0);
        manager.applyFill(AAPL, Side.SELL, 10, 100.0);
        manager.applyFill(AAPL, Side.BUY, 10, 90.0);

        assertTrue(manager.position(AAPL).isFlat());
        assertEquals(100.0, manager.realizedProfitLoss(), 1e-9);
        assertEquals(10_100.0, manager.cash(), 1e-9);
    }

    @Test
    void snapshotMarksPositionsToMarket() {
        DefaultPortfolioManager manager = new DefaultPortfolioManager(10_000.0);
        manager.applyFill(AAPL, Side.BUY, 10, 100.0);
        PortfolioSnapshot snapshot = manager.snapshot(Map.of("AAPL", 130.0));
        assertEquals(9_000.0, snapshot.cash(), 1e-9);
        assertEquals(1_300.0, snapshot.marketValue(), 1e-9);
        assertEquals(300.0, snapshot.unrealizedProfitLoss(), 1e-9);
        assertEquals(10_300.0, snapshot.totalEquity(), 1e-9);
    }

    @Test
    void closedPositionsDoNotContributeToMarketValue() {
        DefaultPortfolioManager manager = new DefaultPortfolioManager(10_000.0);
        manager.applyFill(AAPL, Side.BUY, 10, 100.0);
        manager.applyFill(AAPL, Side.SELL, 10, 100.0);

        PortfolioSnapshot snapshot = manager.snapshot(Map.of("AAPL", 150.0));
        assertEquals(0.0, snapshot.marketValue(), 1e-9);
        assertEquals(10_000.0, snapshot.totalEquity(), 1e-9);
    }

    @Test
    void projectedExposureUsesAbsoluteNotional() {
        DefaultPortfolioManager manager = new DefaultPortfolioManager(10_000.0);
        manager.applyFill(AAPL, Side.BUY, 10, 100.0);
        Map<String, Double> prices = Map.of("AAPL", 100.0);
        assertEquals(2_000.0, manager.projectedExposure(AAPL, Side.BUY, 10, 100.0, prices), 1e-9);
        assertEquals(0.0, manager.projectedExposure(AAPL, Side.SELL, 10, 100.0, prices), 1e-9);
    }

    @Test
    void exposureCountsShortNotional() {
        DefaultPortfolioManager manager = new DefaultPortfolioManager(10_000.0);
        manager.applyFill(AAPL, Side.SELL, 10, 100.0);
        assertEquals(1_000.0, manager.totalExposure(Map.of("AAPL", 100.0)), 1e-9);
    }

    @Test
    void resetRestoresStartingState() {
        DefaultPortfolioManager manager = new DefaultPortfolioManager(1_000.0);
        manager.applyFill(AAPL, Side.BUY, 5, 100.0);
        manager.reset(2_000.0);
        assertEquals(2_000.0, manager.cash(), 1e-9);
        assertTrue(manager.position(AAPL).isFlat());
        assertFalse(manager.position(AAPL).isShort());
    }

    @Test
    void rejectsInvalidFills() {
        DefaultPortfolioManager manager = new DefaultPortfolioManager(1_000.0);
        assertThrows(IllegalArgumentException.class, () -> manager.applyFill(AAPL, Side.BUY, 0, 100.0));
        assertThrows(IllegalArgumentException.class, () -> manager.applyFill(AAPL, Side.BUY, 1, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new DefaultPortfolioManager(-1.0));
    }
}
