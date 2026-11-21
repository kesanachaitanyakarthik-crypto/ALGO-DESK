package com.tradingdesk.exchange;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * The single authority on whether live trading may run.
 *
 * <p>Live mode requires <strong>all three</strong> of the following, checked every time an order is
 * placed rather than once at startup:
 * <ol>
 *   <li>the operator started the JVM with {@code -Dtd.live=true};</li>
 *   <li>API credentials are present in the environment;</li>
 *   <li>the operator completed an explicit confirmation in the UI.</li>
 * </ol>
 *
 * <p>Anything less keeps the application in {@link TradingMode#PAPER}. There is no code path that
 * transitions to live implicitly: {@link #activateLive(String)} demands the typed confirmation
 * phrase, and {@link #killSwitch()} immediately reverts to paper.
 */
public class TradingModeGuard {

    private static final Logger LOGGER = Logger.getLogger(TradingModeGuard.class.getName());

    /** The exact phrase an operator must type. Deliberately awkward to type by accident. */
    public static final String CONFIRMATION_PHRASE = "ENABLE LIVE TRADING";

    private static final String LIVE_PROPERTY = "td.live";

    private final boolean propertyAllowsLive;
    private final java.util.function.Supplier<Optional<ExchangeCredentials>> credentialSupplier;

    private volatile TradingMode mode = TradingMode.PAPER;
    private volatile boolean confirmed;
    private volatile boolean killed;
    private volatile String lastReason = "Application started in paper mode";

    public TradingModeGuard() {
        this(Boolean.getBoolean(LIVE_PROPERTY), ExchangeCredentials::fromEnvironment);
    }

    /** Injectable constructor so the three conditions can be exercised independently in tests. */
    public TradingModeGuard(boolean propertyAllowsLive,
                            java.util.function.Supplier<Optional<ExchangeCredentials>> credentialSupplier) {
        this.propertyAllowsLive = propertyAllowsLive;
        this.credentialSupplier = Objects.requireNonNull(credentialSupplier, "credentialSupplier");
        if (!propertyAllowsLive) {
            lastReason = "Live trading not enabled: -D" + LIVE_PROPERTY + "=true absent";
        }
    }

    public TradingMode mode() {
        return mode;
    }

    public boolean isLive() {
        return mode.isLive();
    }

    public boolean isPropertyAllowsLive() {
        return propertyAllowsLive;
    }

    public boolean isKilled() {
        return killed;
    }

    public boolean hasCredentials() {
        return credentialSupplier.get().isPresent();
    }

    public String lastReason() {
        return lastReason;
    }

    /**
     * Checks every precondition without changing state — used by the UI to explain what is missing.
     */
    public boolean livePermitted() {
        return propertyAllowsLive && !killed && hasCredentials() && confirmed;
    }

    /** Human-readable explanation of why live mode is or is not available. */
    public String diagnostics() {
        StringBuilder text = new StringBuilder();
        text.append("-D").append(LIVE_PROPERTY).append("=true: ")
                .append(propertyAllowsLive ? "present" : "MISSING").append("; ");
        text.append("credentials: ").append(hasCredentials() ? "present" : "MISSING").append("; ");
        text.append("confirmation: ").append(confirmed ? "given" : "MISSING").append("; ");
        text.append("kill switch: ").append(killed ? "ENGAGED" : "clear");
        return text.toString();
    }

    /**
     * Attempts to enter live mode.
     *
     * @param typedPhrase must equal {@link #CONFIRMATION_PHRASE} exactly
     * @throws LiveTradingDisabledException when any precondition is unmet, with the specific reason
     */
    public void activateLive(String typedPhrase) {
        if (!CONFIRMATION_PHRASE.equals(typedPhrase)) {
            lastReason = "Confirmation phrase did not match";
            throw new LiveTradingDisabledException(
                    "Live trading not activated: confirmation phrase did not match");
        }
        if (!propertyAllowsLive) {
            lastReason = "Live trading not enabled at startup";
            throw new LiveTradingDisabledException(
                    "Live trading not activated: start the JVM with -D" + LIVE_PROPERTY + "=true");
        }
        if (!hasCredentials()) {
            lastReason = "No exchange credentials in the environment";
            throw new LiveTradingDisabledException(
                    "Live trading not activated: set EXCHANGE_API_KEY and EXCHANGE_API_SECRET");
        }
        if (killed) {
            lastReason = "Kill switch engaged";
            throw new LiveTradingDisabledException(
                    "Live trading not activated: the kill switch is engaged; restart to clear it");
        }

        confirmed = true;
        mode = TradingMode.LIVE;
        lastReason = "LIVE mode active";
        LOGGER.warning("LIVE trading mode activated by explicit operator confirmation");
    }

    /** Returns to paper mode and clears the confirmation. */
    public void revertToPaper(String reason) {
        mode = TradingMode.PAPER;
        confirmed = false;
        lastReason = reason;
        LOGGER.info("Reverted to PAPER mode: " + reason);
    }

    /**
     * Emergency stop. Engages permanently for this process: live mode cannot be re-entered until the
     * application is restarted, which prevents a panicked click from immediately re-arming trading.
     */
    public void killSwitch() {
        killed = true;
        revertToPaper("Kill switch engaged");
        LOGGER.severe("KILL SWITCH engaged — live trading disabled for this session");
    }

    /**
     * Re-checks the preconditions immediately before an order is placed. A caller must invoke this
     * rather than trusting an earlier check, so revoking credentials mid-session takes effect.
     */
    public void requireLivePermitted() {
        if (!livePermitted()) {
            lastReason = "Live order refused: " + diagnostics();
            throw new LiveTradingDisabledException("Live order refused — " + lastReason);
        }
    }
}
