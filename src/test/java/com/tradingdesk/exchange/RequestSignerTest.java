package com.tradingdesk.exchange;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestSignerTest {

    @Test
    void signMatchesRfc4231StyleKnownVector() {
        // HMAC-SHA256("key", "The quick brown fox jumps over the lazy dog")
        // = f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
                RequestSigner.sign("key", "The quick brown fox jumps over the lazy dog"));
    }

    @Test
    void signOfEmptyPayloadIsStillDeterministic() {
        String first = RequestSigner.sign("secret", "");
        String second = RequestSigner.sign("secret", "");
        assertEquals(first, second);
        assertEquals(64, first.length(), "hex-encoded SHA-256 is 64 characters");
    }

    @Test
    void differentSecretsProduceDifferentSignatures() {
        String a = RequestSigner.sign("secret-a", "symbol=BTCUSDT");
        String b = RequestSigner.sign("secret-b", "symbol=BTCUSDT");
        assertTrue(!a.equals(b));
    }

    @Test
    void differentPayloadsProduceDifferentSignatures() {
        String a = RequestSigner.sign("secret", "symbol=BTCUSDT");
        String b = RequestSigner.sign("secret", "symbol=ETHUSDT");
        assertTrue(!a.equals(b));
    }

    @Test
    void querySigningSortsParametersDeterministically() {
        Map<String, String> unordered = new LinkedHashMap<>();
        unordered.put("timestamp", "1700000000000");
        unordered.put("symbol", "BTCUSDT");
        unordered.put("side", "BUY");

        Map<String, String> reordered = new LinkedHashMap<>();
        reordered.put("side", "BUY");
        reordered.put("symbol", "BTCUSDT");
        reordered.put("timestamp", "1700000000000");

        assertEquals(RequestSigner.signQuery("secret", unordered),
                RequestSigner.signQuery("secret", reordered),
                "parameter order must not change the signature");
    }

    @Test
    void signedQueryAppendsSignature() {
        String signed = RequestSigner.signQuery("secret", Map.of("symbol", "BTCUSDT"));
        assertTrue(signed.startsWith("symbol=BTCUSDT&signature="));
        assertEquals("symbol=BTCUSDT&signature=".length() + 64, signed.length());
    }

    @Test
    void rejectsBlankSecretAndNullPayload() {
        assertThrows(IllegalArgumentException.class, () -> RequestSigner.sign("", "payload"));
        assertThrows(IllegalArgumentException.class, () -> RequestSigner.sign("secret", null));
    }

    @Test
    void sha256HexIsStable() {
        assertEquals("2bb80d537b1da3e38bd30361aa855686bde0eacd7162fef6a25fe97bf527a25b",
                RequestSigner.sha256Hex("secret"));
    }

    @Test
    void credentialsNeverLeakTheSecretInToString() {
        ExchangeCredentials credentials = ExchangeCredentials.of("MY-API-KEY-123456", "SUPER-SECRET-VALUE");
        String text = credentials.toString();

        assertTrue(!text.contains("SUPER-SECRET-VALUE"), "the secret must never appear in toString");
        assertTrue(text.contains("***"));
        assertTrue(!text.contains("MY-API-KEY-123456") || text.contains("MY****56"),
                "the key should be masked too");
    }

    @Test
    void credentialsExposeOnlyAFingerprint() {
        ExchangeCredentials credentials = ExchangeCredentials.of("MY-API-KEY-123456", "secret");
        assertEquals(8, credentials.keyFingerprint().length());
        assertTrue(!credentials.keyFingerprint().contains("MY-API"));
    }

    @Test
    void credentialsRejectBlankValues() {
        assertThrows(IllegalArgumentException.class, () -> ExchangeCredentials.of("", "secret"));
        assertThrows(IllegalArgumentException.class, () -> ExchangeCredentials.of("key", "  "));
    }
}
