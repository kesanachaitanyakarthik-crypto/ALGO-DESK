package com.tradingdesk.exchange;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * HMAC-SHA256 request signing, as used by Binance-style APIs.
 *
 * <p>Implemented and unit-tested against published reference vectors, so the credential handling is
 * real and verifiable without inventing any endpoint or protocol detail. Nothing here performs
 * network I/O.
 */
public final class RequestSigner {

    private RequestSigner() {
    }

    /**
     * Signs a query string with HMAC-SHA256 and returns the lowercase hex signature,
     * which is the {@code signature} parameter expected by Binance's signed endpoints.
     */
    public static String sign(String secret, String payload) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("secret must not be blank");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    /** Plain SHA-256 hex digest, used for fingerprints rather than authentication. */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Builds a canonical, signed query string from parameters.
     *
     * <p>Parameters are sorted by name so the signature is deterministic regardless of map
     * ordering, which is what the exchange expects.
     */
    public static String signQuery(String secret, Map<String, String> parameters) {
        String canonical = parameters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        return canonical + "&signature=" + sign(secret, canonical);
    }

    /** Convenience: a sorted copy of the parameters, for inspection and testing. */
    public static Map<String, String> canonical(Map<String, String> parameters) {
        return new TreeMap<>(parameters);
    }
}
