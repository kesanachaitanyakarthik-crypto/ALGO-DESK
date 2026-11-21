package com.tradingdesk.paper;

/**
 * Configurable execution costs and constraints.
 *
 * <p>Defaults are deliberately zero-cost so that a paper configuration can reproduce the legacy
 * frictionless behaviour when desired; realistic trading uses {@link #realistic()}.
 *
 * @param commissionBps      commission in basis points of notional, per fill
 * @param slippageBps        adverse slippage in basis points applied to the fill price
 * @param maxFillFraction    proportion of the order that may fill per tick (1.0 = no partial fills)
 * @param liquidityPerTick   maximum units that can trade per tick; {@code 0} means unlimited
 */
public record ExecutionCosts(
        double commissionBps,
        double slippageBps,
        double maxFillFraction,
        long liquidityPerTick
) {

    public ExecutionCosts {
        if (commissionBps < 0.0) {
            throw new IllegalArgumentException("commissionBps must not be negative");
        }
        if (slippageBps < 0.0) {
            throw new IllegalArgumentException("slippageBps must not be negative");
        }
        if (maxFillFraction <= 0.0 || maxFillFraction > 1.0) {
            throw new IllegalArgumentException("maxFillFraction must be within (0, 1]");
        }
        if (liquidityPerTick < 0) {
            throw new IllegalArgumentException("liquidityPerTick must not be negative");
        }
    }

    /** No fees, no slippage, full fill each tick — matches the legacy behaviour. */
    public static ExecutionCosts frictionless() {
        return new ExecutionCosts(0.0, 0.0, 1.0, 0L);
    }

    /** Typical retail-style costs: 5 bps commission, 3 bps slippage, partial fills. */
    public static ExecutionCosts realistic() {
        return new ExecutionCosts(5.0, 3.0, 0.5, 0L);
    }

    public boolean chargesCommission() {
        return commissionBps > 0.0;
    }

    public boolean appliesSlippage() {
        return slippageBps > 0.0;
    }
}
