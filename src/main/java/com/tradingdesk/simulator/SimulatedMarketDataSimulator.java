package com.tradingdesk.simulator;

import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.MarketEvent;
import com.tradingdesk.model.MarketEventType;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class SimulatedMarketDataSimulator implements MarketDataSimulator {

    private final List<Instrument> instruments;
    private final MarketEventPublisher publisher;
    private final SimulatorConfig config;
    private final double[] lastPrices;
    private final AtomicLong sequence = new AtomicLong();
    private volatile boolean running;

    public SimulatedMarketDataSimulator(List<Instrument> instruments,
                                        double[] startingPrices,
                                        MarketEventPublisher publisher,
                                        SimulatorConfig config) {
        Objects.requireNonNull(instruments, "instruments");
        Objects.requireNonNull(startingPrices, "startingPrices");
        Objects.requireNonNull(publisher, "publisher");
        Objects.requireNonNull(config, "config");
        if (instruments.isEmpty()) {
            throw new IllegalArgumentException("at least one instrument is required");
        }
        if (startingPrices.length != instruments.size()) {
            throw new IllegalArgumentException("a starting price is required for every instrument");
        }
        this.instruments = List.copyOf(instruments);
        this.lastPrices = startingPrices.clone();
        for (double price : this.lastPrices) {
            if (price <= 0.0) {
                throw new IllegalArgumentException("starting prices must be positive");
            }
        }
        this.publisher = publisher;
        this.config = config;
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public MarketEvent nextEvent() {
        int index = ThreadLocalRandom.current().nextInt(instruments.size());
        return nextEventFor(index);
    }

    public MarketEvent nextEventFor(int index) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double previous = lastPrices[index];
        double shock = random.nextGaussian() * config.baseVolatility();
        double next = Math.max(0.01, previous * (1.0 + shock + config.driftBias()));
        lastPrices[index] = next;

        double spreadBasisPoints = random.nextDouble(
                config.minSpreadBasisPoints(), config.maxSpreadBasisPoints());
        double halfSpread = next * spreadBasisPoints / 20_000.0;
        double bid = Math.max(0.01, next - halfSpread);
        double ask = next + halfSpread;
        long volume = random.nextLong(config.minVolume(), config.maxVolume() + 1);

        return new MarketEvent(sequence.incrementAndGet(), instruments.get(index),
                MarketEventType.QUOTE, round(next), round(bid), round(ask), volume, Instant.now());
    }

    @Override
    public MarketEvent emit() {
        MarketEvent event = nextEvent();
        publisher.publish(event);
        return event;
    }

    public double lastPrice(int index) {
        return lastPrices[index];
    }

    public List<Instrument> instruments() {
        return instruments;
    }

    public SimulatorConfig config() {
        return config;
    }

    private static double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    @Override
    public void close() {
        stop();
    }
}
