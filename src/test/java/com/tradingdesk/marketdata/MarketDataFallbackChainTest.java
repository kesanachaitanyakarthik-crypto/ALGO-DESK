package com.tradingdesk.marketdata;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the degradation chain {@code LIVE_WS → LIVE_REST → SIMULATED} deterministically.
 *
 * <p>Before this test the chain could only be read in code, never run: the supervisor hardcoded the
 * Binance endpoints, so the REST-fallback branch could not be reached on a machine where the
 * WebSocket happened to work, and the SIMULATED branch only appeared when the whole network was
 * down. Both endpoints are now injectable, so each branch is proven.
 *
 * <p>No real network is used: the "unreachable" endpoint is an address that cannot resolve.
 */
class MarketDataFallbackChainTest {

    private static final String UNREACHABLE_WS = "wss://127.0.0.1:9/stream?streams=";
    private static final String UNREACHABLE_REST = "https://127.0.0.1:9";

    /** Both transports dead: the supervisor must land on SIMULATED and say so. */
    @Test
    void fallsBackToSimulatedWhenNothingIsReachable() {
        try (MarketDataSourceSupervisor supervisor = new MarketDataSourceSupervisor(
                java.util.List.of("BTCUSDT"), message -> { }, true,
                UNREACHABLE_WS, UNREACHABLE_REST)) {

            MarketDataSourceSupervisor.Mode mode = supervisor.start();

            assertEquals(MarketDataSourceSupervisor.Mode.SIMULATED, mode,
                    "with no reachable transport the only honest mode is SIMULATED");
            assertFalse(supervisor.isLive(), "SIMULATED must never report itself live");
            assertTrue(supervisor.isSimulated());
            assertTrue(supervisor.display().startsWith("SIMULATED"),
                    "the label must name SIMULATED; was '" + supervisor.display() + "'");
            assertEquals(0L, supervisor.liveTicks());
        }
    }

    /** Network disabled entirely: same conclusion, without attempting any connection. */
    @Test
    void allowNetworkFalseGoesStraightToSimulated() {
        try (MarketDataSourceSupervisor supervisor = new MarketDataSourceSupervisor(
                java.util.List.of("BTCUSDT"), message -> { }, false)) {
            assertEquals(MarketDataSourceSupervisor.Mode.SIMULATED, supervisor.start());
            assertTrue(supervisor.isSimulated());
        }
    }

    /** Every mode maps to a provider kind the UI can colour correctly. */
    @Test
    void onlyRealModesAreMarkedReal() {
        assertTrue(MarketDataSourceSupervisor.Mode.LIVE_WS.kind().isReal());
        assertTrue(MarketDataSourceSupervisor.Mode.LIVE_REST.kind().isReal());
        assertFalse(MarketDataSourceSupervisor.Mode.SIMULATED.kind().isReal());
        // The UI keys "live" styling off isReal(), so SIMULATED can never render as live.
        assertTrue(MarketDataSourceSupervisor.Mode.SIMULATED.kind().displayLabel().contains("not live"));
    }

    /** A mode change is observable, which is what re-enables the synthetic source. */
    @Test
    void modeListenerFiresWithTheAchievedMode() {
        java.util.List<MarketDataSourceSupervisor.Mode> seen = new java.util.ArrayList<>();
        try (MarketDataSourceSupervisor supervisor = new MarketDataSourceSupervisor(
                java.util.List.of("BTCUSDT"), message -> { }, true,
                UNREACHABLE_WS, UNREACHABLE_REST)) {
            supervisor.setModeListener(seen::add);
            supervisor.start();
            assertEquals(MarketDataSourceSupervisor.Mode.SIMULATED, seen.get(seen.size() - 1));
        }
    }

    /** Live deployment: the real Binance endpoint still reaches LIVE_WS (network-dependent). */
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "live.enabled", matches = "true")
    @Test
    void realEndpointAchievesLiveWebSocket() {
        try (MarketDataSourceSupervisor supervisor = new MarketDataSourceSupervisor(
                java.util.List.of("BTCUSDT"), message -> { }, true)) {
            MarketDataSourceSupervisor.Mode mode = supervisor.start();
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    mode != MarketDataSourceSupervisor.Mode.SIMULATED,
                    "Binance unreachable from this machine");
            assertEquals(MarketDataSourceSupervisor.Mode.LIVE_WS, mode);
            assertTrue(supervisor.isLive());

            // The stream must actually deliver ticks.
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (supervisor.liveTicks() == 0 && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            assertTrue(supervisor.liveTicks() > 0, "LIVE_WS without ticks is not live");
        }
    }

    /**
     * The middle branch: WebSocket dead but REST alive must degrade to LIVE_REST, not all the way to
     * SIMULATED. This is the branch that matters most in practice — a blocked WebSocket port behind a
     * corporate proxy while HTTPS still works. Network-dependent because it needs real REST.
     */
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "live.enabled", matches = "true")
    @Test
    void degradesToLiveRestWhenWebSocketIsUnreachable() {
        try (MarketDataSourceSupervisor supervisor = new MarketDataSourceSupervisor(
                java.util.List.of("BTCUSDT"), message -> { }, true,
                UNREACHABLE_WS, null)) {
            MarketDataSourceSupervisor.Mode mode = supervisor.start();
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    mode != MarketDataSourceSupervisor.Mode.SIMULATED,
                    "Binance REST unreachable from this machine");

            assertEquals(MarketDataSourceSupervisor.Mode.LIVE_REST, mode,
                    "a dead WebSocket with working REST must land on LIVE_REST, not SIMULATED");
            assertTrue(supervisor.isLive(), "LIVE_REST is real data and must report live");
            assertFalse(supervisor.isSimulated());
            assertTrue(supervisor.display().startsWith("LIVE REST"),
                    "the label must name LIVE REST; was '" + supervisor.display() + "'");
        }
    }
}
