package com.tradingdesk.paper;

import com.tradingdesk.model.MarketEvent;
import com.tradingdesk.model.Side;

import java.util.Optional;

/**
 * Decides whether and how much of an order executes against a market snapshot.
 *
 * <p>Implementations must respect the quote: a buy pays the ask and a sell receives the bid, so the
 * spread is never ignored.
 */
public interface ExecutionEngine {

    /**
     * @param order   the working order being matched
     * @param quote   the current market snapshot (bid, ask, available volume)
     * @param now     execution timestamp source
     * @return a fill when the order is executable this tick, otherwise empty
     */
    Optional<OrderFill> match(WorkingOrder order, MarketEvent quote, java.time.Instant now);

    /** True when the order's price conditions are satisfied by this quote. */
    boolean isTriggered(WorkingOrder order, MarketEvent quote);

    ExecutionCosts costs();

    /** The quoted price a side would transact at, before slippage. */
    static double quotedPrice(MarketEvent quote, Side side) {
        return side == Side.BUY ? quote.askPrice() : quote.bidPrice();
    }
}
