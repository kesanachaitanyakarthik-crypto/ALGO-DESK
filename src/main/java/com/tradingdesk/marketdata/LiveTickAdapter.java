package com.tradingdesk.marketdata;

import com.tradingdesk.marketdata.ws.StreamMessage;
import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.InstrumentType;
import com.tradingdesk.model.MarketEvent;
import com.tradingdesk.model.MarketEventType;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adapts real streaming messages into the application's {@link MarketEvent} shape.
 *
 * <p>Real ticks are published into the <strong>same bounded queue</strong> the simulator uses, so the
 * event-processing architecture (queue → ExecutorService → workers → pipeline) is identical whether
 * the data is live or synthetic. That is a core requirement: real data must not bypass the
 * concurrency pipeline.
 *
 * <p>Binance sends trades and book updates on separate streams. This adapter merges them per symbol
 * so a {@code MarketEvent} always carries a coherent last/bid/ask triple, and it keeps the exchange
 * timestamp rather than stamping the local clock.
 */
public class LiveTickAdapter {

    private final Map<String, Instrument> instrumentsByStreamSymbol;
    private final ConcurrentMap<String, Double> lastPrice = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, double[]> quotes = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public LiveTickAdapter(Map<String, Instrument> instrumentsByStreamSymbol) {
        this.instrumentsByStreamSymbol = Map.copyOf(instrumentsByStreamSymbol);
    }

    /** Builds the default Binance→Instrument mapping used by the application. */
    public static Map<String, Instrument> defaultMapping() {
        return Map.of(
                "BTCUSDT", new Instrument("BTCUSDT", "Bitcoin / USDT", InstrumentType.CRYPTO),
                "ETHUSDT", new Instrument("ETHUSDT", "Ethereum / USDT", InstrumentType.CRYPTO),
                "BNBUSDT", new Instrument("BNBUSDT", "BNB / USDT", InstrumentType.CRYPTO),
                "SOLUSDT", new Instrument("SOLUSDT", "Solana / USDT", InstrumentType.CRYPTO),
                "XRPUSDT", new Instrument("XRPUSDT", "XRP / USDT", InstrumentType.CRYPTO));
    }

    /**
     * Converts one stream message into a market event.
     *
     * @return the event, or null when the message cannot yet form a complete quote for its symbol
     */
    public MarketEvent adapt(StreamMessage message) {
        if (message == null) {
            return null;
        }
        Instrument instrument = instrumentsByStreamSymbol.get(message.symbol());
        if (instrument == null) {
            return null;
        }
        String symbol = message.symbol();

        if (message.kind() == StreamMessage.Kind.TRADE) {
            lastPrice.put(symbol, message.price());
        } else if (message.bid() > 0.0 && message.ask() > 0.0) {
            quotes.put(symbol, new double[]{message.bid(), message.ask()});
            lastPrice.putIfAbsent(symbol, message.mid());
        }

        Double last = lastPrice.get(symbol);
        double[] book = quotes.get(symbol);
        if (last == null) {
            return null;
        }
        // If no book update has arrived yet, derive a plausible one around the trade price so the
        // event is still internally consistent (bid <= ask).
        double bid = book == null ? last * 0.9999 : book[0];
        double ask = book == null ? last * 1.0001 : book[1];
        if (bid > ask) {
            double swap = bid;
            bid = ask;
            ask = swap;
        }

        return new MarketEvent(
                sequence.incrementAndGet(),
                instrument,
                MarketEventType.TRADE,
                last,
                bid,
                ask,
                1L,
                message.eventTime() == null ? Instant.now() : message.eventTime());
    }

    public long sequence() {
        return sequence.get();
    }
}
