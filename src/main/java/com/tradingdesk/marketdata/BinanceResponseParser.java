package com.tradingdesk.marketdata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Binance public REST payloads into domain types.
 *
 * <p>Kept separate from the HTTP client so the parsing rules — including rejection of malformed or
 * inconsistent data — can be tested exhaustively without touching the network.
 *
 * <p>Kline array format, per the Binance API docs:
 * {@code [openTime, open, high, low, close, volume, closeTime, quoteVolume, trades,
 * takerBase, takerQuote, ignore]}.
 */
public final class BinanceResponseParser {

    private BinanceResponseParser() {
    }

    /**
     * Parses a {@code /api/v3/klines} array.
     *
     * @throws IllegalArgumentException when the payload is malformed or a row is invalid
     */
    public static List<Candle> parseKlines(String symbol, String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("empty klines payload");
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("[")) {
            throw new IllegalArgumentException("klines payload must be a JSON array");
        }
        String body = trimmed.substring(1, trimmed.lastIndexOf(']'));

        List<Candle> candles = new ArrayList<>();
        for (String row : splitTopLevel(body)) {
            String entry = row.trim();
            if (entry.isEmpty()) {
                continue;
            }
            candles.add(parseKlineRow(symbol, entry));
        }
        return List.copyOf(candles);
    }

    private static Candle parseKlineRow(String symbol, String row) {
        String trimmed = row.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            throw new IllegalArgumentException("kline row must be an array: " + trimmed);
        }
        List<String> fields = splitTopLevel(trimmed.substring(1, trimmed.length() - 1));
        if (fields.size() < 6) {
            throw new IllegalArgumentException("kline row needs at least 6 fields: " + trimmed);
        }
        long openTimeMillis = Long.parseLong(unquote(fields.get(0)));
        return new Candle(
                symbol,
                Instant.ofEpochMilli(openTimeMillis),
                Double.parseDouble(unquote(fields.get(1))),
                Double.parseDouble(unquote(fields.get(2))),
                Double.parseDouble(unquote(fields.get(3))),
                Double.parseDouble(unquote(fields.get(4))),
                Double.parseDouble(unquote(fields.get(5))));
    }

    /**
     * Parses a {@code /api/v3/ticker/bookTicker} object into a quote.
     *
     * @param lastPrice last traded price if known; the ticker endpoint does not include it, so the
     *                  caller supplies it from elsewhere (usually the mid price)
     */
    public static Quote parseBookTicker(String symbol, String json, double lastPrice, Instant at) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("empty bookTicker payload");
        }
        double bid = parseNumericField(json, "bidPrice");
        double ask = parseNumericField(json, "askPrice");
        double bidSize = parseNumericField(json, "bidQty");
        double askSize = parseNumericField(json, "askQty");

        return new Quote(symbol, bid, ask, lastPrice, bidSize, askSize,
                at, ProviderKind.LIVE, "binance");
    }

    /** Reads a numeric JSON field, tolerating surrounding quotes. */
    static double parseNumericField(String json, String field) {
        String marker = "\"" + field + "\"";
        int index = json.indexOf(marker);
        if (index < 0) {
            throw new IllegalArgumentException("missing field: " + field);
        }
        int colon = json.indexOf(':', index + marker.length());
        if (colon < 0) {
            throw new IllegalArgumentException("malformed field: " + field);
        }
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') {
            end++;
        }
        String raw = json.substring(start, end).trim();
        return Double.parseDouble(unquote(raw));
    }

    /** Splits a JSON array/object body on commas that are not inside nested brackets or strings. */
    static List<String> splitTopLevel(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"') {
                inString = !inString;
            }
            if (!inString) {
                if (c == '[' || c == '{') {
                    depth++;
                } else if (c == ']' || c == '}') {
                    depth--;
                }
            }
            if (c == ',' && depth == 0 && !inString) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
