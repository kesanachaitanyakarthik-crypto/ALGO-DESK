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
    private final Optional<BinancePublicProvider> restProvider;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong liveTicks = new AtomicLong();

    private volatile Mode activeMode = Mode.SIMULATED;
    private volatile BinanceWebSocketClient socketClient;
    private volatile String statusDetail = "Starting";
    private volatile long lastTickAtMillis;

    public MarketDataSourceSupervisor(List<String> symbols, Consumer<StreamMessage> onLiveTick,
                                      boolean allowNetwork) {
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("at least one symbol is required");
        }
        this.symbols = List.copyOf(symbols);
        this.onLiveTick = onLiveTick;
        this.restProvider = allowNetwork
                ? Optional.of(new BinancePublicProvider())
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
            activeMode = Mode.LIVE_WS;
            statusDetail = "Streaming " + symbols.size() + " symbols";
            return activeMode;
        }

        if (restProvider.isPresent() && restProvider.get().connect()) {
            activeMode = Mode.LIVE_REST;
            statusDetail = "WebSocket unavailable; using REST";
            LOGGER.info("Market data degraded to " + activeMode.label());
            return activeMode;
        }

        activeMode = Mode.SIMULATED;
        statusDetail = "No network; using the simulator";
        LOGGER.info("Market data degraded to " + activeMode.label());
        return activeMode;
    }

    private boolean connectSocket() {
        try {
            BinanceWebSocketClient client = new BinanceWebSocketClient(
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
            activeMode = Mode.LIVE_REST;
            statusDetail = "WebSocket disconnected; REST polling";
        } else {
            activeMode = Mode.SIMULATED;
            statusDetail = "All live sources unavailable; simulated";
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
