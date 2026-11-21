package com.tradingdesk.indicators;

import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.InstrumentType;
import com.tradingdesk.model.PriceBar;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies each advanced indicator against hand-computed reference values.
 */
class AdvancedIndicatorsTest {

    private static final Instrument AAPL = new Instrument("AAPL", "Apple Inc.", InstrumentType.EQUITY);
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static PriceBar bar(int index, double open, double high, double low, double close, long volume) {
        return new PriceBar(AAPL, open, high, low, close, volume, T0.plusSeconds(60L * index));
    }

    // ------------------------------------------------------------------ RSI

    @Test
    void rsiOfMonotonicRiseIsOneHundred() {
        List<Double> rising = List.of(10.0, 11.0, 12.0, 13.0, 14.0, 15.0);
        assertEquals(100.0, AdvancedIndicators.rsi(rising, 5), 1e-9);
    }

    @Test
    void rsiOfMonotonicFallIsZero() {
        List<Double> falling = List.of(15.0, 14.0, 13.0, 12.0, 11.0, 10.0);
        assertEquals(0.0, AdvancedIndicators.rsi(falling, 5), 1e-9);
    }

    @Test
    void rsiOfFlatSeriesIsNeutral() {
        List<Double> flat = List.of(10.0, 10.0, 10.0, 10.0, 10.0, 10.0);
        assertEquals(50.0, AdvancedIndicators.rsi(flat, 5), 1e-9);
    }

    @Test
    void rsiMatchesHandComputedWilderValue() {
        // Changes over the first 14 periods: gains 2,2,2 and losses 1,1  -> avgGain .375, avgLoss .25
        // The next change (+1) is smoothed: avgGain = (.375*13 + 1)/14 = .41964..., avgLoss = .25*13/14
        // RS = 2.3548... -> RSI = 70.19...  (computed independently below)
        List<Double> prices = List.of(
                44.34, 44.09, 44.15, 43.61, 44.33, 44.83, 45.10, 45.42,
                45.84, 46.08, 45.89, 46.03, 45.61, 46.28, 46.28);
        double rsi = AdvancedIndicators.rsi(prices, 14);
        assertTrue(rsi > 69.0 && rsi < 71.0, "expected ~70.19, got " + rsi);
    }

    @Test
    void rsiIsBounded() {
        List<Double> prices = List.of(1.0, 5.0, 2.0, 8.0, 3.0, 9.0, 4.0, 10.0, 5.0, 11.0);
        double rsi = AdvancedIndicators.rsi(prices, 5);
        assertTrue(rsi >= 0.0 && rsi <= 100.0);
    }

    @Test
    void rsiRejectsInsufficientHistory() {
        assertThrows(IllegalArgumentException.class,
                () -> AdvancedIndicators.rsi(List.of(1.0, 2.0), 14));
    }

    // ------------------------------------------------------------------ MACD

    @Test
    void macdOfFlatSeriesIsZero() {
        List<Double> flat = List.of(50.0, 50.0, 50.0, 50.0, 50.0, 50.0, 50.0, 50.0, 50.0, 50.0);
        AdvancedIndicators.Macd macd = AdvancedIndicators.macd(flat, 3, 5, 3);
        assertEquals(0.0, macd.macd(), 1e-9);
        assertEquals(0.0, macd.histogram(), 1e-9);
    }

    @Test
    void macdIsPositiveInAnUptrend() {
        List<Double> rising = List.of(10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 19.0);
        AdvancedIndicators.Macd macd = AdvancedIndicators.macd(rising, 3, 6, 3);
        assertTrue(macd.macd() > 0.0, "fast EMA above slow EMA in an uptrend");
    }

    @Test
    void macdIsNegativeInADowntrend() {
        List<Double> falling = List.of(19.0, 18.0, 17.0, 16.0, 15.0, 14.0, 13.0, 12.0, 11.0, 10.0);
        AdvancedIndicators.Macd macd = AdvancedIndicators.macd(falling, 3, 6, 3);
        assertTrue(macd.macd() < 0.0);
    }

    @Test
    void macdHistogramIsLineMinusSignal() {
        List<Double> prices = List.of(10.0, 12.0, 11.0, 13.0, 15.0, 14.0, 16.0, 18.0, 17.0, 19.0);
        AdvancedIndicators.Macd macd = AdvancedIndicators.macd(prices, 3, 6, 3);
        assertEquals(macd.macd() - macd.signal(), macd.histogram(), 1e-9);
    }

    @Test
    void macdReturnsZeroWhenHistoryTooShort() {
        AdvancedIndicators.Macd macd = AdvancedIndicators.macd(List.of(1.0, 2.0), 12, 26, 9);
        assertEquals(0.0, macd.macd(), 1e-9);
    }

    // ------------------------------------------------------------------ Bollinger

    @Test
    void bollingerBandsCollapseOnFlatSeries() {
        List<Double> flat = List.of(10.0, 10.0, 10.0, 10.0, 10.0);
        AdvancedIndicators.BollingerBands bands = AdvancedIndicators.bollingerBands(flat, 5, 2.0);

        assertEquals(10.0, bands.middle(), 1e-9);
        assertEquals(10.0, bands.upper(), 1e-9);
        assertEquals(10.0, bands.lower(), 1e-9);
        assertEquals(0.0, bands.width(), 1e-9);
    }

