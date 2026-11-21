package com.tradingdesk.indicators;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechnicalIndicatorsTest {

    @Test
    void simpleMovingAverageAveragesLastPeriod() {
        assertEquals(2.0, TechnicalIndicators.simpleMovingAverage(List.of(1.0, 2.0, 3.0), 3), 1e-9);
        assertEquals(2.5, TechnicalIndicators.simpleMovingAverage(List.of(1.0, 2.0, 3.0), 2), 1e-9);
    }

    @Test
    void exponentialMovingAverageWeighsRecentPricesHigher() {
        double ema = TechnicalIndicators.exponentialMovingAverage(List.of(1.0, 2.0, 3.0), 3);
        assertTrue(ema > 2.0 && ema < 3.0, "EMA should sit between the mean and the latest price");
    }

    @Test
    void rateOfChangeUsesLookbackPeriod() {
        assertEquals(1.0, TechnicalIndicators.rateOfChange(List.of(10.0, 15.0, 20.0), 2), 1e-9);
        assertEquals(0.0, TechnicalIndicators.rateOfChange(List.of(10.0, 15.0, 20.0), 0), 1e-9,
                "zero lookback yields no change");
    }

    @Test
    void rollingVolatilityIsZeroForFlatSeries() {
        assertEquals(0.0, TechnicalIndicators.rollingVolatility(List.of(5.0, 5.0, 5.0), 3), 1e-9);
        assertTrue(TechnicalIndicators.rollingVolatility(List.of(1.0, 3.0), 2) > 0.0);
    }

    @Test
    void orderBookImbalanceIsBoundedAndSigned() {
        assertEquals(1.0, TechnicalIndicators.orderBookImbalance(100, 0), 1e-9);
        assertEquals(-1.0, TechnicalIndicators.orderBookImbalance(0, 100), 1e-9);
        assertEquals(0.0, TechnicalIndicators.orderBookImbalance(0, 0), 1e-9);
        assertEquals(0.5, TechnicalIndicators.orderBookImbalance(75, 25), 1e-9);
    }

    @Test
    void rejectsInsufficientHistory() {
        assertThrows(IllegalArgumentException.class,
                () -> TechnicalIndicators.simpleMovingAverage(List.of(1.0), 5));
        assertThrows(IllegalArgumentException.class,
                () -> TechnicalIndicators.rateOfChange(List.of(1.0, 2.0), 5));
    }
}
