package com.tradingdesk.marketdata;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live check against Binance's real public API.
 *
 * <p>Opt-in via {@code -Dlive.enabled=true} because it needs outbound network access. It is skipped
 * (not faked) when the network is unavailable, so the normal suite never depends on the internet.
 */
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "live.enabled", matches = "true")
class BinancePublicProviderIntegrationTest {

    @Test
    void connectsToTheRealApi() {
        BinancePublicProvider provider = new BinancePublicProvider();
        try {
            boolean connected = provider.connect();
            assumeTrue(connected, "Binance not reachable from this machine");
            assertTrue(provider.isConnected());
            assertEquals(ProviderKind.LIVE, provider.kind());
            assertTrue(provider.status().requestsServed() > 0, "a request should have been served");
        } finally {
            provider.disconnect();
        }
    }

    @Test
    void fetchesRealCandles() {
        BinancePublicProvider provider = new BinancePublicProvider();
        try {
            assumeTrue(provider.connect(), "Binance not reachable from this machine");

            List<Candle> candles = provider.history("BTCUSDT", "1h", 5);

            assertFalse(candles.isEmpty(), "real klines should be returned");
            assertTrue(candles.size() <= 5);
            for (Candle candle : candles) {
                assertEquals("BTCUSDT", candle.symbol());
                assertTrue(candle.high() >= candle.low());
                assertTrue(candle.open() > 0 && candle.close() > 0);
                assertTrue(candle.volume() >= 0);
            }
            // Real BTC has traded above $1000 for years; a tiny price would indicate bad parsing.
            assertTrue(candles.get(0).close() > 1_000.0,
                    "unexpected price, got " + candles.get(0).close());
        } finally {
            provider.disconnect();
        }
    }

    @Test
    void fetchesRealQuote() {
        BinancePublicProvider provider = new BinancePublicProvider();
        try {
            assumeTrue(provider.connect(), "Binance not reachable from this machine");

            var quote = provider.latest("BTCUSDT");

            assertTrue(quote.isPresent(), "a real quote should be returned");
            Quote real = quote.get();
            assertTrue(real.hasBothSides());
            assertTrue(real.ask() >= real.bid(), "book must not be crossed");
            assertEquals(ProviderKind.LIVE, real.kind());
        } finally {
            provider.disconnect();
        }
    }

    @Test
    void unknownSymbolIsHandledGracefully() {
        BinancePublicProvider provider = new BinancePublicProvider();
        try {
            assumeTrue(provider.connect(), "Binance not reachable from this machine");

            // The API returns an error object; the provider must return empty, not throw.
            assertTrue(provider.latest("NOTAREALSYMBOL").isEmpty());
            assertTrue(provider.history("NOTAREALSYMBOL", "1h", 5).isEmpty());
        } finally {
            provider.disconnect();
        }
    }
}
