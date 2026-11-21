package com.tradingdesk.portfolio;

import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.PortfolioSnapshot;
import com.tradingdesk.model.Position;
import com.tradingdesk.model.Side;

import java.util.Map;

public interface PortfolioManager {

    void applyFill(Instrument instrument, Side side, long quantity, double price);

    Position position(Instrument instrument);

    Map<String, Position> positions();

    PortfolioSnapshot snapshot(Map<String, Double> marketPrices);

    double totalExposure(Map<String, Double> marketPrices);

    double projectedExposure(Instrument instrument, Side side, long quantity,
                             double price, Map<String, Double> marketPrices);

    double cash();

    double realizedProfitLoss();

    void reset(double startingCash);
}
