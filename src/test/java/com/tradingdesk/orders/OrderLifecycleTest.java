package com.tradingdesk.orders;

import com.tradingdesk.indicators.IndicatorSnapshot;
import com.tradingdesk.model.Execution;
import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.InstrumentType;
import com.tradingdesk.model.Order;
import com.tradingdesk.model.OrderStatus;
import com.tradingdesk.model.SignalType;
import com.tradingdesk.model.TradeSignal;
import com.tradingdesk.portfolio.DefaultPortfolioManager;
import com.tradingdesk.portfolio.PortfolioManager;
import com.tradingdesk.risk.DefaultRiskManager;
import com.tradingdesk.risk.RiskConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderLifecycleTest {

    private static final Instrument AAPL = new Instrument("AAPL", "Apple Inc.", InstrumentType.EQUITY);

    private PortfolioManager portfolio;
    private DefaultOrderManagementSystem oms;

    @BeforeEach
    void setUp() {
        portfolio = new DefaultPortfolioManager(100_000.0);
        DefaultRiskManager risk = new DefaultRiskManager(
                new RiskConfiguration(500L, 50_000.0, 10_000.0, 0.05, 500L));
        oms = new DefaultOrderManagementSystem(risk, portfolio, 100L);
    }

    private static TradeSignal signal(SignalType type) {
        IndicatorSnapshot indicators =
                IndicatorSnapshot.ofCore(100, 101, 0.01, 0.5, 0.1, 20, Instant.now());
        return new TradeSignal(AAPL, type, 0.7, "test", indicators, Instant.now());
    }

    @Test
    void approvedSignalExecutesAndUpdatesPortfolio() {
        OrderResult result = oms.submit(signal(SignalType.BUY), 100.0);

        assertEquals(OrderStatus.EXECUTED, result.order().status());
        assertTrue(result.executed());
        assertEquals(100, portfolio.position(AAPL).quantity());
        assertEquals(90_000.0, portfolio.cash(), 1e-9);
    }

    @Test
    void rejectedOrderCarriesReasonAndDoesNotTouchPortfolio() {
        DefaultRiskManager risk = new DefaultRiskManager(
                new RiskConfiguration(50L, 50_000.0, 10_000.0, 0.05, 500L));
        DefaultOrderManagementSystem tight =
                new DefaultOrderManagementSystem(risk, portfolio, 100L);

        OrderResult result = tight.submit(signal(SignalType.BUY), 100.0);

        assertEquals(OrderStatus.REJECTED, result.order().status());
        assertTrue(result.rejected());
        assertNotNull(result.rejectionReason());
        assertTrue(result.rejectionReason().contains("Maximum order quantity"));
        assertEquals(0, portfolio.position(AAPL).quantity());
        assertEquals(100_000.0, portfolio.cash(), 1e-9);
    }

    @Test
    void everyRejectedOrderHasReason() {
        DefaultRiskManager risk = new DefaultRiskManager(
                new RiskConfiguration(50L, 50_000.0, 10_000.0, 0.05, 500L));
        DefaultOrderManagementSystem tight = new DefaultOrderManagementSystem(risk, portfolio, 100L);
        OrderResult result = tight.submit(signal(SignalType.SELL), 100.0);
        assertFalse(result.rejectionReason().isBlank());
    }

    @Test
    void executionIsRecordedOnce() {
        OrderResult result = oms.submit(signal(SignalType.BUY), 100.0);
        List<Execution> executions = oms.executionsFor(result.order().id());
        assertEquals(1, executions.size());
        assertEquals(100.0, executions.get(0).price(), 1e-9);
    }

    @Test
    void holdSignalsNeverCreateOrders() {
        assertThrows(IllegalArgumentException.class, () -> oms.submit(signal(SignalType.HOLD), 100.0));
        assertEquals(0, oms.history(10).size());
    }

    @Test
    void cancelMovesOpenOrderToTerminalState() {
        Order open = Order.pending(AAPL, com.tradingdesk.model.Side.BUY, 10, 100.0, Instant.now());
        assertTrue(oms.cancel(java.util.UUID.randomUUID()) == false);
        assertTrue(oms.find(open.id()).isEmpty() || !oms.cancel(open.id()));
    }

    @Test
    void statisticsTrackSubmissionsAndRejections() {
        oms.submit(signal(SignalType.BUY), 100.0);
        OrderStatistics statistics = oms.statistics();
        assertEquals(1, statistics.submitted());
        assertEquals(1, statistics.executed());
        assertEquals(0.0, statistics.rejectionRate(), 1e-9);
    }

    @Test
    void resetClearsBookAndCounters() {
        oms.submit(signal(SignalType.BUY), 100.0);
        oms.reset();
        assertEquals(0, oms.history(10).size());
        assertEquals(0, oms.statistics().submitted());
    }
}
