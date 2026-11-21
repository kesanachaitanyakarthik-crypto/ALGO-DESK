package com.tradingdesk.marketdata.ws;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline tests for the streaming layer: parsing, dedupe and backoff.
 * No network access is required.
 */
class StreamMessageParserTest {

    /** A real combined-stream trade frame shape. */
    private static final String TRADE_FRAME =
            "{\"stream\":\"btcusdt@trade\",\"data\":{\"e\":\"trade\",\"E\":1795251600000,"
                    + "\"s\":\"BTCUSDT\",\"t\":401168,\"p\":\"57891.77000000\","
                    + "\"q\":\"0.01500000\",\"T\":1795251600000}}";

    private static final String BOOK_FRAME =
            "{\"stream\":\"btcusdt@bookTicker\",\"data\":{\"u\":12345,\"s\":\"BTCUSDT\","
                    + "\"b\":\"57891.77000000\",\"B\":\"5.44499000\","
                    + "\"a\":\"57891.78000000\",\"A\":\"0.51914000\"}}";

    @Test
    void parsesCombinedTradeFrame() {
        List<StreamMessage> messages = StreamMessageParser.parse(TRADE_FRAME);

        assertEquals(1, messages.size());
        StreamMessage trade = messages.get(0);
        assertEquals("BTCUSDT", trade.symbol());
        assertEquals(StreamMessage.Kind.TRADE, trade.kind());
        assertEquals(57891.77, trade.price(), 1e-6);
        assertEquals(0.015, trade.quantity(), 1e-9);
        assertEquals(401168L, trade.tradeId());
        assertEquals(1795251600000L, trade.eventTime().toEpochMilli());
    }

    @Test
    void parsesCombinedBookTickerFrame() {
        List<StreamMessage> messages = StreamMessageParser.parse(BOOK_FRAME);

        assertEquals(1, messages.size());
        StreamMessage book = messages.get(0);
        assertEquals(StreamMessage.Kind.BOOK_TICKER, book.kind());
        assertEquals(57891.77, book.bid(), 1e-6);
        assertEquals(57891.78, book.ask(), 1e-6);
        assertEquals(0.01, book.spread(), 1e-6);
        assertTrue(book.mid() > book.bid() && book.mid() < book.ask());
    }

    @Test
    void ignoresSubscriptionAcknowledgements() {
        assertTrue(StreamMessageParser.parse("{\"result\":null,\"id\":1}").isEmpty());
    }

    @Test
    void ignoresUnusedEventTypes() {
        String frame = "{\"stream\":\"x\",\"data\":{\"e\":\"depthUpdate\",\"s\":\"BTCUSDT\"}}";
        assertTrue(StreamMessageParser.parse(frame).isEmpty());
    }

    @Test
    void rejectsMalformedFrames() {
        assertThrows(IllegalArgumentException.class, () -> StreamMessageParser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> StreamMessageParser.parse("not json"));
        assertThrows(IllegalArgumentException.class,
                () -> StreamMessageParser.parse("{\"data\":{\"e\":\"trade\"}}"));
    }

    @Test
    void rejectsMissingNumericFields() {
        String frame = "{\"data\":{\"e\":\"trade\",\"s\":\"BTCUSDT\",\"E\":1,\"t\":1}}";
        assertThrows(IllegalArgumentException.class, () -> StreamMessageParser.parse(frame));
    }

    @Test
    void dedupeKeyDistinguishesTradesAndBookUpdates() {
        StreamMessage trade = StreamMessageParser.parse(TRADE_FRAME).get(0);
        StreamMessage book = StreamMessageParser.parse(BOOK_FRAME).get(0);

        assertFalse(trade.dedupeKey().equals(book.dedupeKey()));
        assertTrue(trade.dedupeKey().contains("trade"));
        assertTrue(book.dedupeKey().contains("book"));
    }

    @Test
    void splitStreamNameLowercasesAndSplits() {
        assertEquals(List.of("btcusdt", "trade"),
                StreamMessageParser.splitStreamName("BTCUSDT@trade"));
    }

    // ------------------------------------------------------------------ duplicate filter

    @Test
    void duplicateFilterSuppressesRepeats() {
        DuplicateFilter filter = new DuplicateFilter(100);

        assertTrue(filter.firstOccurrence("a", 1000));
        assertFalse(filter.firstOccurrence("a", 1001), "the same key must be suppressed");
        assertTrue(filter.firstOccurrence("b", 1002));
        assertEquals(1, filter.suppressedCount());
    }

    @Test
    void duplicateFilterRemainsBounded() {
        DuplicateFilter filter = new DuplicateFilter(50);
        for (int i = 0; i < 5_000; i++) {
            filter.firstOccurrence("key-" + i, System.currentTimeMillis());
        }
        assertTrue(filter.trackedCount() <= 50, "tracked keys must stay bounded");
    }

    // ------------------------------------------------------------------ reconnect policy

    @Test
    void backoffGrowsAndIsCapped() {
        ReconnectPolicy policy = new ReconnectPolicy(1_000, 8_000, 2.0, 60_000);

        assertEquals(1_000, policy.nextDelayMillis());
        policy.recordFailure();
        assertEquals(2_000, policy.nextDelayMillis());
        policy.recordFailure();
        assertEquals(4_000, policy.nextDelayMillis());
        policy.recordFailure();
        assertEquals(8_000, policy.nextDelayMillis());
        policy.recordFailure();
        assertEquals(8_000, policy.nextDelayMillis(), "delay must be capped");
    }

    @Test
    void successResetsBackoff() {
        ReconnectPolicy policy = new ReconnectPolicy();
        policy.recordFailure();
        policy.recordFailure();
        assertEquals(2, policy.consecutiveFailures());

        policy.recordSuccess();
        assertEquals(0, policy.consecutiveFailures());
        assertEquals(1_000, policy.nextDelayMillis());
    }

    @Test
    void stalenessDetectsSilentStream() {
        ReconnectPolicy policy = new ReconnectPolicy(1_000, 30_000, 2.0, 60_000);

        assertFalse(policy.isStale(System.currentTimeMillis(), System.currentTimeMillis()),
                "a fresh message is not stale");
        assertTrue(policy.isStale(1_000, 100_000), "an old message is stale");
        assertFalse(policy.isStale(0, 100_000), "never-received is not treated as stale");
    }

    @Test
    void policyRejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReconnectPolicy(0, 1_000, 2.0, 1_000));
        assertThrows(IllegalArgumentException.class,
                () -> new ReconnectPolicy(1_000, 500, 2.0, 1_000));
        assertThrows(IllegalArgumentException.class,
                () -> new ReconnectPolicy(1_000, 2_000, 0.5, 1_000));
    }

    @Test
    void buildsCombinedStreamUriForAllSymbols() {
        BinanceWebSocketClient client = new BinanceWebSocketClient(
                List.of("BTCUSDT", "ETHUSDT"), message -> { }, () -> { });

        String uri = client.buildUri();

        assertTrue(uri.startsWith("wss://stream.binance.com:9443/stream?streams="));
        assertTrue(uri.contains("btcusdt@trade"));
        assertTrue(uri.contains("btcusdt@bookTicker"));
        assertTrue(uri.contains("ethusdt@trade"));
        client.close();
    }

    @Test
    void clientRejectsEmptySymbolList() {
        assertThrows(IllegalArgumentException.class,
                () -> new BinanceWebSocketClient(List.of(), message -> { }, () -> { }));
    }
}
