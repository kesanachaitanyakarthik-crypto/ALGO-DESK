package com.tradingdesk.database.dao;

import com.tradingdesk.model.Instrument;

import java.util.Map;
import java.util.Optional;

public interface InstrumentDao {

    void upsert(Instrument instrument);

    Optional<Instrument> find(String symbol);

    Map<String, Instrument> findAll();

    boolean delete(String symbol);
}
