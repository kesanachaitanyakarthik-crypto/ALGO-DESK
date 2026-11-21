package com.tradingdesk.marketdata.ws;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses Binance combined-stream WebSocket payloads without any external JSON library.
 *
 * <p>Combined streams wrap each event: {@code {"stream":"btcusdt@trade","data":{...}}}. The parser
 * extracts only the fields this application needs and rejects malformed or inconsistent messages
 * rather than passing bad data into the pipeline.
 *
 * <p>Kept separate from the socket so every parsing rule is unit-testable offline.
 */
public final class StreamMessageParser {

    private StreamMessageParser() {
    }

    /**
     * Parses one WebSocket text frame into zero or more messages.
     *
     * @return parsed messages; empty when the frame is a control/acknowledgement payload
     * @throws IllegalArgumentException when the frame is malformed
     */
    public static List<StreamMessage> parse(String frame) {
        if (frame == null || frame.isBlank()) {
            throw new IllegalArgumentException("empty frame");
        }
        String trimmed = frame.trim();

        // Subscription acknowledgements look like {"result":null,"id":1} and carry no market data.
        if (trimmed.startsWith("{") && trimmed.contains("\"result\"")) {
            return List.of();
        }

        String payload = trimmed;
        if (trimmed.contains("\"data\"")) {
            // Combined stream: unwrap the inner data object.
            int dataIndex = trimmed.indexOf("\"data\"");
            int brace = trimmed.indexOf('{', dataIndex);
            if (brace < 0) {
                throw new IllegalArgumentException("combined stream without a data object");
            }
            payload = extractObject(trimmed, brace);
        }

        String eventType = readString(payload, "e");
        if (eventType == null) {
            // bookTicker messages have no "e" field; detect them by their shape.
            if (payload.contains("\"b\"") && payload.contains("\"a\"") && payload.contains("\"s\"")) {
                return List.of(parseBookTicker(payload));
            }
            throw new IllegalArgumentException("unrecognised stream payload");
        }

        if ("trade".equals(eventType) || "aggTrade".equals(eventType)) {
            return List.of(parseTrade(payload));
        }
        if ("24hrTicker".equals(eventType)) {
            return List.of(parseBookTicker(payload));
        }
        // Other event types are not used by this application.
        return List.of();
    }

    private static StreamMessage parseTrade(String payload) {
        String symbol = requireString(payload, "s");
        long eventTime = requireLong(payload, "E");
        long tradeId = readLong(payload, "t", 0L);
        double price = requireDouble(payload, "p");
        double quantity = requireDouble(payload, "q");

        return new StreamMessage(symbol, java.time.Instant.ofEpochMilli(eventTime),
                StreamMessage.Kind.TRADE, price, 0.0, 0.0, quantity, tradeId);
    }

    private static StreamMessage parseBookTicker(String payload) {
        String symbol = requireString(payload, "s");
        double bid = requireDouble(payload, "b");
        double ask = requireDouble(payload, "a");
        // bookTicker has no event time; the "E" field is present on some variants.
        long eventTime = readLong(payload, "E", System.currentTimeMillis());

        return new StreamMessage(symbol, java.time.Instant.ofEpochMilli(eventTime),
                StreamMessage.Kind.BOOK_TICKER, (bid + ask) / 2.0, bid, ask, 0.0, 0L);
    }

    // ------------------------------------------------------------------ minimal JSON helpers

    /** Extracts a balanced {@code {...}} object starting at {@code start}. */
    static String extractObject(String json, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(start, i + 1);
                }
            }
        }
        throw new IllegalArgumentException("unterminated JSON object");
    }

    private static String readRaw(String json, String key) {
        String marker = "\"" + key + "\"";
        int index = json.indexOf(marker);
        if (index < 0) {
            return null;
        }
        int colon = json.indexOf(':', index + marker.length());
        if (colon < 0) {
            return null;
        }
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length()) {
            return null;
        }
        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            return end < 0 ? null : json.substring(start, end + 1);
        }
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') {
            end++;
        }
        return json.substring(start, end).trim();
    }

    static String readString(String json, String key) {
        String raw = readRaw(json, key);
        if (raw == null) {
            return null;
        }
        return raw.length() >= 2 && raw.startsWith("\"") ? raw.substring(1, raw.length() - 1) : raw;
    }

    private static String requireString(String json, String key) {
        String value = readString(json, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing field: " + key);
        }
        return value;
    }

    private static double requireDouble(String json, String key) {
        String raw = readRaw(json, key);
        if (raw == null) {
            throw new IllegalArgumentException("missing field: " + key);
        }
        String value = raw.startsWith("\"") ? raw.substring(1, raw.length() - 1) : raw;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("field " + key + " is not numeric: " + raw, e);
        }
    }

    private static long readLong(String json, String key, long fallback) {
        String raw = readRaw(json, key);
        if (raw == null) {
            return fallback;
        }
        String value = raw.startsWith("\"") ? raw.substring(1, raw.length() - 1) : raw;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long requireLong(String json, String key) {
        String raw = readRaw(json, key);
        if (raw == null) {
            throw new IllegalArgumentException("missing field: " + key);
        }
        try {
            return Long.parseLong(raw.startsWith("\"") ? raw.substring(1, raw.length() - 1) : raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("field " + key + " is not an integer", e);
        }
    }

    /** Splits a stream name such as {@code btcusdt@trade} into its parts. */
    public static List<String> splitStreamName(String streamName) {
        List<String> parts = new ArrayList<>();
        if (streamName == null) {
            return parts;
        }
        for (String part : streamName.split("@")) {
            if (!part.isBlank()) {
                parts.add(part.toLowerCase(java.util.Locale.ROOT));
            }
        }
        return parts;
    }
}
