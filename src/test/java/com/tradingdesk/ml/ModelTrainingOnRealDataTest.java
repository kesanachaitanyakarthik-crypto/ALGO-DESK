package com.tradingdesk.ml;

import com.tradingdesk.marketdata.BinancePublicProvider;
import com.tradingdesk.marketdata.Candle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Trains the classifier on <em>real</em> Binance history and reports the measured accuracy.
 *
 * <p>Opt-in via {@code -Dlive.enabled=true} because it needs network access. It asserts only that
 * training completes and produces a valid report — it does <strong>not</strong> assert a flattering
 * accuracy, because short-horizon price direction is close to noise and asserting a high score would
 * be dishonest. The measured number is printed for the report.
 */
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "live.enabled", matches = "true")
class ModelTrainingOnRealDataTest {

    @Test
    void trainsAndReportsOnRealBinanceHistory() {
        BinancePublicProvider provider = new BinancePublicProvider();
        boolean connected = provider.connect();
        assumeTrue(connected, "Binance not reachable from this machine");

        List<Candle> candles = provider.history("BTCUSDT", "1h", 1000);
        provider.disconnect();

        assumeTrue(candles.size() > 300, "not enough real candles to train on");

        double[] closes = new double[candles.size()];
        for (int i = 0; i < candles.size(); i++) {
            closes[i] = candles.get(i).close();
        }

        ModelTrainer trainer = new ModelTrainer();
        ModelTrainer.ModelReport report = trainer.trainAndEvaluate(closes, 0.7);

        System.out.println("=== REAL-DATA MODEL EVALUATION (BTCUSDT 1h, "
                + candles.size() + " candles) ===");
        System.out.println("horizon bars     : " + trainer.horizon());
        System.out.println("train / test     : " + report.trainingSamples() + " / " + report.testSamples());
        System.out.println("train accuracy   : " + pct(report.trainingAccuracy()));
        System.out.println("TEST accuracy    : " + pct(report.testAccuracy()));
        System.out.println("majority baseline: " + pct(report.baselineAccuracy()));
        System.out.println("precision        : " + report.precisionByClass());
        System.out.println("recall           : " + report.recallByClass());
        System.out.println("confusion matrix : " + java.util.Arrays.deepToString(report.confusionMatrix()));
        System.out.println("summary          : " + report.summary());

        assertTrue(report.model().isTrained(), "the model must actually be trained");
        assertTrue(report.testAccuracy() >= 0.0 && report.testAccuracy() <= 1.0);
        assertTrue(report.testSamples() > 0);
        // Deliberately no assertion that accuracy is high.
    }

    private static String pct(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100.0);
    }
}
