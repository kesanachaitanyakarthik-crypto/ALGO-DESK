package com.tradingdesk.ui;

import com.tradingdesk.indicators.IndicatorSnapshot;
import com.tradingdesk.model.AlertSeverity;
import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.InstrumentType;
import com.tradingdesk.model.Order;
import com.tradingdesk.model.PortfolioSnapshot;
import com.tradingdesk.model.RiskAlert;
import com.tradingdesk.model.Side;
import com.tradingdesk.model.SignalType;
import com.tradingdesk.model.TradeSignal;
import com.tradingdesk.orders.OrderStatistics;
import com.tradingdesk.service.TradingService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for the display mapping. No JavaFX toolkit is started.
 */
class SnapshotBuilderTest {

    private static final Instrument AAPL = new Instrument("AAPL", "Apple Inc.", InstrumentType.EQUITY);
    private static final Instrument MSFT = new Instrument("MSFT", "Microsoft Corp.", InstrumentType.EQUITY);

    @Test
    void signedPercentMarksGainsAndLosses() {
        assertEquals("+1.25%", SnapshotBuilder.signedPercent(1.25));
        assertEquals("-0.40%", SnapshotBuilder.signedPercent(-0.40));
        assertEquals("0.00%", SnapshotBuilder.signedPercent(0.0));
    }

    @Test
    void percentChangeHandlesMissingAndZeroBaseline() {
        assertEquals(0.0, SnapshotBuilder.percentChange(null, 100.0), 1e-9);
        assertEquals(0.0, SnapshotBuilder.percentChange(0.0, 100.0), 1e-9);
        assertEquals(10.0, SnapshotBuilder.percentChange(100.0, 110.0), 1e-9);
        assertEquals(-50.0, SnapshotBuilder.percentChange(100.0, 50.0), 1e-9);
    }

    @Test
    void countsSignalsByType() {
        List<TradeSignal> signals = List.of(
                signal(SignalType.BUY), signal(SignalType.BUY), signal(SignalType.SELL),
                signal(SignalType.HOLD));
        ViewSnapshot.SignalCounts counts = SnapshotBuilder.countSignals(signals);
        assertEquals(2, counts.buy());
        assertEquals(1, counts.sell());
        assertEquals(1, counts.hold());
        assertEquals(4, counts.total());
    }

    @Test
    void shortIdTruncatesUuid() {
        assertEquals("12345678", SnapshotBuilder.shortId("12345678-90ab-cdef-1234-567890abcdef"));
        assertEquals("-", SnapshotBuilder.shortId("-"));
    }

    @Test
    void microsFormatsToTenth() {
        assertEquals("12.3 us", SnapshotBuilder.micros(12.34));
        assertEquals("0.0 us", SnapshotBuilder.micros(0.0));
    }

    @Test
    void buildMapsServiceDataIntoRows() {
        FakeTradingService service = new FakeTradingService();
        service.prices = Map.of("AAPL", 110.0, "MSFT", 240.0);
        service.volumes = Map.of("AAPL", 1_500L, "MSFT", 900L);
        service.signals = List.of(signal(SignalType.BUY));
        service.portfolio = new PortfolioSnapshot(90_000.0, 20_000.0, 250.0, -125.0, Instant.now());

        SnapshotBuilder builder = new SnapshotBuilder(service, 100_000.0);
        ViewSnapshot snapshot = builder.build();

        assertEquals(2, snapshot.markets().size());
        MarketRow aapl = snapshot.markets().stream()
                .filter(row -> row.symbol().equals("AAPL")).findFirst().orElseThrow();
        assertEquals("110.0000", aapl.last());
        assertEquals("0.00%", aapl.changePercent(), "first observation establishes the session baseline");
        assertEquals("1500", aapl.volume());

        assertEquals("110000.00", snapshot.portfolio().totalEquity());
        assertTrue(snapshot.portfolio().pnlPositive());
        assertEquals("+10.00%", snapshot.portfolio().returnPercent());
        assertEquals(1, snapshot.signalCounts().buy());
    }

    @Test
    void secondBuildComputesChangeAgainstBaseline() {
        FakeTradingService service = new FakeTradingService();
        service.prices = Map.of("AAPL", 100.0);

        SnapshotBuilder builder = new SnapshotBuilder(service, 100_000.0);
        builder.build();

        service.prices = Map.of("AAPL", 105.0);
        ViewSnapshot snapshot = builder.build();

        MarketRow row = snapshot.markets().get(0);
        assertEquals("+5.00%", row.changePercent());
        assertTrue(row.up());
    }

    @Test
    void resetSessionClearsBaseline() {
        FakeTradingService service = new FakeTradingService();
        service.prices = Map.of("AAPL", 100.0);
        SnapshotBuilder builder = new SnapshotBuilder(service, 100_000.0);
        builder.build();

        service.prices = Map.of("AAPL", 120.0);
        builder.resetSession();
        ViewSnapshot snapshot = builder.build();

        assertEquals("0.00%", snapshot.markets().get(0).changePercent());
    }

