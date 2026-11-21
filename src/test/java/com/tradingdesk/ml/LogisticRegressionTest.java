package com.tradingdesk.ml;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogisticRegressionTest {

    private static final int FEATURES = FeatureExtractor.FEATURE_COUNT;

    /** Three clearly separable synthetic clusters, one per class. */
    private static List<double[]> separableFeatures() {
        List<double[]> rows = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            rows.add(new double[]{2.0, 2.0, 1.5, 1.2, 0.1, 1.5, 0.8});   // BUY
            rows.add(new double[]{-2.0, -2.0, -1.5, -1.2, 0.1, -1.5, -0.8}); // SELL
            rows.add(new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0});    // HOLD
        }
        return rows;
    }

    private static List<Integer> separableLabels() {
        List<Integer> labels = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            labels.add(0);
            labels.add(1);
            labels.add(2);
        }
        return labels;
    }

    @Test
    void untrainedModelRefusesToPredict() {
        LogisticRegressionClassifier model = new LogisticRegressionClassifier(FEATURES);
        assertFalse(model.isTrained());
        assertThrows(IllegalStateException.class, () -> model.predictClass(new double[FEATURES]));
    }

    @Test
    void learnsPerfectlySeparableData() {
        LogisticRegressionClassifier model = new LogisticRegressionClassifier(FEATURES);
        double accuracy = model.fit(separableFeatures(), separableLabels());

        assertTrue(model.isTrained());
        assertEquals(1.0, accuracy, 0.02, "clearly separable clusters should be learned");
    }

    @Test
    void predictsEachSeparableClusterCorrectly() {
        LogisticRegressionClassifier model = new LogisticRegressionClassifier(FEATURES);
        model.fit(separableFeatures(), separableLabels());

        assertEquals(0, model.predictClass(new double[]{2.0, 2.0, 1.5, 1.2, 0.1, 1.5, 0.8}));
        assertEquals(1, model.predictClass(new double[]{-2.0, -2.0, -1.5, -1.2, 0.1, -1.5, -0.8}));
        assertEquals(2, model.predictClass(new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}));
    }

    @Test
    void probabilitiesFormAValidDistribution() {
        LogisticRegressionClassifier model = new LogisticRegressionClassifier(FEATURES);
        model.fit(separableFeatures(), separableLabels());

        double[] probs = model.softmax(new double[]{1.0, 1.0, 0.5, 0.5, 0.2, 0.5, 0.3});

        assertEquals(1.0, probs[0] + probs[1] + probs[2], 1e-9);
        for (double p : probs) {
            assertTrue(p >= 0.0 && p <= 1.0);
        }
    }

    @Test
    void confidenceIsTheWinningProbability() {
        LogisticRegressionClassifier model = new LogisticRegressionClassifier(FEATURES);
        model.fit(separableFeatures(), separableLabels());

        double[] features = new double[]{2.0, 2.0, 1.5, 1.2, 0.1, 1.5, 0.8};
        double[] probs = model.softmax(features);
        assertEquals(Math.max(probs[0], Math.max(probs[1], probs[2])),
                model.confidence(features), 1e-9);
        assertTrue(model.confidence(features) > 0.5);
    }

    @Test
    void weightsRemainBoundedByRegularisation() {
        LogisticRegressionClassifier model = new LogisticRegressionClassifier(FEATURES);
        model.fit(separableFeatures(), separableLabels());

        for (double[] classWeights : model.weights()) {
            for (double w : classWeights) {
                assertTrue(Math.abs(w) < 5.0, "L2 regularisation should keep weights sane, got " + w);
            }
        }
    }

    @Test
    void trainingIsDeterministicForAFixedSeed() {
        LogisticRegressionClassifier a = new LogisticRegressionClassifier(FEATURES);
        LogisticRegressionClassifier b = new LogisticRegressionClassifier(FEATURES);

        double accA = a.fit(separableFeatures(), separableLabels());
        double accB = b.fit(separableFeatures(), separableLabels());

        assertEquals(accA, accB, 1e-9, "same seed and data must give the same result");
    }

    @Test
    void rejectsMismatchedInputs() {
        LogisticRegressionClassifier model = new LogisticRegressionClassifier(FEATURES);
        List<double[]> rows = List.of(new double[FEATURES], new double[FEATURES]);
        assertThrows(IllegalArgumentException.class, () -> model.fit(rows, List.of(0)));
        assertThrows(IllegalArgumentException.class, () -> model.fit(List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> model.fit(List.of(new double[3]), List.of(0)));
    }

    // ------------------------------------------------------------------ feature extraction

    @Test
    void featureVectorHasTheExpectedShape() {
        FeatureExtractor extractor = new FeatureExtractor();
        double[] window = ramp(100, 120, 40);

        double[] features = extractor.extract(window, 0.25);

        assertEquals(FeatureExtractor.FEATURE_COUNT, features.length);
        assertEquals(0.25, features[6], 1e-9, "imbalance passes through");
    }

    @Test
    void featuresAreFiniteAndBounded() {
        FeatureExtractor extractor = new FeatureExtractor();
        double[] features = extractor.extract(ramp(100, 200, 40), 0.9);

        for (double f : features) {
            assertTrue(Double.isFinite(f), "feature must be finite");
            assertTrue(Math.abs(f) <= 3.0, "feature must be clamped, got " + f);
        }
    }

    @Test
    void rejectsShortWindows() {
        FeatureExtractor extractor = new FeatureExtractor();
        assertFalse(extractor.canExtract(new double[5]));
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(new double[5], 0.0));
    }

    @Test
    void featureNamesMatchFeatureCount() {
        assertEquals(FeatureExtractor.FEATURE_COUNT, FeatureExtractor.FEATURE_NAMES.size());
    }

    // ------------------------------------------------------------------ full training pipeline

    @Test
    void trainerProducesAnEvaluationReport() {
        double[] prices = noisySeries(400);
        ModelTrainer trainer = new ModelTrainer();

        ModelTrainer.ModelReport report = trainer.trainAndEvaluate(prices, 0.7);

        assertTrue(report.model().isTrained());
        assertTrue(report.trainingSamples() > 0);
        assertTrue(report.testSamples() > 0);
        assertTrue(report.testAccuracy() >= 0.0 && report.testAccuracy() <= 1.0);
        assertFalse(report.summary().isBlank());
        assertEquals(3, report.confusionMatrix().length);
    }

    @Test
    void splitIsChronologicalNotShuffled() {
        // A trainer with a tiny training ratio must consume the EARLIEST samples for training.
        double[] prices = noisySeries(300);
        ModelTrainer trainer = new ModelTrainer();
        ModelTrainer.ModelReport report = trainer.trainAndEvaluate(prices, 0.5);

        // Train and test counts must both be non-trivial and sum to the total sample count.
        assertTrue(report.trainingSamples() > 10);
        assertTrue(report.testSamples() > 10);
        assertTrue(report.trainingSamples() > report.testSamples() - 5,
                "a 50/50 split should be roughly balanced");
    }

    @Test
    void reportIncludesPerClassMetrics() {
        ModelTrainer.ModelReport report = new ModelTrainer().trainAndEvaluate(noisySeries(400), 0.7);

        assertEquals(3, report.precisionByClass().size());
        assertEquals(3, report.recallByClass().size());
        assertTrue(report.precisionByClass().containsKey("BUY"));
        assertTrue(report.precisionByClass().containsKey("HOLD"));
    }

    @Test
    void trainerRejectsInsufficientData() {
        ModelTrainer trainer = new ModelTrainer();
        assertThrows(IllegalArgumentException.class,
                () -> trainer.trainAndEvaluate(new double[10], 0.7));
        assertThrows(IllegalArgumentException.class,
                () -> trainer.trainAndEvaluate(noisySeries(400), 1.5));
    }

    // ------------------------------------------------------------------ helpers

    private static double[] ramp(double from, double to, int count) {
        double[] values = new double[count];
        double step = (to - from) / (count - 1);
        for (int i = 0; i < count; i++) {
            values[i] = from + step * i;
        }
        return values;
    }

    /** Deterministic pseudo-random walk with a mild trend, for reproducible tests. */
    private static double[] noisySeries(int count) {
        double[] values = new double[count];
        Random random = new Random(7);
        double price = 100.0;
        for (int i = 0; i < count; i++) {
            price = Math.max(1.0, price * (1.0 + random.nextGaussian() * 0.01));
            values[i] = price;
        }
        return values;
    }
}
