package com.tradingdesk.marketdata;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Market data from Binance's <strong>public</strong> REST endpoints.
 *
 * <p>Uses only unauthenticated public endpoints — {@code /api/v3/klines} and
 * {@code /api/v3/ticker/bookTicker} — so no API key is involved and no permissions are required.
 * This is real external market data, hence {@link ProviderKind#LIVE} for quotes and
 * {@link ProviderKind#HISTORICAL} for candles.
 *
 * <p>Reliability behaviour: a minimum interval between calls provides simple rate-limit protection,
 * failed requests leave the provider marked disconnected with the reason recorded, and responses are
 * validated by {@link BinanceResponseParser} before being trusted.
 */
public class BinancePublicProvider implements MarketDataProvider {

    private static final Logger LOGGER = Logger.getLogger(BinancePublicProvider.class.getName());
    private static final String DEFAULT_BASE_URL = "https://api.binance.com";
    private static final long MIN_REQUEST_INTERVAL_MILLIS = 250L;

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ConcurrentMap<String, List<Candle>> historyCache = new ConcurrentHashMap<>();
    private final AtomicLong requestsServed = new AtomicLong();
    private final AtomicLong lastRequestAt = new AtomicLong(0);

    private volatile boolean connected;
    private volatile Instant lastUpdate;
    private volatile String statusDetail = "Not connected";

    public BinancePublicProvider() {
        this(DEFAULT_BASE_URL);
    }

    public BinancePublicProvider(String baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String name() {
        return "Binance (public REST)";
    }

    @Override
    public ProviderKind kind() {
        return ProviderKind.LIVE;
    }

    @Override
    public Capabilities capabilities() {
        return Capabilities.restWithHistory();
    }

    @Override
    public boolean connect() {
        try {
            // /api/v3/time is the cheapest endpoint to confirm reachability.
            get("/api/v3/time");
            connected = true;
            statusDetail = "Reachable";
            return true;
        } catch (RuntimeException e) {
            connected = false;
            statusDetail = "Unreachable: " + rootMessage(e);
            LOGGER.log(Level.WARNING, "Binance connect failed: " + statusDetail);
            return false;
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        statusDetail = "Disconnected";
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public MarketDataStatus status() {
        return new MarketDataStatus(name(), kind(), connected, lastUpdate, statusDetail,
                requestsServed.get());
    }

    @Override
    public Optional<Quote> latest(String symbol) {
        Objects.requireNonNull(symbol, "symbol");
        try {
            String body = get("/api/v3/ticker/bookTicker?symbol=" + encode(symbol));
            double bid = BinanceResponseParser.parseNumericField(body, "bidPrice");
            double ask = BinanceResponseParser.parseNumericField(body, "askPrice");
            Instant now = Instant.now();
            Quote quote = BinanceResponseParser.parseBookTicker(symbol, body, (bid + ask) / 2.0, now);

            lastUpdate = now;
            connected = true;
            statusDetail = "OK";
            return Optional.of(quote);
        } catch (RuntimeException e) {
            connected = false;
            statusDetail = "Quote failed: " + rootMessage(e);
            LOGGER.log(Level.FINE, "Binance quote failed for " + symbol, e);
            return Optional.empty();
        }
    }

    @Override
    public List<Candle> history(String symbol, String interval, int limit) {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(interval, "interval");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        String cacheKey = symbol + "|" + interval + "|" + limit;
        List<Candle> cached = historyCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            String body = get("/api/v3/klines?symbol=" + encode(symbol)
                    + "&interval=" + encode(interval) + "&limit=" + limit);
            List<Candle> candles = BinanceResponseParser.parseKlines(symbol, body);
            historyCache.put(cacheKey, candles);
            lastUpdate = Instant.now();
            connected = true;
            statusDetail = "OK";
            return candles;
        } catch (RuntimeException e) {
            connected = false;
            statusDetail = "History failed: " + rootMessage(e);
            LOGGER.log(Level.FINE, "Binance history failed for " + symbol, e);
            return List.of();
        }
    }

    /** Clears the cached history, for example to force a refresh. */
    public void clearCache() {
        historyCache.clear();
    }

    /** Performs an HTTP GET, enforcing a minimum interval between requests. */
    private String get(String path) {
        throttle();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }
            requestsServed.incrementAndGet();
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("network error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }

    private void throttle() {
        long now = System.currentTimeMillis();
        long previous = lastRequestAt.getAndSet(now);
        long elapsed = now - previous;
        if (previous != 0 && elapsed < MIN_REQUEST_INTERVAL_MILLIS) {
            try {
                Thread.sleep(MIN_REQUEST_INTERVAL_MILLIS - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String rootMessage(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
