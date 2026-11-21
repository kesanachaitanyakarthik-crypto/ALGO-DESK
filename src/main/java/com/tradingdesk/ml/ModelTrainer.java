package com.tradingdesk.ml;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a labelled dataset from historical prices and trains/evaluates the classifier.
 *
 * <h2>No look-ahead, no leakage</h2>
 * <ul>
 *   <li>Features at index {@code i} are computed from prices up to {@code i} only.</li>
 *   <li>The label at {@code i} is the <em>forward</em> return over {@code horizon} bars — the thing
 *       being predicted, which no feature can see.</li>
 *   <li>The train/test split is strictly chronological: the model is always evaluated on data that
 *       comes after everything it trained on. No shuffling across the split.</li>
 * </ul>
 */
public class ModelTrainer {

    /** Result of training: the fitted model plus honest evaluation metrics. */
    public record ModelReport(
            LogisticRegressionClassifier model,
            int trainingSamples,
            int testSamples,
            double trainingAccuracy,
            double testAccuracy,
            double baselineAccuracy,
            Map<String, Double> precisionByClass,
            Map<String, Double> recallByClass,
            int[][] confusionMatrix,
            Instant trainedAt,
            String summary
    ) {
    }

    private final FeatureExtractor extractor;
    private final int horizon;
    private final double buyThresholdPercent;
    private final double sellThresholdPercent;

    public ModelTrainer() {
        // Thresholds are matched to typical 1-hour crypto volatility (~0.3-0.5%). A much tighter
        // band makes almost every sample HOLD, which is a class-imbalance problem rather than a
        // modelling one, and would make the accuracy figure meaningless.
        this(new FeatureExtractor(), 5, 0.30, -0.30);
    }

    /**
     * @param extractor           feature builder
     * @param horizon             forward bars used to label a sample
     * @param buyThresholdPercent forward return above which the label is BUY
     * @param sellThresholdPercent forward return below which the label is SELL
     */
    public ModelTrainer(FeatureExtractor extractor, int horizon,
                        double buyThresholdPercent, double sellThresholdPercent) {
        if (horizon <= 0) {
            throw new IllegalArgumentException("horizon must be positive");
        }
        this.extractor = extractor;
        this.horizon = horizon;
        this.buyThresholdPercent = buyThresholdPercent;
        this.sellThresholdPercent = sellThresholdPercent;
    }

    /**
     * Trains and evaluates on the supplied price series.
     *
     * @param prices  chronological closing prices
     * @param trainRatio fraction used for training, e.g. 0.7
     */
    public ModelReport trainAndEvaluate(double[] prices, double trainRatio) {
        if (prices == null || prices.length < FeatureExtractor.MIN_WINDOW + horizon + 10) {
            throw new IllegalArgumentException("not enough prices to train a model");
        }
        if (trainRatio <= 0.1 || trainRatio >= 1.0) {
            throw new IllegalArgumentException("trainRatio must be within (0.1, 1.0)");
        }

        List<double[]> features = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();

        for (int i = FeatureExtractor.MIN_WINDOW - 1; i < prices.length - horizon; i++) {
            double[] window = slice(prices, i);
            features.add(extractor.extract(window, 0.0));
            labels.add(labelFor(prices, i));
        }

        if (features.size() < 20) {
            throw new IllegalArgumentException(
                    "not enough samples after windowing: " + features.size());
        }

        int split = (int) Math.floor(features.size() * trainRatio);
        List<double[]> trainX = features.subList(0, split);
        List<Integer> trainY = labels.subList(0, split);
        List<double[]> testX = features.subList(split, features.size());
        List<Integer> testY = labels.subList(split, labels.size());

        LogisticRegressionClassifier model = new LogisticRegressionClassifier(
                FeatureExtractor.FEATURE_COUNT);
        double trainAccuracy = model.fit(trainX, trainY);
        double testAccuracy = model.accuracy(testX, testY);

        // Baseline: always predict the most common training label. A model that cannot beat this
        // is not useful, and reporting it keeps the evaluation honest.
        int majority = majorityClass(trainY);
        int correct = 0;
        for (int label : testY) {
            if (label == majority) {
                correct++;
            }
        }
        double baseline = testY.isEmpty() ? 0.0 : (double) correct / testY.size();

        Map<String, Double> precision = new LinkedHashMap<>();
        Map<String, Double> recall = new LinkedHashMap<>();
        int[][] confusion = confusionMatrix(model, testX, testY);

        for (int c = 0; c < LogisticRegressionClassifier.CLASSES.length; c++) {
            String name = LogisticRegressionClassifier.CLASSES[c];
            int tp = confusion[c][c];
            int predicted = 0;
            int actual = 0;
            for (int j = 0; j < LogisticRegressionClassifier.CLASSES.length; j++) {
                predicted += confusion[j][c];
                actual += confusion[c][j];
            }
            precision.put(name, predicted == 0 ? 0.0 : round((double) tp / predicted));
            recall.put(name, actual == 0 ? 0.0 : round((double) tp / actual));
        }

        String summary = String.format(java.util.Locale.ROOT,
                "train %.1f%% | test %.1f%% | baseline %.1f%% | samples %d/%d",
                trainAccuracy * 100, testAccuracy * 100, baseline * 100,
                trainX.size(), testX.size());

        return new ModelReport(model, trainX.size(), testX.size(),
                round(trainAccuracy), round(testAccuracy), round(baseline),
                precision, recall, confusion, Instant.now(), summary);
    }

    /** Label for the sample at {@code i}: BUY / SELL / HOLD from the forward return. */
    private int labelFor(double[] prices, int i) {
        double current = prices[i];
        double future = prices[Math.min(prices.length - 1, i + horizon)];
        if (current <= 0.0) {
            return 2;
        }
        double forwardPercent = (future - current) / current * 100.0;
        if (forwardPercent > buyThresholdPercent) {
            return 0; // BUY
        }
        if (forwardPercent < sellThresholdPercent) {
            return 1; // SELL
        }
        return 2; // HOLD
    }

    private static double[] slice(double[] prices, int endInclusive) {
        int start = Math.max(0, endInclusive - 40 + 1);
        double[] window = new double[endInclusive - start + 1];
        System.arraycopy(prices, start, window, 0, window.length);
        return window;
    }

    private static int majorityClass(List<Integer> labels) {
        int[] counts = new int[LogisticRegressionClassifier.CLASSES.length];
        for (int label : labels) {
            counts[label]++;
        }
        int best = 0;
        for (int c = 1; c < counts.length; c++) {
            if (counts[c] > counts[best]) {
                best = c;
            }
        }
        return best;
    }

    private static int[][] confusionMatrix(LogisticRegressionClassifier model,
                                           List<double[]> features, List<Integer> labels) {
        int n = LogisticRegressionClassifier.CLASSES.length;
        int[][] matrix = new int[n][n];
        for (int i = 0; i < features.size(); i++) {
            int actual = labels.get(i);
            int predicted = model.predictClass(features.get(i));
            matrix[actual][predicted]++;
        }
        return matrix;
    }

    private static double round(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    public int horizon() {
        return horizon;
    }
}
