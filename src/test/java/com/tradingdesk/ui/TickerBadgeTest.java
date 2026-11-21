package com.tradingdesk.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Ticker badge rules. {@link TickerBadge#colorFor} and {@link TickerBadge#monogram} are pure
 * and safe to test without starting the JavaFX toolkit.
 */
class TickerBadgeTest {

    @Test
    void monogramUsesFirstLetterOfTicker() {
        assertEquals("A", TickerBadge.monogram("AAPL"));
        assertEquals("M", TickerBadge.monogram("MSFT"));
        assertEquals("N", TickerBadge.monogram("NVDA"));
    }

    @Test
    void monogramStripsPairSuffix() {
        assertEquals("B", TickerBadge.monogram("BTC-USD"));
    }

    @Test
    void monogramHandlesMissingSymbol() {
        assertEquals("?", TickerBadge.monogram(null));
        assertEquals("?", TickerBadge.monogram("  "));
    }

    @Test
    void colourIsDeterministicForSameSymbol() {
        assertEquals(TickerBadge.colorFor("AAPL"), TickerBadge.colorFor("AAPL"));
        assertEquals(TickerBadge.colorFor("aapl"), TickerBadge.colorFor("AAPL"));
    }

    @Test
    void colourIsStableAcrossCallsAndDistinguishesSomeSymbols() {
        List<String> symbols = List.of("AAPL", "MSFT", "GOOG", "AMZN", "NVDA");
        long distinct = symbols.stream().map(TickerBadge::colorFor).distinct().count();
        assertNotEquals(1, distinct, "symbols should not all collapse onto one colour");
    }

    @Test
    void colourNeverNullForUnknownInput() {
        assertNotNull(TickerBadge.colorFor(null));
        assertNotNull(TickerBadge.colorFor(""));
    }
}
