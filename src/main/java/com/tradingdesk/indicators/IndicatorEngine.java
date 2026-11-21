package com.tradingdesk.indicators;

import com.tradingdesk.model.MarketEvent;
import com.tradingdesk.model.PriceBar;

public interface IndicatorEngine {

    void ingest(MarketEvent event);

    void ingest(PriceBar bar);

    IndicatorSnapshot latest();

    default IndicatorSnapshot latestIfAvailable() {
        return latest();
    }

    void reset();
}
