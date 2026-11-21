package com.tradingdesk.risk;

import com.tradingdesk.model.RiskAlert;

import java.util.List;
import java.util.Optional;

public interface RiskManager {

    RiskConfiguration configuration();

    void updateConfiguration(RiskConfiguration configuration);

    List<RiskAlert> evaluate(RiskContext context);

    default Optional<RiskAlert> firstViolation(RiskContext context) {
        return evaluate(context).stream().findFirst();
    }

    void resetDuplicateTracking();
}
