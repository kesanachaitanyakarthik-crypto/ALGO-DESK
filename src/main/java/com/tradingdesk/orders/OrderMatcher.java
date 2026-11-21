package com.tradingdesk.orders;

import com.tradingdesk.model.Order;

@FunctionalInterface
public interface OrderMatcher {

    boolean matches(Order order, double marketPrice);
}
