package com.tradingdesk.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies that <em>real</em> market data flows through the application's own concurrency pipeline.
 *
 * <p>This is the integration claim that matters: live ticks must arrive, be adapted into
 * {@code MarketEvent}s, be published into the bounded queue and be processed by the worker pool — not
 * bypass it.
 *
 * <p>Opt-in via {@code -Dlive.enabled=true} because it needs outbound network access.
 */
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "live.enabled", matches = "true")
class LiveMarketDataPipelineIntegrationTest {

    private static boolean waitUntil(java.util.function.BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }

    @Test
    void liveTicksAreProcessedByTheWorkerPipeline() {
        // allowNetwork=true is the real configuration the application uses at launch.
        try (ApplicationContext context = new ApplicationContext(false, true)) {
            context.start();
            try {
                var supervisor = context.marketData();
                boolean connected = waitUntil(
                        () -> supervisor.liveTicks() > 0, Duration.ofSeconds(25));
                assumeTrue(connected, "live market data not reachable from this machine");

                // The decisive assertion: live ticks reached the processed counter, which only the
                // worker pool increments after draining the bounded queue.
                boolean processed = waitUntil(
                        () -> context.tradingService().eventsProcessed() > 0,
                        Duration.ofSeconds(20));

                assertTrue(processed,
                        "live ticks must be processed by the worker pool, not bypass it");
                assertTrue(supervisor.liveTicks() > 0, "the supervisor must count live ticks");
                assertTrue(supervisor.isLive(),
                        "the active source should be live; was " + supervisor.activeMode());

                System.out.println("=== LIVE PIPELINE VERIFICATION ===");
                System.out.println("market data mode : " + supervisor.activeMode().label());
                System.out.println("live ticks       : " + supervisor.liveTicks());
                System.out.println("duplicates       : " + supervisor.duplicatesSuppressed());
                System.out.println("reconnects       : " + supervisor.reconnects());
                System.out.println("events processed : " + context.tradingService().eventsProcessed());
                System.out.println("p50 latency      : "
                        + context.tradingService().p50LatencyMicros() + " us");
            } finally {
                context.stop();
            }
        }
    }
}
