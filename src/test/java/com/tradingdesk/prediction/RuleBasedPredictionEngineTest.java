package com.tradingdesk.prediction;

import com.tradingdesk.indicators.IndicatorSnapshot;
import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.InstrumentType;
import com.tradingdesk.model.SignalType;
import com.tradingdesk.model.TradeSignal;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedPredictionEngineTest {

    private static final Instrument AAPL = new Instrument("AAPL", "Apple Inc.", InstrumentType.EQUITY);
    private final RuleBasedPredictionEngine engine =
            new RuleBasedPredictionEngine(new PredictionConfig(0.0015, 5.0, 3));

    private static IndicatorSnapshot snapshot(double sma, double ema, double roc, double volatility, int samples) {
        return new IndicatorSnapshot(sma, ema, roc, volatility, 0.2, samples, Instant.now());
    }

    @Test
    void bullishCrossoverWithMomentumProducesBuy() {
        TradeSignal signal = engine.predict(AAPL, snapshot(100.0, 101.0, 0.01, 0.5, 20));
        assertEquals(SignalType.BUY, signal.type());
        assertTrue(signal.confidence() > 0.4);
    }

    @Test
    void bearishCrossoverWithNegativeMomentumProducesSell() {
        TradeSignal signal = engine.predict(AAPL, snapshot(100.0, 99.0, -0.01, 0.5, 20));
        assertEquals(SignalType.SELL, signal.type());
    }

    @Test
    void flatMarketProducesHold() {
        assertEquals(SignalType.HOLD, engine.predict(AAPL, snapshot(100.0, 100.0, 0.0, 0.5, 20)).type());
    }

    @Test
    void conflictingSignalsProduceHold() {
        assertEquals(SignalType.HOLD, engine.predict(AAPL, snapshot(100.0, 99.0, 0.01, 0.5, 20)).type());
    }

    @Test
    void insufficientHistoryProducesHoldWithZeroConfidence() {
        TradeSignal signal = engine.predict(AAPL, snapshot(100.0, 101.0, 0.05, 0.5, 1));
        assertEquals(SignalType.HOLD, signal.type());
        assertEquals(0.0, signal.confidence(), 1e-9);
        assertTrue(signal.rationale().startsWith("Insufficient history"));
    }

    @Test
    void excessiveVolatilityForcesHold() {
        TradeSignal signal = engine.predict(AAPL, snapshot(100.0, 105.0, 0.05, 9.0, 20));
        assertEquals(SignalType.HOLD, signal.type());
        assertTrue(signal.rationale().contains("Volatility"));
    }
}
