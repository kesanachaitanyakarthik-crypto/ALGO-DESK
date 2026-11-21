package com.tradingdesk.database.dao;

import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.InstrumentType;

public final class InstrumentResolver {

    private final InstrumentDao instrumentDao;

    public InstrumentResolver(InstrumentDao instrumentDao) {
        this.instrumentDao = instrumentDao;
    }

    public Instrument resolve(String symbol) {
        return instrumentDao.find(symbol)
                .orElseGet(() -> new Instrument(symbol, symbol, InstrumentType.EQUITY));
    }

    public InstrumentDao instrumentDao() {
        return instrumentDao;
    }
}
