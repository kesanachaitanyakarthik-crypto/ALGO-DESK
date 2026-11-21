package com.tradingdesk.portfolio;

import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.PortfolioSnapshot;
import com.tradingdesk.model.Position;
import com.tradingdesk.model.Side;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultPortfolioManager implements PortfolioManager {

    private final ConcurrentMap<String, Position> positions = new ConcurrentHashMap<>();
    private volatile double cash;
    private volatile double realizedProfitLoss;

    public DefaultPortfolioManager(double startingCash) {
        requireNonNegative(startingCash);
        this.cash = startingCash;
    }

    @Override
    public synchronized void applyFill(Instrument instrument, Side side, long quantity, double price) {
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(side, "side");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (price <= 0.0) {
            throw new IllegalArgumentException("price must be positive");
        }
        long signedDelta = side == Side.BUY ? quantity : -quantity;
        positions.compute(instrument.symbol(), (symbol, existing) -> applyFill(
                instrument, existing == null ? Position.flat(instrument) : existing, signedDelta, price));
        cash -= signedDelta * price;
    }

    /**
     * Applies a signed quantity change. Opposite-direction fills realise P&L on the closed
     * portion; a fill that crosses zero opens a new position in the opposite direction.
     */
    private Position applyFill(Instrument instrument, Position existing, long signedDelta, double price) {
        long held = existing.quantity();
        if (held == 0L) {
            return new Position(instrument, signedDelta, price);
        }

        boolean sameDirection = (held > 0) == (signedDelta > 0);
        if (sameDirection) {
            long combined = held + signedDelta;
            double average = ((existing.averagePrice() * Math.abs(held)) + (price * Math.abs(signedDelta)))
                    / Math.abs(combined);
            return new Position(instrument, combined, average);
        }

        long closingQuantity = Math.min(Math.abs(held), Math.abs(signedDelta));
        double direction = held > 0 ? 1.0 : -1.0;
        realizedProfitLoss += (price - existing.averagePrice()) * closingQuantity * direction;

        long remaining = held + signedDelta;
        if (remaining == 0L) {
            return Position.flat(instrument);
        }
        if ((remaining > 0) == (held > 0)) {
            return new Position(instrument, remaining, existing.averagePrice());
        }
        return new Position(instrument, remaining, price);
    }

    @Override
    public Position position(Instrument instrument) {
        return positions.getOrDefault(instrument.symbol(), Position.flat(instrument));
    }

    @Override
    public Map<String, Position> positions() {
        return new HashMap<>(positions);
    }

    @Override
    public PortfolioSnapshot snapshot(Map<String, Double> marketPrices) {
        double marketValue = 0.0;
        double unrealized = 0.0;
        for (Position position : positions.values()) {
            if (position.isFlat()) {
                continue;
            }
            double price = marketPrices.getOrDefault(position.instrument().symbol(), position.averagePrice());
            marketValue += position.marketValue(price);
            unrealized += position.unrealizedProfitLoss(price);
        }
        return new PortfolioSnapshot(cash, marketValue, realizedProfitLoss, unrealized, Instant.now());
    }

    @Override
    public double totalExposure(Map<String, Double> marketPrices) {
        double exposure = 0.0;
        for (Position position : positions.values()) {
            if (position.isFlat()) {
                continue;
            }
            double price = marketPrices.getOrDefault(position.instrument().symbol(), position.averagePrice());
            exposure += Math.abs(position.marketValue(price));
        }
        return exposure;
    }

    @Override
    public double projectedExposure(Instrument instrument, Side side, long quantity,
                                    double price, Map<String, Double> marketPrices) {
        double projected = totalExposure(marketPrices);
        Position current = position(instrument);
        double currentExposure = current.isFlat()
                ? 0.0
                : Math.abs(current.marketValue(price));
        long projectedQuantity = current.quantity() + (side == Side.BUY ? quantity : -quantity);
        double projectedExposure = Math.abs(projectedQuantity * price);
        return Math.max(0.0, projected - currentExposure + projectedExposure);
    }

    @Override
    public double cash() {
        return cash;
    }

    @Override
    public double realizedProfitLoss() {
        return realizedProfitLoss;
    }

    @Override
    public synchronized void reset(double startingCash) {
        requireNonNegative(startingCash);
        positions.clear();
        cash = startingCash;
        realizedProfitLoss = 0.0;
    }

    private static void requireNonNegative(double value) {
        if (value < 0.0) {
            throw new IllegalArgumentException("starting cash must not be negative");
        }
    }
}
