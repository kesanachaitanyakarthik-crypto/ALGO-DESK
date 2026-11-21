package com.tradingdesk.marketdata.ws;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Streams Binance public market data over WebSocket.
 *
 * <p>Uses the JDK's built-in {@link WebSocket} — no external dependency. Implements the reliability
 * behaviour the presentation calls for:
 * <ul>
 *   <li>automatic reconnection with exponential backoff ({@link ReconnectPolicy});</li>
 *   <li>a heartbeat watchdog that reconnects if the stream goes silent;</li>
 *   <li>duplicate suppression ({@link DuplicateFilter});</li>
 *   <li>exchange event timestamps, never local clocks, for ordering;</li>
 *   <li>a disconnect callback so the supervisor can fall back to REST.</li>
 * </ul>
 *
 * <p>Everything here is read-only public data: no API key, no authentication, no order placement.
 */
public class BinanceWebSocketClient implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(BinanceWebSocketClient.class.getName());
    private static final String DEFAULT_WS_BASE = "wss://stream.binance.com:9443/stream?streams=";

    private final String baseUrl;
    private final List<String> symbols;
    private final Consumer<StreamMessage> onMessage;
    private final Runnable onDisconnect;
    private final ReconnectPolicy policy = new ReconnectPolicy();
    private final DuplicateFilter duplicates = new DuplicateFilter();
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong lastMessageAt = new AtomicLong(0);
    private final AtomicLong messagesReceived = new AtomicLong();
    private final AtomicLong reconnects = new AtomicLong();

    private volatile WebSocket socket;
    private volatile String lastError;

    public BinanceWebSocketClient(List<String> symbols,
                                  Consumer<StreamMessage> onMessage,
                                  Runnable onDisconnect) {
        this(DEFAULT_WS_BASE, symbols, onMessage, onDisconnect);
    }

    public BinanceWebSocketClient(String baseUrl, List<String> symbols,
                                  Consumer<StreamMessage> onMessage, Runnable onDisconnect) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("at least one symbol is required");
        }
        this.symbols = List.copyOf(symbols);
        this.onMessage = Objects.requireNonNull(onMessage, "onMessage");
        this.onDisconnect = onDisconnect == null ? () -> { } : onDisconnect;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "td-ws-watchdog");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Builds the combined-stream URI, e.g. {@code ...?streams=btcusdt@trade/btcusdt@bookTicker}. */
    String buildUri() {
        StringBuilder uri = new StringBuilder(baseUrl);
        for (int i = 0; i < symbols.size(); i++) {
            String symbol = symbols.get(i).toLowerCase(java.util.Locale.ROOT);
            if (i > 0) {
                uri.append('/');
            }
            uri.append(symbol).append("@trade/").append(symbol).append("@bookTicker");
        }
        return uri.toString();
    }

    /** Opens the stream. Returns true when the socket was established. */
    public boolean start() {
        if (!running.compareAndSet(false, true)) {
            return true;
        }
        boolean connected = connect();
        scheduler.scheduleAtFixedRate(this::watchdog, 15, 15, TimeUnit.SECONDS);
        return connected;
    }

    private boolean connect() {
        try {
            socket = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(buildUri()), new Listener())
                    .get(15, TimeUnit.SECONDS);
            policy.recordSuccess();
            lastMessageAt.set(System.currentTimeMillis());
            lastError = null;
            LOGGER.info("WebSocket connected: " + symbols.size() + " symbols");
            return true;
        } catch (Exception e) {
            policy.recordFailure();
            lastError = rootMessage(e);
            LOGGER.log(Level.WARNING, "WebSocket connect failed: " + lastError);
            return false;
        }
    }

    private void scheduleReconnect() {
        if (!running.get()) {
            return;
        }
        long delay = policy.nextDelayMillis();
        policy.recordFailure();
        reconnects.incrementAndGet();
        LOGGER.info("Scheduling WebSocket reconnect in " + delay + " ms");
        scheduler.schedule(this::reconnect, delay, TimeUnit.MILLISECONDS);
    }

    private void reconnect() {
        if (!running.get()) {
            return;
        }
        closeQuietly();
        onDisconnect.run();
        connect();
    }

    /** Reconnects when the stream has been silent longer than the policy allows. */
    private void watchdog() {
        if (!running.get()) {
            return;
        }
        long last = lastMessageAt.get();
        if (policy.isStale(last, System.currentTimeMillis())) {
            LOGGER.warning("WebSocket stale for over " + policy.staleTimeoutMillis()
                    + " ms; reconnecting");
            lastError = "stale stream";
            scheduleReconnect();
        }
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        scheduler.shutdownNow();
        closeQuietly();
    }

    private void closeQuietly() {
        WebSocket current = socket;
        socket = null;
        if (current != null) {
            try {
                current.abort();
            } catch (RuntimeException e) {
                LOGGER.log(Level.FINE, "Socket abort failed", e);
            }
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public long messagesReceived() {
        return messagesReceived.get();
    }

    public long duplicatesSuppressed() {
        return duplicates.suppressedCount();
    }

    public long reconnects() {
        return reconnects.get();
    }

    public Instant lastMessageTime() {
        long millis = lastMessageAt.get();
        return millis == 0 ? null : Instant.ofEpochMilli(millis);
    }

    public String lastError() {
        return lastError;
    }

    ReconnectPolicy policy() {
        return policy;
    }

    @Override
    public void close() {
        stop();
    }

    private static String rootMessage(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    /** Receives frames and dispatches parsed messages. */
    private final class Listener implements WebSocket.Listener {

        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String frame = buffer.toString();
                buffer.setLength(0);
                handleFrame(frame);
            }
            webSocket.request(1);
            return null;
        }

        private void handleFrame(String frame) {
            try {
                for (StreamMessage message : StreamMessageParser.parse(frame)) {
                    if (!duplicates.firstOccurrence(message.dedupeKey(),
                            message.eventTime().toEpochMilli())) {
                        continue;
                    }
                    lastMessageAt.set(System.currentTimeMillis());
                    messagesReceived.incrementAndGet();
                    onMessage.accept(message);
                }
            } catch (RuntimeException e) {
                // A malformed frame must not kill the stream.
                LOGGER.log(Level.FINE, "Discarding malformed frame: " + e.getMessage());
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            lastError = "closed: " + statusCode + " " + reason;
            LOGGER.warning("WebSocket closed (" + statusCode + "): " + reason);
            if (running.get()) {
                scheduleReconnect();
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            lastError = rootMessage(error);
            LOGGER.log(Level.WARNING, "WebSocket error: " + lastError);
            if (running.get()) {
                scheduleReconnect();
            }
        }
    }
}
