package com.tradingdesk.exchange;

import java.util.Objects;

/**
 * Exchange API credentials.
 *
 * <p>Security properties enforced here:
 * <ul>
 *   <li>Values come from the environment only; nothing is hardcoded or written to disk.</li>
 *   <li>{@link #toString()} is masked, so an accidental log or exception cannot leak a secret.</li>
 *   <li>The secret is never exposed via a public accessor — it is only handed to the signer.</li>
 * </ul>
 */
public final class ExchangeCredentials {

    private final String apiKey;
    private final String apiSecret;

    private ExchangeCredentials(String apiKey, String apiSecret) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    /**
     * Loads credentials from the environment.
     *
     * @return the credentials, or empty when either variable is absent or blank
     */
    public static java.util.Optional<ExchangeCredentials> fromEnvironment() {
        String key = System.getenv("EXCHANGE_API_KEY");
        String secret = System.getenv("EXCHANGE_API_SECRET");
        if (key == null || key.isBlank() || secret == null || secret.isBlank()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new ExchangeCredentials(key.trim(), secret.trim()));
    }

    /** For tests and explicit provisioning; never persist the values passed here. */
    public static ExchangeCredentials of(String apiKey, String apiSecret) {
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(apiSecret, "apiSecret");
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            throw new IllegalArgumentException("api key and secret must not be blank");
        }
        return new ExchangeCredentials(apiKey, apiSecret);
    }

    /** The public identifier, safe to display. */
    public String apiKey() {
        return apiKey;
    }

    /**
     * The signing secret. Package-private on purpose: only the signer should reach it, so ordinary
     * application code cannot accidentally log or transmit it.
     */
    String apiSecret() {
        return apiSecret;
    }

    /** A non-reversible short fingerprint, usable in the UI to show which key is loaded. */
    public String keyFingerprint() {
        String digest = RequestSigner.sha256Hex(apiKey);
        return digest.substring(0, 8);
    }

    @Override
    public String toString() {
        return "ExchangeCredentials[apiKey=" + mask(apiKey) + ", apiSecret=***]";
    }

    private static String mask(String value) {
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }
}
