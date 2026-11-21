package com.tradingdesk.marketdata;

import com.tradingdesk.marketdata.ws.BinanceWebSocketClient;
import com.tradingdesk.marketdata.ws.StreamMessage;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Chooses and supervises the market data source.
 *
 * <p>Degradation order: {@code LIVE_WS} → {@code LIVE_REST} → {@code SIMULATED}. The supervisor only
 * reports a mode it has actually achieved, and the UI reads {@link #activeMode()} so simulated data
 * is never presented as live. Simulated fallback guarantees the application always starts and runs,
 * even with no network at all.
 */
public class MarketDataSourceSupervisor implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(MarketDataSourceSupervisor.class.getName());

    /** The source actually in use. */
    public enum Mode {
        LIVE_WS("LIVE WebSocket", ProviderKind.LIVE),
        LIVE_REST("LIVE REST", ProviderKind.LIVE),
        SIMULATED("SIMULATED", ProviderKind.SIMULATED);

        private final String label;
        private final ProviderKind kind;

        Mode(String label, ProviderKind kind) {
            this.label = label;
            this.kind = kind;
        }

        public String label() {
            return label;
        }

        public ProviderKind kind() {
            return kind;
        }
    }

    private final List<String> symbols;
    private final Consumer<StreamMessage> onLiveTick;
    private final String wsBaseUrl;
    private final String restBaseUrl;
    private final Optional<BinancePublicProvider> restProvider;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong liveTicks = new AtomicLong();

    private volatile Mode activeMode = Mode.SIMULATED;
    private volatile BinanceWebSocketClient socketClient;
    private volatile String statusDetail = "Starting";
    private volatile long lastTickAtMillis;
    private volatile java.util.function.Consumer<Mode> modeListener = mode -> { };

    /** Registers a listener told about every achieved mode, including later degradation. */
    public void setModeListener(java.util.function.Consumer<Mode> listener) {
        this.modeListener = listener == null ? mode -> { } : listener;
        modeListener.accept(activeMode);
    }

    private void applyMode(Mode mode, String detail) {
        this.activeMode = mode;
        this.statusDetail = detail;
        modeListener.accept(mode);
    }

    public MarketDataSourceSupervisor(List<String> symbols, Consumer<StreamMessage> onLiveTick,
                                      boolean allowNetwork) {
        this(symbols, onLiveTick, allowNetwork, null, null);
    }

    /**
     * Test/demo seam: allows the endpoints to be overridden so the degradation chain
     * (LIVE_WS → LIVE_REST → SIMULATED) can be exercised deterministically, instead of only being
     * inspectable in code. A null argument keeps the real Binance endpoint.
     *
     * @param wsBaseUrl   WebSocket endpoint, or null for Binance
     * @param restBaseUrl REST endpoint, or null for Binance
     */
    public MarketDataSourceSupervisor(List<String> symbols, Consumer<StreamMessage> onLiveTick,
                                      boolean allowNetwork, String wsBaseUrl, String restBaseUrl) {
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("at least one symbol is required");
        }
        this.symbols = List.copyOf(symbols);
        this.onLiveTick = onLiveTick;
        this.wsBaseUrl = wsBaseUrl == null
                ? "wss://stream.binance.com:9443/stream?streams="
                : wsBaseUrl;
        this.restBaseUrl = restBaseUrl;
        this.restProvider = allowNetwork
                ? Optional.of(restBaseUrl == null
                        ? new BinancePublicProvider()
                        : new BinancePublicProvider(restBaseUrl))
                : Optional.empty();
    }

    /**
     * Attempts to start live streaming, falling back to REST and then simulated.
     *
     * @return the mode actually achieved
     */
    public Mode start() {
        if (!running.compareAndSet(false, true)) {
            return activeMode;
        }

        if (restProvider.isPresent() && connectSocket()) {
            applyMode(Mode.LIVE_WS, "Streaming " + symbols.size() + " symbols");
            return activeMode;
        }

        if (restProvider.isPresent() && restProvider.get().connect()) {
            applyMode(Mode.LIVE_REST, "WebSocket unavailable; using REST");
            LOGGER.info("Market data degraded to " + activeMode.label());
            return activeMode;
        }

        applyMode(Mode.SIMULATED, "No network; using the simulator");
        LOGGER.info("Market data degraded to " + activeMode.label());
        return activeMode;
    }

    private boolean connectSocket() {
        try {
            BinanceWebSocketClient client = new BinanceWebSocketClient(
                    wsBaseUrl,
                    symbols,
                    message -> {
                        liveTicks.incrementAndGet();
                        lastTickAtMillis = System.currentTimeMillis();
                        onLiveTick.accept(message);
                    },
                    this::onSocketDisconnect);
            this.socketClient = client;
            boolean started = client.start();
            if (!started) {
                statusDetail = "WebSocket connect failed: " + client.lastError();
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "WebSocket start failed: " + e.getMessage());
            statusDetail = "WebSocket error: " + e.getMessage();
            return false;
        }
    }

    /** Called by the socket when it drops, so the supervisor can degrade honestly. */
    private void onSocketDisconnect() {
        if (!running.get()) {
            return;
        }
        if (restProvider.isPresent() && restProvider.get().isConnected()) {
            applyMode(Mode.LIVE_REST, "WebSocket disconnected; REST polling");
        } else {
            applyMode(Mode.SIMULATED, "All live sources unavailable; simulated");
        }
        LOGGER.info("Market data degraded to " + activeMode.label());
    }

    public Mode activeMode() {
        return activeMode;
    }

    /** Provenance the UI must display. */
    public ProviderKind kind() {
        return activeMode.kind();
    }

    public boolean isLive() {
        return activeMode.kind().isReal();
    }

    /** True when no external source is in use, so every displayed price is generated locally. */
    public boolean isSimulated() {
        return activeMode == Mode.SIMULATED;
    }

    public String statusDetail() {
        return statusDetail;
    }

    public long liveTicks() {
        return liveTicks.get();
    }

    public long duplicatesSuppressed() {
        BinanceWebSocketClient client = socketClient;
        return client == null ? 0L : client.duplicatesSuppressed();
    }

    public long reconnects() {
        BinanceWebSocketClient client = socketClient;
        return client == null ? 0L : client.reconnects();
    }

    public long lastTickAgeMillis() {
        return lastTickAtMillis == 0 ? -1L : System.currentTimeMillis() - lastTickAtMillis;
    }

    /**
     * Historical candles for backtesting.
     *
     * @return candles from the real provider, or an empty list when offline
     */
    public List<com.tradingdesk.marketdata.Candle> history(String symbol, String interval, int limit) {
        return restProvider
                .map(provider -> provider.history(symbol, interval, limit))
                .orElse(List.of());
    }

    /** True when real historical data is obtainable. */
    public boolean hasHistoricalSource() {
        return restProvider.isPresent();
    }

    /** One line for the status bar, always naming the real source. */
    public String display() {
        return activeMode.label() + " · " + statusDetail;
    }

    public void stop() {
        running.set(false);
        BinanceWebSocketClient client = socketClient;
        if (client != null) {
            client.stop();
        }
        restProvider.ifPresent(BinancePublicProvider::disconnect);
    }

    @Override
    public void close() {
        stop();
    }
}
