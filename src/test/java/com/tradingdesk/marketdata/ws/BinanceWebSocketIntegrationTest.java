package com.tradingdesk.marketdata.ws;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Connects to the real Binance public WebSocket and asserts that live messages arrive.
 *
 * <p>Opt-in via {@code -Dlive.enabled=true}. Skipped (not failed) when the network is unavailable,
 * so the standard suite never depends on the internet.
 */
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "live.enabled", matches = "true")
class BinanceWebSocketIntegrationTest {

    @Test
    void receivesRealStreamingMessages() throws InterruptedException {
        List<StreamMessage> received = new CopyOnWriteArrayList<>();
        AtomicBoolean disconnected = new AtomicBoolean(false);

        BinanceWebSocketClient client = new BinanceWebSocketClient(
                List.of("BTCUSDT", "ETHUSDT"),
                received::add,
                () -> disconnected.set(true));

        boolean started = client.start();
        assumeTrue(started, "Binance WebSocket not reachable from this machine");

        try {
            long deadline = System.currentTimeMillis() + 25_000;
            while (System.currentTimeMillis() < deadline && received.size() < 5) {
                Thread.sleep(200);
            }

            assertTrue(received.size() >= 5,
                    "expected live messages, received " + received.size());
            assertTrue(client.messagesReceived() >= 5);
            assertTrue(client.lastMessageTime() != null, "a message timestamp should be recorded");

            StreamMessage sample = received.get(0);
            assertTrue(sample.symbol().equals("BTCUSDT") || sample.symbol().equals("ETHUSDT"));
            assertTrue(sample.price() > 0.0, "a real price must be positive");

            System.out.println("=== LIVE WEBSOCKET VERIFICATION ===");
            System.out.println("messages received : " + client.messagesReceived());
            System.out.println("duplicates removed: " + client.duplicatesSuppressed());
            System.out.println("reconnects        : " + client.reconnects());
            System.out.println("sample message    : " + sample.kind() + " " + sample.symbol()
                    + " price=" + sample.price() + " bid=" + sample.bid() + " ask=" + sample.ask()
                    + " at " + sample.eventTime());
        } finally {
            client.stop();
        }

        assertTrue(!client.isRunning(), "stopping the client must release the stream");
    }
}
