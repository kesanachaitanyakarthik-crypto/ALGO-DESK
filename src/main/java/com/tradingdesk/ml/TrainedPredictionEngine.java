package com.tradingdesk.ml;

import com.tradingdesk.indicators.IndicatorSnapshot;
import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.SignalType;
import com.tradingdesk.model.TradeSignal;
import com.tradingdesk.prediction.PredictionEngine;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A prediction engine backed by the trained logistic-regression classifier.
 *
 * <p>Implements the same {@link PredictionEngine} contract as the rule-based engine, so the pipeline
 * can use either without any other change. Signals from this engine are clearly attributed in their
 * rationale, which the UI surfaces as the source column.
 *
 * <p>Until a model has been trained, this engine returns HOLD with an explicit "model not trained"
 * reason rather than pretending to predict.
 */
public class TrainedPredictionEngine implements PredictionEngine {

    public static final String SOURCE = "ML";

    private final FeatureExtractor extractor;
    private final int windowSize;
    private final ConcurrentMap<String, Deque<Double>> priceHistory = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Double> lastConfidence = new ConcurrentHashMap<>();
    private volatile LogisticRegressionClassifier model;
    private volatile ModelTrainer.ModelReport report;

    public TrainedPredictionEngine(FeatureExtractor extractor, int windowSize) {
        this.extractor = extractor;
        if (windowSize < FeatureExtractor.MIN_WINDOW) {
            throw new IllegalArgumentException(
                    "windowSize must be at least " + FeatureExtractor.MIN_WINDOW);
        }
        this.windowSize = windowSize;
    }

    /** Installs a trained model and its evaluation report. */
    public void install(LogisticRegressionClassifier trainedModel,
                        ModelTrainer.ModelReport modelReport) {
        if (trainedModel == null || !trainedModel.isTrained()) {
            throw new IllegalArgumentException("the installed model must already be trained");
        }
        this.model = trainedModel;
        this.report = modelReport;
    }

    public boolean isReady() {
        return model != null && model.isTrained();
    }

    public ModelTrainer.ModelReport report() {
        return report;
    }

    @Override
    public String name() {
        return "Logistic Regression Classifier";
    }

    @Override
    public String description() {
        return "Trained multinomial logistic regression over technical features. "
                + "Educational output, not investment advice.";
    }

    @Override
    public TradeSignal predict(Instrument instrument, IndicatorSnapshot snapshot) {
        String symbol = instrument.symbol();
        double[] window = windowFor(symbol);

        if (!isReady()) {
            return new TradeSignal(instrument, SignalType.HOLD, 0.0,
                    SOURCE + ": model not trained yet", snapshot, Instant.now());
        }

        if (!extractor.canExtract(window)) {
            return new TradeSignal(instrument, SignalType.HOLD, 0.0,
                    SOURCE + ": insufficient history (" + window.length + "/"
                            + FeatureExtractor.MIN_WINDOW + ")", snapshot, Instant.now());
        }

        double[] features = extractor.extract(window, snapshot.orderBookImbalance());
        int prediction = model.predictClass(features);
        double confidence = model.confidence(features);
        SignalType type = switch (prediction) {
            case 0 -> SignalType.BUY;
            case 1 -> SignalType.SELL;
            default -> SignalType.HOLD;
        };

        lastConfidence.put(symbol, confidence);
        String rationale = String.format(Locale.ROOT,
                "%s: %s with %.1f%% confidence (RSI %.1f, MACD hist %.5f)",
                SOURCE, type, confidence * 100, snapshot.rsi(), snapshot.macdHistogram());

        return new TradeSignal(instrument, type, round(confidence), rationale, snapshot, Instant.now());
    }

    /** Adds a real price observation so the window reflects actual market data. */
    public void observePrice(String symbol, double price) {
        if (!Double.isFinite(price) || price <= 0.0) {
            return;
        }
        Deque<Double> history = priceHistory.computeIfAbsent(symbol, key -> new ArrayDeque<>());
        synchronized (history) {
            history.addLast(price);
            while (history.size() > windowSize) {
                history.removeFirst();
            }
        }
    }

    /** Current feature vector for a symbol, for display in the UI. */
    public double[] featuresFor(String symbol) {
        double[] window = windowFor(symbol);
        if (!extractor.canExtract(window)) {
            return new double[FeatureExtractor.FEATURE_COUNT];
        }
        return extractor.extract(window, 0.0);
    }

    public Map<String, Double> confidences() {
        return Map.copyOf(lastConfidence);
    }

    /** Snapshot of the rolling price window for one symbol. */
    private double[] windowFor(String symbol) {
        Deque<Double> history = priceHistory.get(symbol);
        if (history == null) {
            return new double[0];
        }
        return windowOf(history);
    }

    private static double[] windowOf(Deque<Double> history) {
        synchronized (history) {
            double[] window = new double[history.size()];
            int i = 0;
            for (double value : history) {
                window[i++] = value;
            }
            return window;
        }
    }

    private static double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
