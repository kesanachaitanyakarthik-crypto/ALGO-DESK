package com.tradingdesk.application;

import com.tradingdesk.service.TradingService;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration of the running pipeline (Phases 3-6 without the JavaFX view).
 * Measurements asserted here are produced by the live application components, not mocked.
 */
class SystemIntegrationTest {

    private static boolean waitUntil(java.util.function.BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }

    @Test
    void fullPipelineProcessesEventsAndProducesSignals() {
        try (ApplicationContext context = new ApplicationContext(false)) {
            TradingService service = context.tradingService();
            assertFalse(service.isRunning());

            service.start();
            assertTrue(service.isRunning());

            assertTrue(waitUntil(() -> service.eventsProcessed() > 200, Duration.ofSeconds(15)),
                    "pipeline should process a steady stream of events");
            assertTrue(waitUntil(() -> !service.recentSignals(1).isEmpty(), Duration.ofSeconds(15)),
                    "prediction engine should emit signals once history is sufficient");

            assertTrue(service.latestPrices().keySet().containsAll(
                            java.util.List.of("AAPL", "MSFT", "GOOG", "AMZN", "NVDA")),
                    "all five simulator symbols should publish prices");

            assertTrue(service.activeWorkerThreads() > 0, "worker threads must be active");
            assertTrue(service.averageLatencyMicros() >= 0.0);
            assertTrue(service.throughputPerSecond() > 0.0);

            service.stop();
            assertFalse(service.isRunning());
        }
    }

    @Test
    void queueAndWorkersRemainConsistentUnderSustainedLoad() {
        try (ApplicationContext context = new ApplicationContext(false)) {
            TradingService service = context.tradingService();
            service.start();

            assertTrue(waitUntil(() -> service.eventsProcessed() > 500, Duration.ofSeconds(20)));

            long processed = service.eventsProcessed();
            int depth = service.queueDepth();
            assertTrue(depth >= 0, "queue depth must never go negative");
            assertTrue(processed > 0);
            assertEquals(0, service.eventsDropped(),
                    "with a 25k-capacity queue and healthy consumers nothing should be dropped");

            service.stop();
        }
    }

    @Test
    void ordersAreRiskValidatedAndExecutedSimulated() {
        try (ApplicationContext context = new ApplicationContext(false)) {
            TradingService service = context.tradingService();
            service.start();

            boolean traded = waitUntil(() -> service.orderStatistics().submitted() > 0, Duration.ofSeconds(20));
            assertTrue(traded, "BUY/SELL signals should create orders");

            var stats = service.orderStatistics();
            assertTrue(stats.submitted() >= stats.executed() + stats.rejected(),
                    "every submitted order ends approved/executed or rejected");
            if (stats.rejected() > 0) {
                assertTrue(service.recentAlerts(5).stream()
                                .allMatch(alert -> alert.message() != null && !alert.message().isBlank()),
                        "rejected orders must carry a reason");
            }
            service.stop();
        }
    }

    @Test
    void stopIsIdempotentAndResettable() {
        try (ApplicationContext context = new ApplicationContext(false)) {
            TradingService service = context.tradingService();
            service.start();
            assertTrue(waitUntil(() -> service.eventsProcessed() > 50, Duration.ofSeconds(10)));

            service.stop();
            service.stop();
            assertFalse(service.isRunning());

            service.reset();
            assertEquals(0, service.eventsProcessed());
            assertEquals(0, service.orderStatistics().submitted());
        }
    }
}
