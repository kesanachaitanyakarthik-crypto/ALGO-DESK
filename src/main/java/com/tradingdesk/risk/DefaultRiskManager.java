package com.tradingdesk.risk;

import com.tradingdesk.model.AlertSeverity;
import com.tradingdesk.model.RiskAlert;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultRiskManager implements RiskManager {

    private final ConcurrentMap<String, Long> recentOrders = new ConcurrentHashMap<>();
    private volatile RiskConfiguration configuration;

    public DefaultRiskManager(RiskConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    @Override
    public RiskConfiguration configuration() {
        return configuration;
    }

    @Override
    public void updateConfiguration(RiskConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    @Override
    public List<RiskAlert> evaluate(RiskContext context) {
        RiskConfiguration config = this.configuration;
        List<RiskAlert> violations = new ArrayList<>(4);
        var order = context.order();
        var now = context.evaluatedAt();

        if (order.quantity() > config.maxOrderQuantity()) {
            violations.add(alert(order.instrument(), RiskRule.MAX_ORDER_QUANTITY,
                    "quantity " + order.quantity() + " > limit " + config.maxOrderQuantity(), now));
        }

        if (context.projectedPositionValue() > config.maxPositionValue()) {
            violations.add(alert(order.instrument(), RiskRule.MAX_POSITION_SIZE,
                    "projected position value " + round(context.projectedPositionValue())
                            + " > limit " + config.maxPositionValue(), now));
        }

        if (context.portfolio() != null
                && -context.portfolio().realizedProfitLoss() >= config.dailyMaxLoss()) {
            violations.add(alert(order.instrument(), RiskRule.DAILY_MAX_LOSS,
                    "realized loss " + round(-context.portfolio().realizedProfitLoss())
                            + " reached limit " + config.dailyMaxLoss(), now));
        }

        double reference = order.requestedPrice() > 0.0 ? order.requestedPrice() : context.marketPrice();
        double deviation = Math.abs(reference - context.marketPrice()) / context.marketPrice();
        if (deviation > config.maxPriceDeviationPercent()) {
            violations.add(alert(order.instrument(), RiskRule.PRICE_DEVIATION,
                    "price deviation " + round(deviation * 100) + "% > limit "
                            + round(config.maxPriceDeviationPercent() * 100) + "%", now));
        }

        String fingerprint = fingerprint(order);
        Long seenAt = recentOrders.get(fingerprint);
        boolean duplicate = seenAt != null
                && now.toEpochMilli() - seenAt <= config.duplicateOrderWindowMillis();
        if (duplicate) {
            violations.add(alert(order.instrument(), RiskRule.DUPLICATE_ORDER,
                    "identical order within " + config.duplicateOrderWindowMillis() + " ms", now));
        } else {
            recentOrders.put(fingerprint, now.toEpochMilli());
            prune(now.toEpochMilli() - config.duplicateOrderWindowMillis());
        }

        return List.copyOf(violations);
    }

    @Override
    public void resetDuplicateTracking() {
        recentOrders.clear();
    }

    private void prune(long cutoff) {
        recentOrders.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    private static RiskAlert alert(com.tradingdesk.model.Instrument instrument,
                                   RiskRule rule, String detail, java.time.Instant when) {
        return new RiskAlert(instrument, AlertSeverity.CRITICAL, rule.name(),
                rule.label() + ": " + detail, when);
    }

    private static String fingerprint(com.tradingdesk.model.Order order) {
        return order.instrument().symbol() + "|" + order.side() + "|" + order.type() + "|"
                + order.quantity() + "|" + order.requestedPrice();
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
