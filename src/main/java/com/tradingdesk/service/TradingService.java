package com.tradingdesk.service;

import com.tradingdesk.indicators.IndicatorSnapshot;
import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.Order;
import com.tradingdesk.model.PortfolioSnapshot;
import com.tradingdesk.model.RiskAlert;
import com.tradingdesk.model.TradeSignal;
import com.tradingdesk.orders.OrderStatistics;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface TradingService {

    void start();

    void stop();

    boolean isRunning();

    void reset();

    Set<String> availableSymbols();

    Set<String> selectedSymbols();

    void selectSymbols(Set<String> symbols);

    Map<String, Double> latestPrices();

    Map<String, Long> priceVolumes();

    double averageSpread();

    IndicatorSnapshot indicatorsFor(String symbol);

    List<TradeSignal> recentSignals(int limit);

    List<Order> openOrders(int limit);

    List<Order> orderHistory(int limit);

    List<RiskAlert> recentAlerts(int limit);

    PortfolioSnapshot portfolioSnapshot();

    OrderStatistics orderStatistics();

    long eventsProcessed();

    long eventsDropped();

    int queueDepth();

    int queueCapacity();

    int activeWorkerThreads();

    double averageLatencyMicros();

    double peakLatencyMicros();

    double throughputPerSecond();

    String databaseStatus();

    List<String> recentLogs(int limit);

    List<Instrument> watchlist();
}
