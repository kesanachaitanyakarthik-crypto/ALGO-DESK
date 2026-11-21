package com.tradingdesk.paper;

import com.tradingdesk.model.Side;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A virtual trading account.
 *
 * <p>Tracks cash separately from margin so buying power can be computed without any knowledge of
 * the portfolio layer. All mutations are synchronized: fills arrive from worker threads while the
 * JavaFX thread reads balances.
 */
public class PaperAccount {

    /** Immutable record of a cash movement. */
    public record Transaction(Instant at, String kind, double amount, String detail) {
        public Transaction {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(kind, "kind");
        }
    }

    private final String accountId;
    private final String currency;
    private final double startingBalance;
    private final List<Transaction> history = new ArrayList<>();
    private double cash;
    private double usedMargin;
    private double realizedPnl;

    public PaperAccount(String accountId, String currency, double startingBalance) {
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.currency = Objects.requireNonNull(currency, "currency");
        if (!Double.isFinite(startingBalance) || startingBalance <= 0.0) {
            throw new IllegalArgumentException("starting balance must be positive");
        }
        this.startingBalance = startingBalance;
        this.cash = startingBalance;
        record("OPEN", startingBalance, "Account opened");
    }

    public String accountId() {
        return accountId;
    }

    public String currency() {
        return currency;
    }

    public synchronized double startingBalance() {
        return startingBalance;
    }

    public synchronized double cash() {
        return cash;
    }

    public synchronized double usedMargin() {
        return usedMargin;
    }

    /** Available balance: cash not committed as margin. */
    public synchronized double availableBalance() {
        return cash - usedMargin;
    }

    public synchronized double realizedPnl() {
        return realizedPnl;
    }

    /** Equity = available cash plus margin still posted against open positions. */
    public synchronized double equity() {
        return cash;
    }

    /**
     * Total account value: cash plus the market value of held positions.
     * The portfolio supplies the market value, keeping this class free of position logic.
     */
    public synchronized double totalValue(double positionsMarketValue) {
        return cash + positionsMarketValue;
    }

    /** True when the account can fund the given notional plus fees. */
    public synchronized boolean canAfford(double notional, double fees) {
        return availableBalance() >= notional + fees;
    }

    /** Applies an execution, moving cash and booking fees. */
    public synchronized void applyFill(OrderFill fill, Side side, Instant when) {
        Objects.requireNonNull(fill, "fill");
        double notional = fill.notional();

        if (side == Side.BUY) {
            double outflow = notional + fill.fee();
            cash -= outflow;
            record("BUY_FILL", -outflow, fill.quantity() + " @ " + fill.price());
        } else {
            double inflow = notional - fill.fee();
            cash += inflow;
            record("SELL_FILL", inflow, fill.quantity() + " @ " + fill.price());
        }
    }

    /**
     * Posts collateral for a short position. A cash account funds shorts from available balance,
     * so this reserves (rather than spends) the margin until the position is closed.
     */
    public synchronized void postMargin(double amount) {
        if (amount <= 0.0) {
            return;
        }
        if (availableBalance() < amount) {
            throw new IllegalStateException("insufficient available balance to post margin");
        }
        usedMargin += amount;
    }

    /** Records a realized profit or loss on a closed portion of a position. */
    public synchronized void applyRealizedPnl(double pnl, String detail, Instant when) {
        if (pnl == 0.0) {
            return;
        }
        realizedPnl += pnl;
        cash += pnl;
        record(pnl >= 0 ? "REALIZED_GAIN" : "REALIZED_LOSS", pnl, detail);
    }

    /** Releases margin once a position is fully closed. */
    public synchronized void releaseMargin(double amount) {
        usedMargin = Math.max(0.0, usedMargin - Math.max(0.0, amount));
    }

    public synchronized List<Transaction> history() {
        return List.copyOf(history);
    }

    public synchronized void reset(double newStartingBalance) {
        if (!Double.isFinite(newStartingBalance) || newStartingBalance <= 0.0) {
            throw new IllegalArgumentException("starting balance must be positive");
        }
        cash = newStartingBalance;
        usedMargin = 0.0;
        realizedPnl = 0.0;
        history.clear();
        record("RESET", newStartingBalance, "Account reset");
    }

    private void record(String kind, double amount, String detail) {
        history.add(new Transaction(Instant.now(), kind, amount, detail));
        // Bound memory: keep the most recent transactions.
        while (history.size() > 2_000) {
            history.remove(0);
        }
    }
}
