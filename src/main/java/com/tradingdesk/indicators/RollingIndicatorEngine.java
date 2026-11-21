package com.tradingdesk.indicators;

import com.tradingdesk.model.MarketEvent;
import com.tradingdesk.model.PriceBar;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Rolling indicator calculation over a bounded window of prices.
 *
 * <p>Computes the core readings (SMA, EMA, ROC, volatility, imbalance) plus the advanced set
 * (RSI, MACD, Bollinger, ATR, Stochastic, VWAP) from the <em>same</em> window, so all values in a
 * snapshot describe the same instant. Advanced values degrade gracefully to 0.0 until enough history
 * exists rather than throwing mid-pipeline.
 */
public class RollingIndicatorEngine implements IndicatorEngine {

    private static final int RSI_PERIOD = 14;
    private static final int MACD_FAST = 12;
    private static final int MACD_SLOW = 26;
    private static final int MACD_SIGNAL = 9;
    private static final int BOLLINGER_PERIOD = 20;
    private static final int ATR_PERIOD = 14;
    private static final int STOCHASTIC_PERIOD = 14;
    private static final int STOCHASTIC_SMOOTHING = 3;

    private final int windowSize;
    private final int rocPeriod;
    private final Deque<Double> prices = new ArrayDeque<>();
    private final Deque<Double> imbalances = new ArrayDeque<>();
    private final Deque<Double> highs = new ArrayDeque<>();
    private final Deque<Double> lows = new ArrayDeque<>();
    private final Deque<Double> volumes = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();

    public RollingIndicatorEngine(int windowSize, int rocPeriod) {
        if (windowSize <= 0 || rocPeriod <= 0) {
            throw new IllegalArgumentException("window sizes must be positive");
        }
        this.windowSize = windowSize;
        this.rocPeriod = rocPeriod;
    }

    @Override
    public void ingest(MarketEvent event) {
        lock.lock();
        try {
            append(event.lastPrice());
            imbalances.addLast(event.simulatedOrderBookImbalance() / 100.0);
            // The event carries bid/ask, so the session high/low can be inferred from the quote
            // rather than needing full OHLC bars.
            highs.addLast(Math.max(event.lastPrice(), event.askPrice()));
            lows.addLast(Math.min(event.lastPrice(), event.bidPrice()));
            volumes.addLast((double) event.volume());
            trim(imbalances);
            trim(highs);
            trim(lows);
            trim(volumes);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void ingest(PriceBar bar) {
        lock.lock();
        try {
            append(bar.close());
            highs.addLast(bar.high());
            lows.addLast(bar.low());
            volumes.addLast((double) bar.volume());
            trim(highs);
            trim(lows);
            trim(volumes);
        } finally {
            lock.unlock();
        }
    }

    private void append(double price) {
        prices.addLast(price);
        while (prices.size() > windowSize) {
            prices.removeFirst();
        }
    }

    private void trim(Deque<Double> deque) {
        while (deque.size() > windowSize) {
            deque.removeFirst();
        }
    }

    @Override
    public IndicatorSnapshot latest() {
        lock.lock();
        try {
            List<Double> window = List.copyOf(prices);
            if (window.isEmpty()) {
                return IndicatorSnapshot.empty(Instant.now());
            }

            int period = Math.min(windowSize, window.size());
            double sma = TechnicalIndicators.simpleMovingAverage(window, period);
            double ema = TechnicalIndicators.exponentialMovingAverage(window, period);
            double roc = window.size() > rocPeriod
                    ? TechnicalIndicators.rateOfChange(window, rocPeriod)
                    : 0.0;
            double volatility = TechnicalIndicators.rollingVolatility(window, period);
            double imbalance = imbalances.isEmpty()
                    ? 0.0
                    : imbalances.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

            double rsi = window.size() > RSI_PERIOD
                    ? AdvancedIndicators.rsi(window, RSI_PERIOD) : 0.0;

            AdvancedIndicators.Macd macd = AdvancedIndicators.macd(
                    window, MACD_FAST, MACD_SLOW, MACD_SIGNAL);

            double upper = 0.0;
            double middle = 0.0;
            double lower = 0.0;
            if (window.size() >= BOLLINGER_PERIOD) {
                AdvancedIndicators.BollingerBands bands = AdvancedIndicators.bollingerBands(
                        window, BOLLINGER_PERIOD, 2.0);
                upper = bands.upper();
                middle = bands.middle();
                lower = bands.lower();
            }

            List<PriceBar> bars = asBars(window);
            double atr = 0.0;
            if (bars.size() >= ATR_PERIOD + 1) {
                atr = AdvancedIndicators.atr(bars, ATR_PERIOD);
            }

            double stochasticK = 0.0;
            double stochasticD = 0.0;
            if (bars.size() >= STOCHASTIC_PERIOD) {
                AdvancedIndicators.Stochastic stochastic = AdvancedIndicators.stochastic(
                        bars, STOCHASTIC_PERIOD, STOCHASTIC_SMOOTHING);
                stochasticK = stochastic.k();
                stochasticD = stochastic.d();
            }

            double vwap = AdvancedIndicators.vwap(bars);

            return new IndicatorSnapshot(sma, ema, roc, volatility, imbalance,
                    window.size(), Instant.now(),
                    rsi, macd.macd(), macd.signal(), macd.histogram(),
                    upper, middle, lower, atr, stochasticK, stochasticD, vwap);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Reconstructs bars from the rolling window so ATR/Stochastic/VWAP can reuse the existing
     * implementations. High/low come from the observed extremes; open is the prior close.
     */
    private List<PriceBar> asBars(List<Double> window) {
        List<Double> highList = new ArrayList<>(highs);
        List<Double> lowList = new ArrayList<>(lows);
        List<Double> volumeList = new ArrayList<>(volumes);
        List<PriceBar> bars = new ArrayList<>(window.size());

        for (int i = 0; i < window.size(); i++) {
            double close = window.get(i);
            double open = i == 0 ? close : window.get(i - 1);
            double high = i < highList.size() ? Math.max(highList.get(i), Math.max(open, close))
                    : Math.max(open, close);
            double low = i < lowList.size() ? Math.min(lowList.get(i), Math.min(open, close))
                    : Math.min(open, close);
            long volume = i < volumeList.size() ? volumeList.get(i).longValue() : 0L;
            bars.add(new PriceBar(SYNTHETIC_INSTRUMENT, open, high, low, close, volume,
                    Instant.now()));
        }
        return bars;
    }

    /** Placeholder instrument for internally rebuilt bars; the symbol is not used by the maths. */
    private static final com.tradingdesk.model.Instrument SYNTHETIC_INSTRUMENT =
            new com.tradingdesk.model.Instrument("_WINDOW", "Rolling window", 
                    com.tradingdesk.model.InstrumentType.EQUITY);

    @Override
    public void reset() {
        lock.lock();
        try {
            prices.clear();
            imbalances.clear();
            highs.clear();
            lows.clear();
            volumes.clear();
        } finally {
            lock.unlock();
        }
    }

    public int windowSize() {
        return windowSize;
    }

    public int rocPeriod() {
        return rocPeriod;
    }
}
