package com.tradingdesk.orders;

import com.tradingdesk.model.Execution;
import com.tradingdesk.model.Order;
import com.tradingdesk.model.TradeSignal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderManagementSystem {

    OrderResult submit(TradeSignal signal, double marketPrice);

    Optional<Order> find(UUID orderId);

    List<Order> openOrders();

    List<Order> history(int limit);

    List<Execution> executionsFor(UUID orderId);

    boolean cancel(UUID orderId);

    OrderStatistics statistics();

    void reset();
}
