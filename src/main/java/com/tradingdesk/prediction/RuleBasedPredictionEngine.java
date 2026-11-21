package com.tradingdesk.prediction;

import com.tradingdesk.indicators.IndicatorSnapshot;
import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.SignalType;
import com.tradingdesk.model.TradeSignal;

import java.time.Instant;

/**
 * Transparent rule-based baseline. This is deterministic technical analysis
 * (moving-average crossover plus momentum), not a trained model.
 */
public class RuleBasedPredictionEngine implements PredictionEngine {

    private final PredictionConfig config;

    public RuleBasedPredictionEngine(PredictionConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "Rule-based MA crossover + momentum";
    }

    @Override
    public String description() {
        return "Deterministic technical rules over SMA/EMA/ROC. Not a trained AI/ML model.";
    }

    @Override
    public TradeSignal predict(Instrument instrument, IndicatorSnapshot snapshot) {
        if (snapshot.sampleCount() < config.minimumSamples()) {
            return new TradeSignal(instrument, SignalType.HOLD, 0.0,
                    "Insufficient history: " + snapshot.sampleCount() + "/"
                            + config.minimumSamples() + " samples",
                    snapshot, Instant.now());
        }

        double crossover = snapshot.exponentialMovingAverage() - snapshot.simpleMovingAverage();
        double crossoverPercent = snapshot.simpleMovingAverage() == 0.0
                ? 0.0
                : crossover / snapshot.simpleMovingAverage();
        double momentum = snapshot.rateOfChange();
        boolean tooVolatile = snapshot.rollingVolatility() > config.volatilityCeiling();

        SignalType type;
        String rationale;
        double confidence;

        boolean bullish = crossoverPercent > 0 && momentum > config.momentumThreshold();
        boolean bearish = crossoverPercent < 0 && momentum < -config.momentumThreshold();

        if (tooVolatile) {
            type = SignalType.HOLD;
            rationale = "Volatility " + format(snapshot.rollingVolatility())
                    + " above ceiling " + config.volatilityCeiling();
            confidence = 0.2;
        } else if (bullish) {
            type = SignalType.BUY;
            rationale = "EMA above SMA by " + format(crossoverPercent * 100) + "% and ROC "
                    + format(momentum * 100) + "%";
            confidence = confidenceOf(crossoverPercent, momentum, snapshot);
        } else if (bearish) {
            type = SignalType.SELL;
            rationale = "EMA below SMA by " + format(-crossoverPercent * 100) + "% and ROC "
                    + format(-momentum * 100) + "%";
            confidence = confidenceOf(crossoverPercent, momentum, snapshot);
        } else {
            type = SignalType.HOLD;
            rationale = "No crossover/momentum agreement (crossover "
                    + format(crossoverPercent * 100) + "%, ROC " + format(momentum * 100) + "%)";
            confidence = 0.1;
        }

        return new TradeSignal(instrument, type, confidence, rationale, snapshot, Instant.now());
    }

    private double confidenceOf(double crossoverPercent, double momentum, IndicatorSnapshot snapshot) {
        double crossoverScore = Math.min(1.0,
                Math.abs(crossoverPercent) / (config.momentumThreshold() * 4));
        double momentumScore = Math.min(1.0,
                Math.abs(momentum) / (config.momentumThreshold() * 4));
        double imbalanceAlignment = Math.min(1.0, Math.abs(snapshot.orderBookImbalance()));
        double raw = 0.45 + 0.25 * crossoverScore + 0.2 * momentumScore + 0.1 * imbalanceAlignment;
        return Math.round(Math.min(0.95, raw) * 10_000.0) / 10_000.0;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }
}
