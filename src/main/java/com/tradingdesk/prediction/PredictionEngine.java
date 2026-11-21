package com.tradingdesk.prediction;

import com.tradingdesk.indicators.IndicatorSnapshot;
import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.TradeSignal;

public interface PredictionEngine {

    TradeSignal predict(Instrument instrument, IndicatorSnapshot snapshot);

    String name();

    String description();
}
