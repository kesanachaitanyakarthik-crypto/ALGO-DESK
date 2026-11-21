package com.tradingdesk.indicators;

import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.InstrumentType;
import com.tradingdesk.model.MarketEvent;
import com.tradingdesk.model.MarketEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RollingIndicatorEngineTest {

    private static final Instrument AAPL = new Instrument("AAPL", "Apple Inc.", InstrumentType.EQUITY);

    private static MarketEvent event(long sequence, double price) {
        return new MarketEvent(sequence, AAPL, MarketEventType.QUOTE,
                price, price - 0.01, price + 0.01, 100, Instant.now());
    }

    @Test
    void windowKeepsOnlyTheConfiguredNumberOfPrices() {
        RollingIndicatorEngine engine = new RollingIndicatorEngine(3, 1);
        for (int i = 1; i <= 10; i++) {
            engine.ingest(event(i, i));
        }
        IndicatorSnapshot snapshot = engine.latest();
        assertEquals(9.0, snapshot.simpleMovingAverage(), 1e-9);
        assertEquals(3, snapshot.sampleCount());
    }

    @Test
    void emptyEngineReturnsZeroedSnapshot() {
        RollingIndicatorEngine engine = new RollingIndicatorEngine(5, 1);
        IndicatorSnapshot snapshot = engine.latest();
        assertEquals(0, snapshot.sampleCount());
        assertEquals(0.0, snapshot.simpleMovingAverage(), 1e-9);
        assertTrue(!snapshot.hasSufficientData());
    }

    @Test
    void resetClearsHistory() {
        RollingIndicatorEngine engine = new RollingIndicatorEngine(5, 1);
        engine.ingest(event(1, 100.0));
        engine.reset();
        assertEquals(0, engine.latest().sampleCount());
    }
}
