package com.tradingdesk.orders;

import com.tradingdesk.model.Execution;
import com.tradingdesk.model.Order;
import com.tradingdesk.model.RiskAlert;

import java.util.List;
import java.util.Optional;

public record OrderResult(Order order, Optional<Execution> execution, List<RiskAlert> violations) {

    public OrderResult {
        violations = List.copyOf(violations);
    }

    public boolean executed() {
        return execution.isPresent();
    }

    public boolean rejected() {
        return !violations.isEmpty();
    }

    public String rejectionReason() {
        return order.rejectionReason();
    }
}
