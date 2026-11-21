package com.tradingdesk.exchange;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The live-trading guard. These tests are the core safety evidence for Phase 4: live mode must be
 * impossible unless every precondition is satisfied, and must be revocable instantly.
 */
class TradingModeGuardTest {

    private static final java.util.function.Supplier<Optional<ExchangeCredentials>> WITH_CREDENTIALS =
            () -> Optional.of(ExchangeCredentials.of("key", "secret"));
    private static final java.util.function.Supplier<Optional<ExchangeCredentials>> WITHOUT_CREDENTIALS =
            Optional::empty;

    private static TradingModeGuard paperGuard() {
        return new TradingModeGuard(false, WITHOUT_CREDENTIALS);
    }

    @Test
    void defaultsToPaperMode() {
        TradingModeGuard guard = paperGuard();
        assertEquals(TradingMode.PAPER, guard.mode());
        assertFalse(guard.isLive());
    }

    @Test
    void relatedRuntimeDefaultsToPaperWithoutTheProperty() {
        // The production constructor must not enable live mode on its own.
        assertFalse(new TradingModeGuard().isLive());
    }

    @Test
    void liveIsRefusedWithoutTheStartupProperty() {
        TradingModeGuard guard = new TradingModeGuard(false, WITH_CREDENTIALS);

        assertThrows(LiveTradingDisabledException.class,
                () -> guard.activateLive(TradingModeGuard.CONFIRMATION_PHRASE));
        assertEquals(TradingMode.PAPER, guard.mode());
        assertFalse(guard.isPropertyAllowsLive());
        assertTrue(guard.diagnostics().contains("td.live"),
                "diagnostics must name the missing property");
        assertTrue(guard.lastReason().toLowerCase().contains("not enabled"));
    }

    @Test
    void liveIsRefusedWithoutCredentials() {
        TradingModeGuard guard = new TradingModeGuard(true, WITHOUT_CREDENTIALS);

        assertThrows(LiveTradingDisabledException.class,
                () -> guard.activateLive(TradingModeGuard.CONFIRMATION_PHRASE));
        assertEquals(TradingMode.PAPER, guard.mode());
        assertTrue(guard.lastReason().toLowerCase().contains("credential"));
    }

    @Test
    void liveIsRefusedWithoutTheExactConfirmationPhrase() {
        TradingModeGuard guard = new TradingModeGuard(true, WITH_CREDENTIALS);

        assertThrows(LiveTradingDisabledException.class, () -> guard.activateLive("yes"));
        assertThrows(LiveTradingDisabledException.class, () -> guard.activateLive("enable live trading"));
        assertThrows(LiveTradingDisabledException.class, () -> guard.activateLive(null));
        assertEquals(TradingMode.PAPER, guard.mode());
    }

    @Test
    void liveActivatesWhenAllThreeConditionsHold() {
        TradingModeGuard guard = new TradingModeGuard(true, WITH_CREDENTIALS);

        guard.activateLive(TradingModeGuard.CONFIRMATION_PHRASE);

        assertEquals(TradingMode.LIVE, guard.mode());
        assertTrue(guard.isLive());
        assertTrue(guard.livePermitted());
    }

    @Test
    void killSwitchRevertsToPaperAndBlocksReactivation() {
        TradingModeGuard guard = new TradingModeGuard(true, WITH_CREDENTIALS);
        guard.activateLive(TradingModeGuard.CONFIRMATION_PHRASE);
        assertTrue(guard.isLive());

        guard.killSwitch();

        assertEquals(TradingMode.PAPER, guard.mode());
        assertTrue(guard.isKilled());
        assertThrows(LiveTradingDisabledException.class,
                () -> guard.activateLive(TradingModeGuard.CONFIRMATION_PHRASE),
                "a killed guard must not be re-armed in the same process");
    }

    @Test
    void revertingToPaperClearsConfirmation() {
        TradingModeGuard guard = new TradingModeGuard(true, WITH_CREDENTIALS);
        guard.activateLive(TradingModeGuard.CONFIRMATION_PHRASE);

        guard.revertToPaper("operator request");

        assertEquals(TradingMode.PAPER, guard.mode());
        assertFalse(guard.livePermitted());
    }

    @Test
    void requireLivePermittedThrowsInPaperMode() {
        TradingModeGuard guard = paperGuard();
        assertThrows(LiveTradingDisabledException.class, guard::requireLivePermitted);
    }

    @Test
    void diagnosticsExplainEveryMissingPrecondition() {
        TradingModeGuard guard = paperGuard();
        String diagnostics = guard.diagnostics();

        assertTrue(diagnostics.contains("td.live"));
        assertTrue(diagnostics.contains("MISSING"));
        assertTrue(diagnostics.contains("kill switch"));
    }

    @Test
    void credentialsAreRecheckedAfterRevocation() {
        // A supplier that can start returning empty proves credentials are not cached at startup.
        java.util.concurrent.atomic.AtomicBoolean present = new java.util.concurrent.atomic.AtomicBoolean(true);
        TradingModeGuard guard = new TradingModeGuard(true,
                () -> present.get() ? Optional.of(ExchangeCredentials.of("k", "s")) : Optional.empty());

        guard.activateLive(TradingModeGuard.CONFIRMATION_PHRASE);
        assertTrue(guard.livePermitted());

        present.set(false);
        assertFalse(guard.livePermitted(), "removing credentials must disable live trading");
        assertThrows(LiveTradingDisabledException.class, guard::requireLivePermitted);
    }
}
