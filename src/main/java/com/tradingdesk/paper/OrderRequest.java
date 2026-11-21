package com.tradingdesk.paper;

import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.Side;

import java.util.Objects;
import java.util.UUID;

/**
 * A request to open a paper order. Validation is strict: a request that cannot be placed is
 * rejected at construction rather than surfacing as a mysterious later failure.
 */
public record OrderRequest(
        String clientTag,
        Instrument instrument,
        Side side,
        OrderKind kind,
        long quantity,
        double limitPrice,
        double stopPrice,
        double takeProfitPrice
) {

    public OrderRequest {
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(kind, "kind");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (kind.requiresLimitPrice() && !isValidPrice(limitPrice)) {
            throw new IllegalArgumentException(kind + " orders require a positive limit price");
        }
        if (kind.requiresStopPrice() && !isValidPrice(stopPrice)) {
            throw new IllegalArgumentException(kind + " orders require a positive stop price");
        }
        if (kind.requiresTakeProfitPrice() && !isValidPrice(takeProfitPrice)) {
            throw new IllegalArgumentException("TAKE_PROFIT orders require a positive take-profit price");
        }
        if (!isValidPrice(limitPrice) && limitPrice != 0.0) {
            throw new IllegalArgumentException("limit price must be a finite positive number or 0");
        }
        if (!isValidPrice(stopPrice) && stopPrice != 0.0) {
            throw new IllegalArgumentException("stop price must be a finite positive number or 0");
        }
        if (!isValidPrice(takeProfitPrice) && takeProfitPrice != 0.0) {
            throw new IllegalArgumentException("take-profit price must be a finite positive number or 0");
        }
        clientTag = clientTag == null ? "" : clientTag;
    }

    public static OrderRequest market(Instrument instrument, Side side, long quantity) {
        return new OrderRequest(UUID.randomUUID().toString(), instrument, side,
                OrderKind.MARKET, quantity, 0.0, 0.0, 0.0);
    }

    public static OrderRequest limit(Instrument instrument, Side side, long quantity, double limitPrice) {
        return new OrderRequest(UUID.randomUUID().toString(), instrument, side,
                OrderKind.LIMIT, quantity, limitPrice, 0.0, 0.0);
    }

    public static OrderRequest stop(Instrument instrument, Side side, long quantity, double stopPrice) {
        return new OrderRequest(UUID.randomUUID().toString(), instrument, side,
                OrderKind.STOP, quantity, 0.0, stopPrice, 0.0);
    }

    public static OrderRequest stopLimit(Instrument instrument, Side side, long quantity,
                                         double stopPrice, double limitPrice) {
        return new OrderRequest(UUID.randomUUID().toString(), instrument, side,
                OrderKind.STOP_LIMIT, quantity, limitPrice, stopPrice, 0.0);
    }

    public static OrderRequest takeProfit(Instrument instrument, Side side, long quantity,
                                          double takeProfitPrice) {
        return new OrderRequest(UUID.randomUUID().toString(), instrument, side,
                OrderKind.TAKE_PROFIT, quantity, 0.0, 0.0, takeProfitPrice);
    }

    private static boolean isValidPrice(double price) {
        return Double.isFinite(price) && price > 0.0;
    }
}
