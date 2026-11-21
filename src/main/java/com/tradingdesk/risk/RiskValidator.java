package com.tradingdesk.risk;

import com.tradingdesk.model.RiskAlert;

import java.util.List;

public interface RiskValidator {

    List<RiskAlert> validate(RiskContext context);

    String name();
}
