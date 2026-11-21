package com.tradingdesk.backtest;

import com.tradingdesk.model.PriceBar;
import com.tradingdesk.strategy.StrategyAction;
import com.tradingdesk.strategy.StrategyDecision;
import com.tradingdesk.strategy.StrategyParameters;
import com.tradingdesk.strategy.TradingStrategy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Event-driven backtester.
 *
 * <h2>Look-ahead safety</h2>
 * At bar {@code i} the strategy receives {@link HistoricalDataset#contextUpTo(int, boolean)} for
 * {@code i}, so it can only see bars {@code 0..i}. Its decision is executed at the <em>open of bar
 * {@code i+1}</em> — the first price actually available after acting on bar {@code i}'s close.
 * Nothing is ever filled at a price the decision could have observed.
 *
 * <h2>Costs</h2>
 * Commission and slippage apply on both sides, and every trade's P&L is net of them, so a strategy
 * cannot look profitable by ignoring friction.
 *
 * <h2>Order of operations per bar</h2>
 * <ol>
 *   <li>Malformed/late decision from the previous bar is materialised at this bar's open.</li>
 *   <li>Protective stop and take-profit are checked against this bar's high/low range.</li>
 *   <li>Equity is marked to this bar's close.</li>
 * </ol>
 */
public class BacktestEngine {

    /** Cost and sizing configuration for a run. */
    public record Config(double initialCapital, double commissionBps, double slippageBps,
                         double riskFraction) {

        public Config {
            if (!Double.isFinite(initialCapital) || initialCapital <= 0.0) {
                throw new IllegalArgumentException("initialCapital must be positive");
            }
            if (commissionBps < 0.0 || slippageBps < 0.0) {
                throw new IllegalArgumentException("costs must not be negative");
            }
            if (riskFraction <= 0.0 || riskFraction > 1.0) {
                throw new IllegalArgumentException("riskFraction must be within (0, 1]");
            }
        }

        public static Config defaults() {
            return new Config(100_000.0, 5.0, 3.0, 1.0);
        }

        public static Config frictionless() {
            return new Config(100_000.0, 0.0, 0.0, 1.0);
        }
    }

    private static final double BARS_PER_YEAR_DAILY = 252.0;

    private final Config config;

    public BacktestEngine(Config config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public BacktestResult run(TradingStrategy strategy, HistoricalDataset dataset,
                              StrategyParameters parameters) {
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(parameters, "parameters");

        List<Double> equityCurve = new ArrayList<>();
        List<BacktestTrade> trades = new ArrayList<>();

        double cash = config.initialCapital();
        OpenPosition open = null;
        StrategyDecision pendingDecision = null;
        Instant pendingDecisionAt = null;

        for (int i = 0; i < dataset.size(); i++) {
            PriceBar bar = dataset.barAt(i);

            // 1. Materialise the previous bar's decision at this bar's open.
            if (pendingDecision != null && open == null && pendingDecision.isEntry()) {
                long quantity = sizeFor(cash, bar.open(), pendingDecision.sizeFraction());
                if (quantity > 0) {
                    boolean isLong = pendingDecision.action() == StrategyAction.ENTER_LONG;
                    double fill = applySlippage(bar.open(), isLong);
                    double fee = feeFor(fill, quantity);
                    cash -= fee;
                    open = new OpenPosition(isLong, quantity, fill, fee, bar.openedAt(),
                            pendingDecision.stopLoss(), pendingDecision.takeProfit());
                }
            }
            pendingDecision = null;

            // 2. Protective exits are checked against this bar's range.
            if (open != null) {
                Double exitPrice = stopOrTargetHit(open, bar);
                if (exitPrice != null) {
                    BacktestTrade trade = close(open, exitPrice, bar.openedAt());
                    trades.add(trade);
                    cash += trade.pnl();
                    open = null;
                }
            }

            // 3. Mark equity to this bar's close.
            double equity = cash;
            if (open != null) {
                equity += open.unrealisedPnl(bar.close());
            }
            equityCurve.add(equity);

            // 4. Decide, but only if there is a subsequent bar to execute against.
            if (i < dataset.size() - 1) {
                var context = dataset.contextUpTo(i, open != null);
                try {
                    StrategyDecision decision = strategy.evaluate(context, parameters);
                    if (open != null && decision.action() == StrategyAction.EXIT) {
                        BacktestTrade trade = close(open, dataset.barAt(i + 1).open(),
                                dataset.barAt(i + 1).openedAt());
                        trades.add(trade);
                        cash += trade.pnl();
                        open = null;
                    } else if (open == null && decision.isEntry()) {
                        pendingDecision = decision;
                        pendingDecisionAt = dataset.barAt(i + 1).openedAt();
                    }
                } catch (IllegalArgumentException e) {
                    // Not enough history for this strategy yet: keep walking the series.
                    pendingDecision = null;
                }
            }
        }

        // 5. Close anything still open at the final close so the equity curve is complete.
        if (open != null) {
            PriceBar last = dataset.barAt(dataset.size() - 1);
            BacktestTrade trade = close(open, last.close(), last.openedAt());
            trades.add(trade);
            cash += trade.pnl();
            equityCurve.set(equityCurve.size() - 1, cash);
        }

        BacktestMetrics metrics = BacktestMetrics.from(
                config.initialCapital(), equityCurve, trades, BARS_PER_YEAR_DAILY);
        return new BacktestResult(strategy.id(), dataset.symbol(), equityCurve, trades, metrics);
    }

    /** The stop or target price if this bar's range touched it, otherwise null. */
    private static Double stopOrTargetHit(OpenPosition position, PriceBar bar) {
        if (position.stopPrice > 0.0) {
            boolean hit = position.longPosition
                    ? bar.low() <= position.stopPrice
                    : bar.high() >= position.stopPrice;
            if (hit) {
                return position.stopPrice;
            }
        }
        if (position.targetPrice > 0.0) {
            boolean hit = position.longPosition
                    ? bar.high() >= position.targetPrice
                    : bar.low() <= position.targetPrice;
            if (hit) {
                return position.targetPrice;
            }
        }
        return null;
    }

    private BacktestTrade close(OpenPosition position, double rawExitPrice, Instant at) {
        double exitFill = applySlippage(rawExitPrice, !position.longPosition);
        double exitFee = feeFor(exitFill, position.quantity);
        double gross = position.longPosition
                ? (exitFill - position.entryPrice) * position.quantity
                : (position.entryPrice - exitFill) * position.quantity;
        double net = gross - position.entryFee - exitFee;

        return new BacktestTrade(position.longPosition ? "LONG" : "SHORT",
                position.quantity, position.entryPrice, exitFill, net,
                position.entryFee + exitFee, position.openedAt, at);
    }

    private double feeFor(double price, long quantity) {
        return price * quantity * (config.commissionBps() / 10_000.0);
    }

    private double applySlippage(double price, boolean buying) {
        double factor = config.slippageBps() / 10_000.0;
        return buying ? price * (1.0 + factor) : price * (1.0 - factor);
    }

    private long sizeFor(double equity, double price, double sizeFraction) {
        if (price <= 0.0) {
            return 0;
        }
        double budget = equity * config.riskFraction() * Math.max(0.0, Math.min(1.0, sizeFraction));
        return (long) Math.floor(budget / price);
    }

    /** Immutable working position used only during a single run. */
    private record OpenPosition(boolean longPosition, long quantity, double entryPrice,
                                double entryFee, Instant openedAt, double stopPrice,
                                double targetPrice) {

        double unrealisedPnl(double markPrice) {
            return longPosition
                    ? (markPrice - entryPrice) * quantity
                    : (entryPrice - markPrice) * quantity;
        }
    }
}
