package com.tradingdesk.application;

import com.tradingdesk.marketdata.MarketDataSourceSupervisor;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the offline path: with network access disabled the application must still start, still
 * process events, and must report itself as SIMULATED rather than claiming live data.
 *
 * <p>This is the guarantee that the demo works on a machine with no internet access, and the
 * guarantee that a fabricated "LIVE" badge is impossible — the mode is derived from what actually
 * happened, not from a flag.
 */
class OfflineFallbackTest {

    @Test
    void startsAndProcessesWithSimulatedDataWhenNetworkIsDisabled() {
        try (ApplicationContext context = new ApplicationContext(false, false)) {
            assertEquals(MarketDataSourceSupervisor.Mode.SIMULATED, context.marketData().activeMode(),
                    "no network means the supervisor must report SIMULATED");

            context.start();
            assertTrue(context.tradingService().isRunning());

            assertTrue(waitUntil(() -> context.tradingService().eventsProcessed() > 100,
                    Duration.ofSeconds(15)), "the pipeline must still run offline");

            // The synthetic source is not muted, because no real source is active.
            assertTrue(context.tradingService().eventsProcessed() > 0);
            assertFalse(context.tradingService().marketDataStatus().startsWith("LIVE"),
                    "simulated data must never be labelled LIVE");

            context.stop();
            assertFalse(context.tradingService().isRunning());
        }
    }

    @Test
    void simulatedModeLabelsItselfAsNotLive() {
        // ProviderKind is what the UI keys off; SIMULATED must be distinguishable.
        assertFalse(MarketDataSourceSupervisor.Mode.SIMULATED.kind().isReal());
        assertTrue(MarketDataSourceSupervisor.Mode.SIMULATED.kind().displayLabel().contains("not live"));
        assertTrue(MarketDataSourceSupervisor.Mode.LIVE_WS.kind().isReal());
    }

    private static boolean waitUntil(java.util.function.BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }
}