    @Test
    void rejectedOrdersKeepTheirReason() {
        FakeTradingService service = new FakeTradingService();
        service.history = List.of(new Order(java.util.UUID.randomUUID(), AAPL, Side.BUY,
                com.tradingdesk.model.OrderType.MARKET, 999, 100.0,
                com.tradingdesk.model.OrderStatus.REJECTED, "Maximum order quantity: 999 > 1000",
                Instant.now(), Instant.now()));

        ViewSnapshot snapshot = new SnapshotBuilder(service, 100_000.0).build();
        assertTrue(snapshot.orderHistory().get(0).reason().contains("Maximum order quantity"));
    }

    @Test
    void indicatorsFlagInsufficientHistory() {
        FakeTradingService service = new FakeTradingService();
        service.selected = new LinkedHashSet<>(List.of("AAPL"));
        service.indicators = new IndicatorSnapshot(100, 101, 0.01, 0.5, 0.2, 1, Instant.now());

        ViewSnapshot snapshot = new SnapshotBuilder(service, 100_000.0).build();
        assertFalse(snapshot.indicators().reliable());
        assertEquals(1, snapshot.indicators().samples());
        assertEquals("AAPL", snapshot.indicators().symbol());
    }

    @Test
    void emptyServiceProducesSafeSnapshot() {
        FakeTradingService service = new FakeTradingService();
        ViewSnapshot snapshot = new SnapshotBuilder(service, 100_000.0).build();

        assertTrue(snapshot.markets().isEmpty());
        assertTrue(snapshot.signals().isEmpty());
        assertEquals("-", snapshot.indicators().symbol());
        assertFalse(snapshot.running());
    }

    @Test
    void dashboardStatusReflectsServiceVerbatim() {
        FakeTradingService service = new FakeTradingService();
        service.databaseStatus = "In-memory only (MySQL not configured)";
        ViewSnapshot snapshot = new SnapshotBuilder(service, 100_000.0).build();
        assertEquals("In-memory only (MySQL not configured)", snapshot.databaseStatus(),
                "the UI must report the real status, never a fabricated 'connected'");
    }

    private static TradeSignal signal(SignalType type) {
        IndicatorSnapshot indicators = new IndicatorSnapshot(100, 100, 0, 0, 0, 20, Instant.now());
        return new TradeSignal(AAPL, type, 0.6, "test", indicators, Instant.now());
    }

    /** Minimal in-memory service used to exercise the mapping rules. */
    private static final class FakeTradingService implements TradingService {

        Map<String, Double> prices = Map.of();
        Map<String, Long> volumes = Map.of();
        List<TradeSignal> signals = List.of();
        List<Order> openOrders = List.of();
        List<Order> history = List.of();
        List<RiskAlert> alerts = List.of();
        PortfolioSnapshot portfolio = new PortfolioSnapshot(0, 0, 0, 0, Instant.now());
        OrderStatistics statistics = new OrderStatistics(0, 0, 0, 0, 0, 0);
        IndicatorSnapshot indicators = IndicatorSnapshot.empty(Instant.now());
        Set<String> selected = new LinkedHashSet<>();
        String databaseStatus = "Not connected";
        boolean running;

        @Override public void start() { running = true; }
        @Override public void stop() { running = false; }
        @Override public boolean isRunning() { return running; }
        @Override public void reset() { }
        @Override public Set<String> availableSymbols() { return selected; }
        @Override public Set<String> selectedSymbols() { return selected; }
        @Override public void selectSymbols(Set<String> symbols) { selected = symbols; }
        @Override public Map<String, Double> latestPrices() { return prices; }
        @Override public Map<String, Long> priceVolumes() { return volumes; }
        @Override public double averageSpread() { return 0.01; }
        @Override public IndicatorSnapshot indicatorsFor(String symbol) { return indicators; }
        @Override public List<TradeSignal> recentSignals(int limit) { return signals; }
        @Override public List<Order> openOrders(int limit) { return openOrders; }
        @Override public List<Order> orderHistory(int limit) { return history; }
        @Override public List<RiskAlert> recentAlerts(int limit) { return alerts; }
        @Override public PortfolioSnapshot portfolioSnapshot() { return portfolio; }
        @Override public OrderStatistics orderStatistics() { return statistics; }
        @Override public long eventsProcessed() { return 1_000; }
        @Override public long eventsDropped() { return 0; }
        @Override public int queueDepth() { return 0; }
        @Override public int queueCapacity() { return 25_000; }
        @Override public int activeWorkerThreads() { return 7; }
        @Override public double averageLatencyMicros() { return 210.5; }
        @Override public double peakLatencyMicros() { return 1_200.0; }
        @Override public double throughputPerSecond() { return 33.3; }
        @Override public String databaseStatus() { return databaseStatus; }
        @Override public List<String> recentLogs(int limit) { return List.of("log line"); }
        @Override public List<Instrument> watchlist() { return new ArrayList<>(List.of(AAPL, MSFT)); }
    }
}