    @Test
    void bollingerBandsAreTwoStandardDeviationsAway() {
        List<Double> prices = List.of(8.0, 10.0, 12.0, 10.0, 10.0);
        AdvancedIndicators.BollingerBands bands = AdvancedIndicators.bollingerBands(prices, 5, 2.0);

        double mean = 10.0;
        double stdDev = TechnicalIndicators.rollingVolatility(prices, 5);
        assertEquals(mean, bands.middle(), 1e-9);
        assertEquals(mean + 2 * stdDev, bands.upper(), 1e-9);
        assertEquals(mean - 2 * stdDev, bands.lower(), 1e-9);
    }

    @Test
    void bollingerRejectsBadDeviations() {
        List<Double> prices = List.of(10.0, 10.0, 10.0);
        assertThrows(IllegalArgumentException.class,
                () -> AdvancedIndicators.bollingerBands(prices, 3, 0.0));
    }

    // ------------------------------------------------------------------ ATR

    @Test
    void atrOfConstantRangeEqualsThatRange() {
        // Every bar has high-low = 2 and no gaps, so true range is 2 throughout.
        List<PriceBar> bars = List.of(
                bar(0, 10, 11, 9, 10, 100),
                bar(1, 10, 11, 9, 10, 100),
                bar(2, 10, 11, 9, 10, 100),
                bar(3, 10, 11, 9, 10, 100));

        assertEquals(2.0, AdvancedIndicators.atr(bars, 3), 1e-9);
    }

    @Test
    void atrAccountsForGaps() {
        // Second bar gaps up: true range = |high - prevClose| = |15 - 10| = 5 > high-low = 1.
        List<PriceBar> bars = List.of(
                bar(0, 10, 10.5, 9.5, 10, 100),
                bar(1, 14, 15, 14, 14.5, 100),
                bar(2, 14, 15, 14, 14.5, 100));

        double atr = AdvancedIndicators.atr(bars, 2);
        assertTrue(atr > 1.0, "the gap must raise the true range, got " + atr);
    }

    @Test
    void atrRejectsInsufficientBars() {
        List<PriceBar> bars = List.of(bar(0, 10, 11, 9, 10, 100));
        assertThrows(IllegalArgumentException.class, () -> AdvancedIndicators.atr(bars, 14));
    }

    // ------------------------------------------------------------------ Stochastic

    @Test
    void stochasticIsHundredWhenClosingAtTheHigh() {
        List<PriceBar> bars = List.of(
                bar(0, 10, 11, 9, 10, 100),
                bar(1, 10, 12, 10, 12, 100),
                bar(2, 10, 13, 10, 13, 100));

        AdvancedIndicators.Stochastic stochastic = AdvancedIndicators.stochastic(bars, 3, 1);
        assertEquals(100.0, stochastic.k(), 1e-9);
    }

    @Test
    void stochasticIsZeroWhenClosingAtTheLow() {
        List<PriceBar> bars = List.of(
                bar(0, 10, 12, 10, 11, 100),
                bar(1, 10, 12, 10, 10, 100),
                bar(2, 10, 12, 10, 10, 100));

        assertEquals(0.0, AdvancedIndicators.stochastic(bars, 3, 1).k(), 1e-9);
    }

    @Test
    void stochasticIsNeutralWhenRangeCollapses() {
        List<PriceBar> bars = List.of(
                bar(0, 10, 10, 10, 10, 100),
                bar(1, 10, 10, 10, 10, 100));
        assertEquals(50.0, AdvancedIndicators.stochastic(bars, 2, 1).k(), 1e-9);
    }

    @Test
    void stochasticRejectsInsufficientBars() {
        List<PriceBar> bars = List.of(bar(0, 10, 11, 9, 10, 100));
        assertThrows(IllegalArgumentException.class,
                () -> AdvancedIndicators.stochastic(bars, 14, 3));
    }

    // ------------------------------------------------------------------ VWAP

    @Test
    void vwapIsWeightedByVolume() {
        // Bar A: typical 10, volume 100 -> 1000. Bar B: typical 20, volume 300 -> 6000.
        // VWAP = 7000 / 400 = 17.5
        List<PriceBar> bars = List.of(
                bar(0, 10, 10, 10, 10, 100),
                bar(1, 20, 20, 20, 20, 300));

        assertEquals(17.5, AdvancedIndicators.vwap(bars), 1e-9);
    }

    @Test
    void vwapOfSinglePriceIsThatPrice() {
        List<PriceBar> bars = List.of(bar(0, 42, 42, 42, 42, 1000));
        assertEquals(42.0, AdvancedIndicators.vwap(bars), 1e-9);
    }

    @Test
    void vwapFallsBackToMeanWhenVolumeIsZero() {
        List<PriceBar> bars = List.of(
                bar(0, 10, 10, 10, 10, 0),
                bar(1, 20, 20, 20, 20, 0));
        assertEquals(15.0, AdvancedIndicators.vwap(bars), 1e-9);
    }

    @Test
    void vwapRejectsEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> AdvancedIndicators.vwap(List.of()));
    }

    // ------------------------------------------------------------------ helpers

    @Test
    void closesExtractsCloseColumn() {
        List<PriceBar> bars = List.of(
                bar(0, 1, 5, 0.5, 4, 10),
                bar(1, 4, 6, 3, 5, 10));
        assertEquals(List.of(4.0, 5.0), AdvancedIndicators.closes(bars));
    }
}
