package com.tradingdesk.marketdata;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parsing rules for Binance public payloads, using the exact shapes returned by the live API.
 * No network access is required.
 */
class BinanceResponseParserTest {

    /** A real two-row response captured from /api/v3/klines?symbol=BTCUSDT&interval=1h&limit=2. */
    private static final String KLINES = """
            [[1795248000000,"57997.00000000","58142.00000000","57381.45000000","57805.52000000",\
            "2176.42063000",1795251599999,"125783733.09437080",401168,"859.77337000",\
            "49685571.78018700","0"],[1795251600000,"57805.51000000","58059.55000000",\
            "57769.25000000","57891.77000000","322.27389000",1795255199999,"18660981.26262390",\
            67064,"157.77841000","9135752.24457220","0"]]""";

    private static final String BOOK_TICKER =
            "{\"symbol\":\"BTCUSDT\",\"bidPrice\":\"57891.77000000\",\"bidQty\":\"5.44499000\","
                    + "\"askPrice\":\"57891.78000000\",\"askQty\":\"0.51914000\"}";

    @Test
    void parsesKlinesIntoCandles() {
        List<Candle> candles = BinanceResponseParser.parseKlines("BTCUSDT", KLINES);

        assertEquals(2, candles.size());
        Candle first = candles.get(0);
        assertEquals("BTCUSDT", first.symbol());
        assertEquals(57997.0, first.open(), 1e-6);
        assertEquals(58142.0, first.high(), 1e-6);
        assertEquals(57381.45, first.low(), 1e-6);
        assertEquals(57805.52, first.close(), 1e-6);
        assertEquals(2176.42063, first.volume(), 1e-6);
        assertEquals(Instant.ofEpochMilli(1795248000000L), first.openTime());
    }

    @Test
    void parsedCandlesAreChronological() {
        List<Candle> candles = BinanceResponseParser.parseKlines("BTCUSDT", KLINES);
        assertTrue(candles.get(0).openTime().isBefore(candles.get(1).openTime()));
    }

    @Test
    void candleHighIsNeverBelowLow() {
        for (Candle candle : BinanceResponseParser.parseKlines("BTCUSDT", KLINES)) {
            assertTrue(candle.high() >= candle.low());
        }
    }

    @Test
    void parsesBookTickerIntoQuote() {
        Quote quote = BinanceResponseParser.parseBookTicker(
                "BTCUSDT", BOOK_TICKER, 57891.775, Instant.now());

        assertEquals(57891.77, quote.bid(), 1e-6);
        assertEquals(57891.78, quote.ask(), 1e-6);
        assertEquals(5.44499, quote.bidSize(), 1e-6);
        assertEquals(0.51914, quote.askSize(), 1e-6);
        assertEquals(ProviderKind.LIVE, quote.kind(), "Binance data is real market data");
        assertTrue(quote.hasBothSides());
    }

    @Test
    void quoteSpreadsAndExecutablePricesAreCorrect() {
        Quote quote = BinanceResponseParser.parseBookTicker(
                "BTCUSDT", BOOK_TICKER, 57891.775, Instant.now());

        assertEquals(0.01, quote.spread(), 1e-6);
        assertEquals(quote.ask(), quote.executableBuyPrice(), 1e-9);
        assertEquals(quote.bid(), quote.executableSellPrice(), 1e-9);
    }

    @Test
    void rejectsEmptyOrMalformedPayloads() {
        assertThrows(IllegalArgumentException.class,
                () -> BinanceResponseParser.parseKlines("BTCUSDT", ""));
        assertThrows(IllegalArgumentException.class,
                () -> BinanceResponseParser.parseKlines("BTCUSDT", "not json"));
        assertThrows(IllegalArgumentException.class,
                () -> BinanceResponseParser.parseKlines("BTCUSDT", "[[1,2,3]]"));
    }

    @Test
    void rejectsErrorObjectsFromTheApi() {
        // Binance returns an object, not an array, when the symbol is invalid.
        assertThrows(IllegalArgumentException.class,
                () -> BinanceResponseParser.parseKlines("NOPE",
                        "{\"code\":-1121,\"msg\":\"Invalid symbol.\"}"));
    }

    @Test
    void rejectsMissingQuoteFields() {
        assertThrows(IllegalArgumentException.class,
                () -> BinanceResponseParser.parseNumericField("{\"bidPrice\":\"1\"}", "askPrice"));
    }

    @Test
    void rejectsInconsistentCandleData() {
        // high below low must be refused rather than silently accepted
        String bad = "[[1795248000000,\"10\",\"5\",\"20\",\"10\",\"1\"]]";
        assertThrows(IllegalArgumentException.class,
                () -> BinanceResponseParser.parseKlines("X", bad));
    }

    @Test
    void rejectsNonPositivePrices() {
        String bad = "[[1795248000000,\"0\",\"0\",\"0\",\"0\",\"1\"]]";
        assertThrows(IllegalArgumentException.class,
                () -> BinanceResponseParser.parseKlines("X", bad));
    }

    @Test
    void splitsNestedStructuresOnTopLevelCommasOnly() {
        List<String> parts = BinanceResponseParser.splitTopLevel("a,[b,c],d");
        assertEquals(3, parts.size());
        assertEquals("[b,c]", parts.get(1).trim());
    }

    // ------------------------------------------------------------------ honesty

    @Test
    void simulatedKindIsNeverLabelledLive() {
        assertFalse(ProviderKind.SIMULATED.isReal());
        assertTrue(ProviderKind.SIMULATED.displayLabel().toLowerCase().contains("not live"));
        assertTrue(ProviderKind.LIVE.isReal());
        assertEquals("Live", ProviderKind.LIVE.displayLabel());
    }

    @Test
    void delayedAndHistoricalAreRealButDistinct() {
        assertTrue(ProviderKind.DELAYED.isReal());
        assertTrue(ProviderKind.HISTORICAL.isReal());
        assertEquals("Delayed", ProviderKind.DELAYED.displayLabel());
        assertEquals("Historical", ProviderKind.HISTORICAL.displayLabel());
    }

    @Test
    void candleRejectsInvalidRange() {
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class,
                () -> new Candle("X", now, 10, 5, 20, 10, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Candle("X", now, 10, 20, 5, 10, -1));
    }

    @Test
    void quoteRejectsCrossedBook() {
        assertThrows(IllegalArgumentException.class,
                () -> new Quote("X", 100, 99, 100, 1, 1, Instant.now(), ProviderKind.LIVE, "t"));
    }
}
