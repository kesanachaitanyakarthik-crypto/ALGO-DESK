package com.tradingdesk.simulator;

import com.tradingdesk.concurrency.MarketEventQueue;
import com.tradingdesk.concurrency.MetricsRegistry;
import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.InstrumentType;
import com.tradingdesk.model.MarketEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatedMarketDataSimulatorTest {

    private static final List<Instrument> WATCHLIST = List.of(
            new Instrument("AAPL", "Apple Inc.", InstrumentType.EQUITY),
            new Instrument("MSFT", "Microsoft Corp.", InstrumentType.EQUITY),
            new Instrument("GOOG", "Alphabet Inc.", InstrumentType.EQUITY),
            new Instrument("AMZN", "Amazon.com Inc.", InstrumentType.EQUITY),
            new Instrument("NVDA", "NVIDIA Corp.", InstrumentType.EQUITY));

    private static final double[] PRICES = {189.45, 421.10, 158.22, 183.75, 121.60};

    private static SimulatedMarketDataSimulator simulator(List<MarketEvent> sink) {
        return new SimulatedMarketDataSimulator(WATCHLIST, PRICES, sink::add, SimulatorConfig.defaults());
    }

    @Test
    void producesWellFormedEventsForEverySymbol() {
        List<MarketEvent> events = new ArrayList<>();
        SimulatedMarketDataSimulator simulator = simulator(events);

        for (int i = 0; i < 500; i++) {
            MarketEvent event = simulator.nextEvent();
            assertTrue(event.lastPrice() > 0);
            assertTrue(event.bidPrice() <= event.askPrice(),
                    "bid must never exceed ask");
            assertTrue(event.spread() >= 0);
            assertTrue(event.volume() > 0);
            events.add(event);
        }

        assertEquals(500, events.size());
        long distinctSymbols = events.stream().map(e -> e.instrument().symbol()).distinct().count();
        assertTrue(distinctSymbols >= 4, "events should spread across most symbols");
    }

    @Test
    void sequenceNumbersAreStrictlyIncreasing() {
        SimulatedMarketDataSimulator simulator = simulator(new ArrayList<>());
        long previous = 0;
        for (int i = 0; i < 200; i++) {
            long sequence = simulator.nextEvent().sequence();
            assertTrue(sequence > previous);
            previous = sequence;
        }
    }

    @Test
    void startStopTogglesRunningState() {
        SimulatedMarketDataSimulator simulator = simulator(new ArrayList<>());
        assertFalse(simulator.isRunning());
        simulator.start();
        assertTrue(simulator.isRunning());
        simulator.stop();
        assertFalse(simulator.isRunning());
    }

    @Test
    void emitPublishesToSink() {
        List<MarketEvent> events = new ArrayList<>();
        SimulatedMarketDataSimulator simulator = simulator(events);
        simulator.emit();
        assertEquals(1, events.size());
    }

    @Test
    void runnerCountsDropsWhenQueueIsFull() throws InterruptedException {
        MarketEventQueue<MarketEvent> queue = new MarketEventQueue<>(2);
        MetricsRegistry metrics = new MetricsRegistry();
        SimulatedMarketDataSimulator simulator = simulator(new ArrayList<>());
        SimulationRunner runner = new SimulationRunner(simulator, queue, metrics);
        try {
            for (int i = 0; i < 10; i++) {
                runner.publish(simulator.nextEvent());
            }
            assertEquals(2, runner.published());
            assertEquals(8, runner.dropped());
            assertEquals(8, metrics.eventsDropped());
            assertEquals(2, queue.depth());
        } finally {
            runner.close();
        }
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new SimulatedMarketDataSimulator(WATCHLIST, new double[]{1.0},
                        event -> { }, SimulatorConfig.defaults()));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulatedMarketDataSimulator(List.of(), new double[]{},
                        event -> { }, SimulatorConfig.defaults()));
    }
}
