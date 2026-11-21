package com.tradingdesk.marketdata;

import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.MarketEvent;
import com.tradingdesk.simulator.SimulatedMarketDataSimulator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Exposes the existing in-house simulator through the market data contract.
 *
 * <p>Deliberately reports {@link ProviderKind#SIMULATED} so its data can never be presented as
 * live. Reuses {@link SimulatedMarketDataSimulator} rather than duplicating price generation.
 */
public class SimulatedMarketDataProvider implements MarketDataProvider {

    private final SimulatedMarketDataSimulator simulator;
    private final ConcurrentMap<String, Quote> latestQuotes = new ConcurrentHashMap<>();
    private final List<Candle> syntheticHistory = new ArrayList<>();
    private final AtomicLong ticks = new AtomicLong();
    private volatile boolean connected;
    private volatile Instant lastUpdate;

    public SimulatedMarketDataProvider(SimulatedMarketDataSimulator simulator) {
        this.simulator = simulator;
    }

    @Override
    public String name() {
        return "ALGO DESK Simulator";
    }

    @Override
    public ProviderKind kind() {
        return ProviderKind.SIMULATED;
    }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(true, true, true, false);
    }
    @Override
    public boolean connect() {
        simulator.start();
        connected = true;
        return true;
    }

    @Override
    public void disconnect() {
        simulator.stop();
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public MarketDataStatus status() {
        return new MarketDataStatus(name(), kind(), connected, lastUpdate,
                connected ? "Streaming simulated ticks" : "Stopped", ticks.get());
    }

    /** Records a simulated tick so {@link #latest(String)} can answer from real application data. */
    public void onMarketEvent(MarketEvent event) {
        String symbol = event.instrument().symbol();
        latestQuotes.put(symbol, new Quote(symbol, event.bidPrice(), event.askPrice(),
                event.lastPrice(), event.volume(), event.volume(),
                event.timestamp(), ProviderKind.SIMULATED, name()));
        lastUpdate = event.timestamp();
        ticks.incrementAndGet();
    }

    /** Synthesises candles from the simulator so backtests can run without external data. */
    public List<Candle> history(String symbol, String interval, int limit) {
        synchronized (syntheticHistory) {
            if (syntheticHistory.isEmpty()) {
                buildSyntheticHistory();
            }
            List<Candle> filtered = new ArrayList<>();
            for (Candle candle : syntheticHistory) {
                if (candle.symbol().equals(symbol)) {
                    filtered.add(candle);
                }
            }
            int from = Math.max(0, filtered.size() - limit);
            return List.copyOf(filtered.subList(from, filtered.size()));
        }
    }

    private void buildSyntheticHistory() {
        Map<String, Double> lastPrices = new ConcurrentHashMap<>();
        for (Instrument instrument : simulator.instruments()) {
            lastPrices.put(instrument.symbol(), 100.0);
        }
        Instant start = Instant.now().minusSeconds(3_600L * 60);
        for (int i = 0; i < 60; i++) {
            for (Map.Entry<String, Double> entry : lastPrices.entrySet()) {
                double open = entry.getValue();
                double close = open * (1.0 + Math.sin(i + entry.getKey().hashCode() % 7) * 0.01);
                double high = Math.max(open, close) * 1.004;
                double low = Math.min(open, close) * 0.996;
                syntheticHistory.add(new Candle(entry.getKey(), start.plusSeconds(3_600L * i),
                        open, high, low, close, 1_000.0));
                lastPrices.put(entry.getKey(), close);
            }
        }
    }

    @Override
    public Optional<Quote> latest(String symbol) {
        return Optional.ofNullable(latestQuotes.get(symbol));
    }
}
