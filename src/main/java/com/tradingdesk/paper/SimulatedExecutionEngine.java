package com.tradingdesk.paper;

import com.tradingdesk.model.MarketEvent;
import com.tradingdesk.model.Side;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Realistic simulated execution.
 *
 * <p>Fills are priced from the side an order would actually cross: buys lift the ask, sells hit the
 * bid, so the spread is always paid. Slippage is then applied adversely (buys fill higher, sells
 * fill lower), commission is charged on notional, and the fill quantity is capped by both
 * {@link ExecutionCosts#maxFillFraction()} and {@link ExecutionCosts#liquidityPerTick()}. Those caps
 * are what produce partial fills instead of instant full execution.
 */
public class SimulatedExecutionEngine implements ExecutionEngine {

    private final ExecutionCosts costs;

    public SimulatedExecutionEngine(ExecutionCosts costs) {
        this.costs = Objects.requireNonNull(costs, "costs");
    }

    @Override
    public ExecutionCosts costs() {
        return costs;
    }

    @Override
    public boolean isTriggered(WorkingOrder order, MarketEvent quote) {
        OrderRequest request = order.request();
        Side side = request.side();
        return switch (request.kind()) {
            case MARKET -> true;
            case LIMIT -> isLimitSatisfied(side, request.limitPrice(), quote);
            // A stop buys above the stop and sells below it.
            case STOP -> side == Side.BUY
                    ? quote.lastPrice() >= request.stopPrice()
                    : quote.lastPrice() <= request.stopPrice();
            case STOP_LIMIT -> {
                boolean stopHit = side == Side.BUY
                        ? quote.lastPrice() >= request.stopPrice()
                        : quote.lastPrice() <= request.stopPrice();
                yield stopHit && isLimitSatisfied(side, request.limitPrice(), quote);
            }
            // Take-profit closes a position once the target is reached.
            case TAKE_PROFIT -> side == Side.SELL
                    ? quote.lastPrice() >= request.takeProfitPrice()
                    : quote.lastPrice() <= request.takeProfitPrice();
        };
    }

    private static boolean isLimitSatisfied(Side side, double limitPrice, MarketEvent quote) {
        return side == Side.BUY
                ? quote.askPrice() <= limitPrice
                : quote.bidPrice() >= limitPrice;
    }

    @Override
    public Optional<OrderFill> match(WorkingOrder order, MarketEvent quote, Instant now) {
        if (!order.status().isOpen() || order.isFilled()) {
            return Optional.empty();
        }
        if (!isTriggered(order, quote)) {
            return Optional.empty();
        }

        long fillable = order.remainingQuantity();
        if (fillable <= 0) {
            return Optional.empty();
        }

        // Partial fills: cap per tick by fraction of the *original* size, then by liquidity.
        long fractionCap = Math.max(1L, (long) Math.ceil(order.quantity() * costs.maxFillFraction()));
        fillable = Math.min(fillable, fractionCap);
        if (costs.liquidityPerTick() > 0) {
            fillable = Math.min(fillable, costs.liquidityPerTick());
        }
        // Also respect the quote's own volume as a proxy for real liquidity.
        if (quote.volume() > 0) {
            fillable = Math.min(fillable, Math.max(1L, quote.volume()));
        }

        double basePrice = ExecutionEngine.quotedPrice(quote, order.side());
        double slippageFactor = costs.slippageBps() / 10_000.0;
        double slippedPrice = order.side() == Side.BUY
                ? basePrice * (1.0 + slippageFactor)
                : basePrice * (1.0 - slippageFactor);
        double roundedPrice = roundToTick(slippedPrice);

        double notional = roundedPrice * fillable;
        double fee = notional * (costs.commissionBps() / 10_000.0);
        double slippageCost = Math.abs(roundedPrice - basePrice) * fillable;

        return Optional.of(new OrderFill(fillable, roundedPrice, fee, slippageCost, now));
    }

    private static double roundToTick(double price) {
        return Math.round(price * 10_000.0) / 10_000.0;
    }
}
