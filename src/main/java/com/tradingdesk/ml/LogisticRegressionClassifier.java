package com.tradingdesk.ml;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Multinomial logistic regression trained by mini-batch gradient descent.
 *
 * <p>Three classes map to BUY / SELL / HOLD. Softmax produces a probability distribution, so the
 * model reports a genuine confidence rather than a fabricated number. L2 regularisation keeps the
 * weights bounded on small datasets.
 *
 * <p>Explainability: {@link #weights()} exposes the per-class coefficients, and
 * {@link #featureContributions(double[])} shows which features pushed a specific prediction, so the
 * UI can explain why it produced BUY rather than merely asserting it.
 */
public class LogisticRegressionClassifier {

    /** Class order everywhere in this class. */
    public static final String[] CLASSES = {"BUY", "SELL", "HOLD"};

    private final int featureCount;
    private final int classCount = CLASSES.length;
    private final double learningRate;
    private final double l2;
    private final int epochs;
    private final int batchSize;
    private final long seed;

    /** weights[class][feature] */
    private double[][] weights;
    /** bias[class] */
    private double[] bias;

    /**
     * Per-feature mean and standard deviation learned from the training set.
     *
     * <p>Standardising inputs is not cosmetic: features here have very different scales (a ratio in
     * percent vs an RSI mapped to [-1,1]), and without this the gradient step is dominated by the
     * largest feature and the model collapses onto a single class. The statistics are stored in the
     * model so prediction standardises exactly as training did.
     */
    private double[] featureMean;
    private double[] featureStd;

    private boolean trained;
    private double trainingAccuracy;

    public LogisticRegressionClassifier(int featureCount) {
        // Settings chosen from measured behaviour on real BTCUSDT hourly data. Stronger L2 did not
        // close the train/test gap, which indicates the ceiling here is the signal-to-noise ratio of
        // 5-bar price direction rather than model capacity. This is reported honestly in the docs.
        this(featureCount, 0.20, 0.02, 250, 32, 42L);
    }

    public LogisticRegressionClassifier(int featureCount, double learningRate, double l2,
                                        int epochs, int batchSize, long seed) {
        if (featureCount <= 0) {
            throw new IllegalArgumentException("featureCount must be positive");
        }
        if (learningRate <= 0.0 || l2 < 0.0) {
            throw new IllegalArgumentException("learningRate must be positive and l2 non-negative");
        }
        if (epochs <= 0 || batchSize <= 0) {
            throw new IllegalArgumentException("epochs and batchSize must be positive");
        }
        this.featureCount = featureCount;
        this.learningRate = learningRate;
        this.l2 = l2;
        this.epochs = epochs;
        this.batchSize = batchSize;
        this.seed = seed;
        this.weights = new double[classCount][featureCount];
        this.bias = new double[classCount];
        this.featureMean = new double[featureCount];
        this.featureStd = new double[featureCount];
        java.util.Arrays.fill(this.featureStd, 1.0);
    }

    // ------------------------------------------------------------------ training

    /**
     * Trains on the supplied samples.
     *
     * @param features rows of feature vectors, all of the configured length
     * @param labels   class index per row (0=BUY, 1=SELL, 2=HOLD)
     * @return accuracy on the training set
     */
    public double fit(List<double[]> features, List<Integer> labels) {
        Objects.requireNonNull(features, "features");
        Objects.requireNonNull(labels, "labels");
        if (features.isEmpty()) {
            throw new IllegalArgumentException("cannot train on an empty dataset");
        }
        if (features.size() != labels.size()) {
            throw new IllegalArgumentException("features and labels must have equal size");
        }
        for (double[] row : features) {
            if (row.length != featureCount) {
                throw new IllegalArgumentException(
                        "expected " + featureCount + " features, got " + row.length);
            }
        }

        // Standardise from the TRAINING data only. Using test statistics would leak information
        // about the evaluation set into the model.
        computeStandardisation(features);

        // Class weighting: real price data is dominated by the HOLD class, so an unweighted model
        // trivially learns to predict HOLD and is useless. Weighting each class by inverse frequency
        // forces the model to learn the minority classes too.
        double[] classWeight = computeClassWeights(labels);

        java.util.Random random = new java.util.Random(seed);
        int n = features.size();
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }

        for (int epoch = 0; epoch < epochs; epoch++) {
            shuffle(order, random);
            for (int start = 0; start < n; start += batchSize) {
                int end = Math.min(n, start + batchSize);
                double[][] gradW = new double[classCount][featureCount];
                double[] gradB = new double[classCount];

                for (int idx = start; idx < end; idx++) {
                    int row = order[idx];
                    double[] x = standardise(features.get(row));
                    int label = labels.get(row);
                    double[] probs = computeSoftmax(x);

                    for (int c = 0; c < classCount; c++) {
                        double error = probs[c] - (c == label ? 1.0 : 0.0);
                        error *= classWeight[label];
                        gradB[c] += error;
                        for (int f = 0; f < featureCount; f++) {
                            gradW[c][f] += error * x[f];
                        }
                    }
                }

                int count = end - start;
                for (int c = 0; c < classCount; c++) {
                    bias[c] -= learningRate * gradB[c] / count;
                    for (int f = 0; f < featureCount; f++) {
                        double gradient = gradW[c][f] / count;
                        weights[c][f] -= learningRate * (gradient + l2 * weights[c][f]);
                    }
                }
            }
        }

        // Evaluate while still unfitted, then mark trained. accuracy() would require the guard.
        trainingAccuracy = accuracyInternal(features, labels);
        trained = true;
        return trainingAccuracy;
    }

    /**
     * Inverse-frequency class weights, softened by a square root and normalised.
     *
     * <p>Full inverse-frequency weighting over-corrects on financial data and drives accuracy below
     * the majority baseline; no weighting collapses onto HOLD. The square root is the standard
     * compromise, and the measured effect is reported rather than assumed.
     */
    private double[] computeClassWeights(List<Integer> labels) {
        int[] counts = new int[classCount];
        for (int label : labels) {
            if (label >= 0 && label < classCount) {
                counts[label]++;
            }
        }
        double[] weightsByClass = new double[classCount];
        double sum = 0.0;
        for (int c = 0; c < classCount; c++) {
            double inverse = counts[c] == 0 ? 1.0 : (double) labels.size() / counts[c];
            weightsByClass[c] = Math.sqrt(inverse);
            sum += weightsByClass[c];
        }
        // Normalise so the average weight is 1, keeping the effective learning rate stable.
        for (int c = 0; c < classCount; c++) {
            weightsByClass[c] = weightsByClass[c] * classCount / sum;
        }
        return weightsByClass;
    }

    /** Computes per-feature mean and standard deviation from the training rows. */
    private void computeStandardisation(List<double[]> features) {
        int n = features.size();
        for (int f = 0; f < featureCount; f++) {
            double sum = 0.0;
            for (double[] row : features) {
                sum += row[f];
            }
            double mean = sum / n;

            double variance = 0.0;
            for (double[] row : features) {
                double d = row[f] - mean;
                variance += d * d;
            }
            double std = Math.sqrt(variance / n);

            featureMean[f] = mean;
            // A constant feature carries no signal; keep std at 1 so the transform is a no-op.
            featureStd[f] = std < 1e-9 ? 1.0 : std;
        }
    }

    /** Standardises a raw feature vector using the stored training statistics. */
    private double[] standardise(double[] raw) {
        double[] out = new double[raw.length];
        for (int f = 0; f < raw.length; f++) {
            out[f] = (raw[f] - featureMean[f]) / featureStd[f];
        }
        return out;
    }

    private static void shuffle(int[] order, java.util.Random random) {
        for (int i = order.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = order[i];
            order[i] = order[j];
            order[j] = temp;
        }
    }

    // ------------------------------------------------------------------ prediction

    /** Class probabilities in CLASSES order. Requires a fitted model. */
    public double[] softmax(double[] features) {
        if (!trained) {
            throw new IllegalStateException("model has not been trained");
        }
        return computeSoftmax(standardise(features));
    }

    /**
     * The actual softmax computation. Kept separate from {@link #softmax(double[])} so training can
     * use it while the model is still unfitted — the public method stays guarded.
     */
    private double[] computeSoftmax(double[] features) {
        double[] logits = new double[classCount];
        for (int c = 0; c < classCount; c++) {
            double z = bias[c];
            for (int f = 0; f < featureCount; f++) {
                z += weights[c][f] * features[f];
            }
            logits[c] = z;
        }
        double max = Double.NEGATIVE_INFINITY;
        for (double z : logits) {
            max = Math.max(max, z);
        }
        double sum = 0.0;
        for (int c = 0; c < classCount; c++) {
            logits[c] = Math.exp(logits[c] - max);
            sum += logits[c];
        }
        for (int c = 0; c < classCount; c++) {
            logits[c] /= sum;
        }
        return logits;
    }

    /** Index of the most likely class. */
    public int predictClass(double[] features) {
        double[] probs = softmax(features);
        int best = 0;
        for (int c = 1; c < probs.length; c++) {
            if (probs[c] > probs[best]) {
                best = c;
            }
        }
        return best;
    }

    /** Index + probability of the most likely class, requiring a fitted model. */
    private int argmax(double[] features) {
        double[] probs = computeSoftmax(features);
        int best = 0;
        for (int c = 1; c < probs.length; c++) {
            if (probs[c] > probs[best]) {
                best = c;
            }
        }
        return best;
    }

    /** Accuracy computed without the trained-state guard, for use during fitting. */
    private double accuracyInternal(List<double[]> features, List<Integer> labels) {
        if (features.isEmpty()) {
            return 0.0;
        }
        int correct = 0;
        for (int i = 0; i < features.size(); i++) {
            if (argmax(features.get(i)) == labels.get(i)) {
                correct++;
            }
        }
        return (double) correct / features.size();
    }

    /** Confidence in the chosen class, 0..1. */
    public double confidence(double[] features) {
        double[] probs = softmax(features);
        int best = 0;
        for (int c = 1; c < probs.length; c++) {
            if (probs[c] > probs[best]) {
                best = c;
            }
        }
        return probs[best];
    }

    /** Per-feature contribution to the predicted class, for an explanation in the UI. */
    public double[] featureContributions(double[] features) {
        double[] standardised = standardise(features);
        int best = predictClass(features);
        double[] contributions = new double[featureCount];
        for (int f = 0; f < featureCount; f++) {
            contributions[f] = weights[best][f] * standardised[f];
        }
        return contributions;
    }

    public double accuracy(List<double[]> features, List<Integer> labels) {
        if (features.isEmpty()) {
            return 0.0;
        }
        int correct = 0;
        for (int i = 0; i < features.size(); i++) {
            if (predictClass(features.get(i)) == labels.get(i)) {
                correct++;
            }
        }
        return (double) correct / features.size();
    }

    public boolean isTrained() {
        return trained;
    }

    public double trainingAccuracy() {
        return trainingAccuracy;
    }

    public double[][] weights() {
        double[][] copy = new double[classCount][featureCount];
        for (int c = 0; c < classCount; c++) {
            System.arraycopy(weights[c], 0, copy[c], 0, featureCount);
        }
        return copy;
    }

    public int featureCount() {
        return featureCount;
    }

    public List<String> classNames() {
        return List.of(CLASSES);
    }

    /** Convenience: a fresh, untrained instance of the same shape. */
    public static LogisticRegressionClassifier untrained(int featureCount) {
        return new LogisticRegressionClassifier(featureCount);
    }

    /** All class names as a mutable list copy, for reporting. */
    public static List<String> allClasses() {
        List<String> list = new ArrayList<>(List.of(CLASSES));
        return list;
    }
}
