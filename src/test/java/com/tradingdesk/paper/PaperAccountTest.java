package com.tradingdesk.paper;

import com.tradingdesk.model.Side;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperAccountTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private static OrderFill fill(long qty, double price, double fee) {
        return new OrderFill(qty, price, fee, 0.0, NOW);
    }

    @Test
    void accountStartsWithConfiguredBalance() {
        PaperAccount account = new PaperAccount("acc-1", "USD", 50_000.0);

        assertEquals(50_000.0, account.cash(), 1e-9);
        assertEquals(50_000.0, account.availableBalance(), 1e-9);
        assertEquals(0.0, account.usedMargin(), 1e-9);
        assertEquals(0.0, account.realizedPnl(), 1e-9);
        assertEquals(1, account.history().size(), "opening the account records one transaction");
    }

    @Test
    void rejectsNonPositiveStartingBalance() {
        assertThrows(IllegalArgumentException.class,
                () -> new PaperAccount("acc", "USD", 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new PaperAccount("acc", "USD", -100.0));
    }

    @Test
    void buyReducesCashByNotionalPlusFee() {
        PaperAccount account = new PaperAccount("acc", "USD", 10_000.0);
        account.applyFill(fill(10, 100.0, 5.0), Side.BUY, NOW);

        assertEquals(10_000.0 - 1_000.0 - 5.0, account.cash(), 1e-9);
    }

    @Test
    void sellIncreasesCashByNotionalLessFee() {
        PaperAccount account = new PaperAccount("acc", "USD", 10_000.0);
        account.applyFill(fill(10, 100.0, 5.0), Side.SELL, NOW);

        assertEquals(10_000.0 + 1_000.0 - 5.0, account.cash(), 1e-9);
    }

    @Test
    void canAffordAccountsForFees() {
        PaperAccount account = new PaperAccount("acc", "USD", 1_000.0);

        assertTrue(account.canAfford(995.0, 5.0));
        assertFalse(account.canAfford(995.0, 6.0), "fees must be included in the check");
    }

    @Test
    void marginReducesAvailableBalanceWithoutChangingCash() {
        PaperAccount account = new PaperAccount("acc", "USD", 10_000.0);
        account.postMargin(3_000.0);

        assertEquals(10_000.0, account.cash(), 1e-9);
        assertEquals(3_000.0, account.usedMargin(), 1e-9);
        assertEquals(7_000.0, account.availableBalance(), 1e-9);
    }

    @Test
    void marginCannotExceedAvailableBalance() {
        PaperAccount account = new PaperAccount("acc", "USD", 1_000.0);
        assertThrows(IllegalStateException.class, () -> account.postMargin(2_000.0));
    }

    @Test
    void releasingMarginRestoresAvailableBalance() {
        PaperAccount account = new PaperAccount("acc", "USD", 10_000.0);
        account.postMargin(4_000.0);
        account.releaseMargin(4_000.0);

        assertEquals(0.0, account.usedMargin(), 1e-9);
        assertEquals(10_000.0, account.availableBalance(), 1e-9);
    }

    @Test
    void realizedPnlMovesCashAndIsTracked() {
        PaperAccount account = new PaperAccount("acc", "USD", 10_000.0);
        account.applyRealizedPnl(250.0, "closed", NOW);
        account.applyRealizedPnl(-100.0, "closed", NOW);

        assertEquals(150.0, account.realizedPnl(), 1e-9);
        assertEquals(10_150.0, account.cash(), 1e-9);
    }

    @Test
    void totalValueCombinesCashAndPositions() {
        PaperAccount account = new PaperAccount("acc", "USD", 10_000.0);
        assertEquals(12_500.0, account.totalValue(2_500.0), 1e-9);
    }

    @Test
    void historyRecordsEveryCashMovement() {
        PaperAccount account = new PaperAccount("acc", "USD", 10_000.0);
        account.applyFill(fill(10, 100.0, 5.0), Side.BUY, NOW);
        account.applyRealizedPnl(50.0, "closed", NOW);

        assertEquals(3, account.history().size());
        assertTrue(account.history().stream().anyMatch(t -> t.kind().equals("BUY_FILL")));
        assertTrue(account.history().stream().anyMatch(t -> t.kind().equals("REALIZED_GAIN")));
    }

    @Test
    void resetRestoresBalanceAndClearsHistory() {
        PaperAccount account = new PaperAccount("acc", "USD", 10_000.0);
        account.applyFill(fill(10, 100.0, 5.0), Side.BUY, NOW);
        account.reset(20_000.0);

        assertEquals(20_000.0, account.cash(), 1e-9);
        assertEquals(0.0, account.usedMargin(), 1e-9);
        assertEquals(1, account.history().size());
    }
}
