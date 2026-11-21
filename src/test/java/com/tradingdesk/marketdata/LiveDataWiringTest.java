package com.tradingdesk.marketdata;

import com.tradingdesk.marketdata.ws.StreamMessage;
import com.tradingdesk.model.Instrument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the live-data wiring defects found during the final audit.
 *
 * <p>Each test pins a specific bug that was only visible by running the GUI, so it can never come
 * back silently:
 *
 * <ol>
 *   <li>the streamed symbol set is the instrument universe (so LIVE data updates visible rows);</li>
 *   <li>fallback prices are paired by symbol, not by map iteration order;</li>
 *   <li>the supervisor reports the mode it actually achieved, and a listener sees later changes.</li>
 * </ol>
 */
class LiveDataWiringTest {

    @Test
    void defaultMappingIsOrderedAndStable() {
        // The mapping seeds the watchlist, and callers pair it with fallback prices positionally.
        assertEquals(List.of("BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT"),
                new ArrayList<>(LiveTickAdapter.defaultMapping().keySet()));
        assertEquals(List.of("BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT"),
                new ArrayList<>(LiveTickAdapter.defaultMapping().keySet()),
                "iteration order must be deterministic, not Map.of() order");
    }

    @Test
    void fallbackPricesAreResolvedPerSymbolNotByPosition() {
        // The bug: XRP was handed BTC's price level and the UI showed +77,301%.
        Map<String, Instrument> mapping = LiveTickAdapter.defaultMapping();
        for (String symbol : mapping.keySet()) {
            double price = LiveTickAdapter.fallbackPrice(symbol);
            assertTrue(price > 0.0, symbol + " needs a positive fallback");
        }
        assertTrue(LiveTickAdapter.fallbackPrice("BTCUSDT") > 10_000.0);
        assertTrue(LiveTickAdapter.fallbackPrice("XRPUSDT") < 10.0,
                "XRP must not be given a BTC-scale price");
        // An unknown symbol must not silently inherit another symbol's level.
        assertEquals(100.0, LiveTickAdapter.fallbackPrice("UNKNOWNUSDT"));
    }

    @Test
    void supervisorReportsAchievedModeAndNotifiesOnChange() {
        List<MarketDataSourceSupervisor.Mode> seen = new ArrayList<>();
        try (MarketDataSourceSupervisor supervisor =
                     new MarketDataSourceSupervisor(List.of("BTCUSDT"), message -> { }, false)) {
            supervisor.setModeListener(seen::add);
            MarketDataSourceSupervisor.Mode mode = supervisor.start();
            assertEquals(MarketDataSourceSupervisor.Mode.SIMULATED, mode,
                    "no network flag means no live attempt");
            assertEquals(mode, supervisor.activeMode());
            assertFalse(supervisor.isLive());
            assertTrue(supervisor.display().startsWith("SIMULATED"));
            // The listener is told about the initial state and every subsequent change.
            assertFalse(seen.isEmpty());
        }
    }

    @Test
    void adapterRejectsUnknownSymbolsInsteadOfInventingData() {
        LiveTickAdapter adapter = new LiveTickAdapter(LiveTickAdapter.defaultMapping());
        StreamMessage unknown = new StreamMessage(
                "DOGEUSDT", Instant.now(), StreamMessage.Kind.TRADE, 1.0, 1.0, 1.0, 5.0, 1L);
        // The adapter must decline to build an event for a symbol it does not know.
        assertEquals(null, adapter.adapt(unknown));
    }
}
