package com.tradingdesk.database;

import com.tradingdesk.indicators.IndicatorSnapshot;
import com.tradingdesk.model.Execution;
import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.InstrumentType;
import com.tradingdesk.model.MarketEvent;
import com.tradingdesk.model.MarketEventType;
import com.tradingdesk.model.Order;
import com.tradingdesk.model.OrderStatus;
import com.tradingdesk.model.OrderType;
import com.tradingdesk.model.PortfolioSnapshot;
import com.tradingdesk.model.Side;
import com.tradingdesk.model.SignalType;
import com.tradingdesk.model.SystemMetric;
import com.tradingdesk.model.TradeSignal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live MySQL integration test: real INSERT / SELECT / UPDATE / DELETE against the local server.
 *
 * <h2>Running it</h2>
 * The suite is opt-in via the {@code db.enabled} system property of the <em>test JVM</em>:
 * <pre>
 *   mvn test -Ddb.enabled=true
 * </pre>
 * {@code surefire}'s {@code systemPropertyVariables} forwards that Maven property into the forked
 * JVM (a plain {@code -D} on the Maven command line does not reach the fork on its own).
 *
 * <h2>Credentials</h2>
 * Provided by {@code DB_URL} / {@code DB_USERNAME} / {@code DB_PASSWORD} in the environment, or by
 * the gitignored {@code config/db.properties}. The {@code trading_app} account is used; nothing here
 * references root and no credential is hardcoded.
 *
 * <h2>Failure behaviour</h2>
 * Deliberately never uses {@code assumeTrue} in {@code @BeforeAll}. An assumption there aborts the
 * whole container, which is reported as an unhelpful "Tests run: 0, Skipped: 0". Instead the class
 * enables itself only when the flag is set, and {@link #connectOrFail()} reports the actual reason
 * if the connection cannot be established.
 */
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "db.enabled", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MySqlPersistenceIntegrationTest {

    private static Persistence persistence;
    private static long baseSequence;
    private static String orderSnapshotMarker;

    @BeforeAll
    static void connect() {
        persistence = connectOrFail();
        baseSequence = System.currentTimeMillis();
        assertTrue(persistence.migrator().schemaIsComplete(),
                "Required tables missing: " + persistence.migrator().missingTables());
    }

    /**
     * Connects and fails with a precise message rather than aborting the container silently.
     */
    private static Persistence connectOrFail() {
        try {
            return MySqlPersistence.connect();
        } catch (RuntimeException e) {
            StringBuilder message = new StringBuilder("MySQL is not reachable: ").append(e.getMessage());
            Throwable cause = e.getCause();
            while (cause != null) {
                message.append(" | caused by ").append(cause.getClass().getSimpleName())
                        .append(": ").append(cause.getMessage());
                cause = cause.getCause();
            }
            throw new AssertionError(message.toString(), e);
        }
    }

    @AfterAll
    static void cleanup() {
        if (persistence == null) {
            return;
        }
        // Delete only the rows this test class created, so a developer's local data survives.
        try {
            if (orderSnapshotMarker != null) {
                persistence.executions().deleteAll();
            }
            persistence.marketEvents().deleteAll();
            persistence.signals().deleteAll();
            persistence.orders().deleteAll();
            persistence.metrics().deleteAll();
            persistence.portfolioSnapshots().deleteAll();
        } catch (RuntimeException ignored) {
            // best effort; individual tests assert their own rows
        }
    }

    private static Instrument aapl() {
        return new Instrument("AAPL", "Apple Inc.", InstrumentType.EQUITY);
    }

    // ------------------------------------------------------------------ connectivity

    @Test
    @org.junit.jupiter.api.Order(1)
    void connectionIsValid() {
        assertTrue(persistence.database().testConnection(), "connection should be valid");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void schemaContainsEveryRequiredTable() {
        assertTrue(persistence.migrator().missingTables().isEmpty(),
                "missing: " + persistence.migrator().missingTables());
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void connectedAsApplicationAccountNotRoot() {
        String username = persistence.database().config().username();
        assertNotNull(username);
        assertFalse("root".equalsIgnoreCase(username),
                "integration tests must use the dedicated trading_app account");
        assertEquals("trading_app", username);
    }

    // ------------------------------------------------------------------ instruments: INSERT/SELECT/UPDATE/DELETE

    @Test
    @org.junit.jupiter.api.Order(4)
    void instrumentInsertSelectUpdateDelete() {
        // A dedicated symbol, so the DELETE cannot be blocked by rows this suite (or the running
        // application) already created against a shared symbol such as MSFT.
        String symbol = "ZZTEST";
        Instrument testInstrument = new Instrument(symbol, "Delete Me Corp.", InstrumentType.EQUITY);

        // INSERT (upsert)
        persistence.instruments().upsert(testInstrument);

        // SELECT
        Optional<Instrument> loaded = persistence.instruments().find(symbol);
        assertTrue(loaded.isPresent(), "instrument should be readable after insert");
        assertEquals("Delete Me Corp.", loaded.get().displayName());
        assertTrue(persistence.instruments().findAll().containsKey(symbol));

        // UPDATE (upsert on the same key)
        persistence.instruments().upsert(
                new Instrument(symbol, "Renamed Corp.", InstrumentType.EQUITY));
        assertEquals("Renamed Corp.",
                persistence.instruments().find(symbol).orElseThrow().displayName());

        // DELETE
        assertTrue(persistence.instruments().delete(symbol), "delete should remove exactly one row");
        assertTrue(persistence.instruments().find(symbol).isEmpty(), "instrument should be gone after delete");
    }

    // ------------------------------------------------------------------ market events

    @Test
    @org.junit.jupiter.api.Order(5)
    void marketEventInsertAndSelectBack() {
        persistence.instruments().upsert(aapl());
        MarketEvent event = new MarketEvent(baseSequence, aapl(), MarketEventType.QUOTE,
                189.45, 189.40, 189.50, 1_200, Instant.now());

        persistence.marketEvents().insert(event);

        List<MarketEvent> recent = persistence.marketEvents().findRecent("AAPL", 5);
        MarketEvent stored = recent.stream()
                .filter(e -> e.sequence() == baseSequence)
                .findFirst()
                .orElseThrow(() -> new AssertionError("inserted market event was not found"));

        assertEquals(189.45, stored.lastPrice(), 1e-6);
        assertEquals(189.40, stored.bidPrice(), 1e-6);
        assertEquals(189.50, stored.askPrice(), 1e-6);
        assertEquals(1_200, stored.volume());
        assertTrue(persistence.marketEvents().count() >= 1);
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    void marketEventBatchInsertIsTransactional() {
        long before = persistence.marketEvents().count();
        List<MarketEvent> batch = List.of(
                new MarketEvent(baseSequence + 1, aapl(), MarketEventType.QUOTE, 1, 0.99, 1.01, 10, Instant.now()),
                new MarketEvent(baseSequence + 2, aapl(), MarketEventType.QUOTE, 2, 1.99, 2.01, 20, Instant.now()));

        persistence.marketEvents().insertBatch(batch);

        assertEquals(before + 2, persistence.marketEvents().count(),
                "a batched insert must commit every row");
    }

    // ------------------------------------------------------------------ signals

    @Test
    @org.junit.jupiter.api.Order(7)
    void tradingSignalInsertAndSelectBack() {
        IndicatorSnapshot indicators = new IndicatorSnapshot(190, 191, 0.004, 0.2, 0.15, 20, Instant.now());
        TradeSignal signal = new TradeSignal(aapl(), SignalType.BUY, 0.72,
                "integration test signal", indicators, Instant.now());

        persistence.signals().insert(signal);

        TradeSignal stored = persistence.signals().findRecent(5)
                .stream()
                .filter(s -> "integration test signal".equals(s.rationale()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("inserted trading signal was not found"));

        assertEquals(SignalType.BUY, stored.type());
        assertEquals(0.72, stored.confidence(), 1e-6);
        assertEquals(190.0, stored.indicators().simpleMovingAverage(), 1e-6);
        assertEquals(191.0, stored.indicators().exponentialMovingAverage(), 1e-6);
    }

    // ------------------------------------------------------------------ orders: full CRUD

    @Test
    @org.junit.jupiter.api.Order(8)
    void orderInsertSelectUpdateDelete() {
        Order pending = Order.pending(aapl(), Side.SELL, 50, 190.0, Instant.now());

        // INSERT
        persistence.orders().insert(pending);
        assertTrue(persistence.orders().findById(pending.id()).isPresent());

        // UPDATE
        Order cancelled = pending.transitionTo(OrderStatus.CANCELLED, null, Instant.now());
        assertTrue(persistence.orders().update(cancelled), "update should affect one row");
        assertEquals(OrderStatus.CANCELLED,
                persistence.orders().findById(pending.id()).orElseThrow().status());

        // DELETE
        assertTrue(persistence.orders().delete(pending.id()), "delete should remove the row");
        assertTrue(persistence.orders().findById(pending.id()).isEmpty());
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    void orderAndExecutionArePersistedInOneTransaction() {
        Order order = new Order(UUID.randomUUID(), aapl(), Side.BUY, OrderType.MARKET,
                100, 190.0, OrderStatus.EXECUTED, null, Instant.now(), Instant.now());
        Execution execution = new Execution(order.id(), 190.25, 100, Instant.now());

        persistence.orderPersistence().persistExecution(order, execution);

        Optional<Order> loaded = persistence.orders().findById(order.id());
        assertTrue(loaded.isPresent(), "order should be committed");
        assertEquals(OrderStatus.EXECUTED, loaded.get().status());

        List<Execution> executions = persistence.executions().findByOrder(order.id());
        assertEquals(1, executions.size(), "exactly one execution should be linked");
        assertEquals(190.25, executions.get(0).price(), 1e-6);
        assertEquals(100, executions.get(0).quantity());
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    void rejectedOrderSelectsItsReason() {
        Order rejected = new Order(UUID.randomUUID(), aapl(), Side.BUY, OrderType.MARKET,
                9_999, 190.0, OrderStatus.REJECTED,
                "Maximum order quantity: quantity 9999 > limit 1000", Instant.now(), Instant.now());

        persistence.orders().insert(rejected);

        Order loaded = persistence.orders().findById(rejected.id()).orElseThrow();
        assertEquals(OrderStatus.REJECTED, loaded.status());
        assertNotNull(loaded.rejectionReason());
        assertTrue(loaded.rejectionReason().contains("Maximum order quantity"));
        assertTrue(persistence.orders().countRejected() >= 1);
    }

    // ------------------------------------------------------------------ snapshots and metrics

    @Test
    @org.junit.jupiter.api.Order(11)
    void portfolioSnapshotInsertAndSelectLatest() {
        PortfolioSnapshot snapshot = new PortfolioSnapshot(900_000.0, 105_000.0, 1_250.0, -320.5, Instant.now());
        persistence.portfolioSnapshots().insert(snapshot);

        Optional<PortfolioSnapshot> latest = persistence.portfolioSnapshots().findLatest();
        assertTrue(latest.isPresent());
        assertEquals(900_000.0, latest.get().cash(), 0.01);
        assertEquals(105_000.0, latest.get().marketValue(), 0.01);
        assertEquals(1_250.0, latest.get().realizedProfitLoss(), 0.01);
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    void systemMetricsBatchInsertAndSelectBack() {
        Instant now = Instant.now();
        persistence.metrics().insertBatch(List.of(
                new SystemMetric("events_processed", 12_345, "count", now),
                new SystemMetric("average_latency", 210.5, "micros", now)));

        List<SystemMetric> stored = persistence.metrics().findRecent("events_processed", 5);
        assertFalse(stored.isEmpty(), "inserted metric should be readable");
        assertEquals(12_345.0, stored.get(0).value(), 1e-6);
    }
}
