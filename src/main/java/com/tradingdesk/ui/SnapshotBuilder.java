package com.tradingdesk.ui;

import com.tradingdesk.indicators.IndicatorSnapshot;
import com.tradingdesk.model.Order;
import com.tradingdesk.model.PortfolioSnapshot;
import com.tradingdesk.model.RiskAlert;
import com.tradingdesk.model.SignalType;
import com.tradingdesk.model.TradeSignal;
import com.tradingdesk.orders.OrderStatistics;
import com.tradingdesk.service.TradingService;
import com.tradingdesk.utils.Formatters;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Maps live service data into the display-oriented {@link ViewSnapshot}.
 *
 * <p>Kept free of JavaFX types so every formatting and aggregation rule can be unit tested
 * headlessly.
 */
public final class SnapshotBuilder {

    private final TradingService service;
    private final double startingCash;
    private final Map<String, Double> sessionOpen = new LinkedHashMap<>();
    private String analyticsSymbol;

    public SnapshotBuilder(TradingService service, double startingCash) {
        this.service = service;
        this.startingCash = startingCash;
    }

    public void setAnalyticsSymbol(String symbol) {
        this.analyticsSymbol = symbol;
    }

    public String analyticsSymbol() {
        if (analyticsSymbol == null || !service.selectedSymbols().contains(analyticsSymbol)) {
            analyticsSymbol = service.selectedSymbols().stream().findFirst().orElse(null);
        }
        return analyticsSymbol;
    }

    public ViewSnapshot build() {
        Map<String, Double> prices = service.latestPrices();
        Map<String, Long> volumes = service.priceVolumes();

        for (Map.Entry<String, Double> entry : prices.entrySet()) {
            sessionOpen.computeIfAbsent(entry.getKey(), key -> entry.getValue());
        }

        List<MarketRow> markets = buildMarkets(prices, volumes);

        PortfolioSnapshot portfolio = service.portfolioSnapshot();
        OrderStatistics stats = service.orderStatistics();
        List<TradeSignal> signals = service.recentSignals(40);

        return new ViewSnapshot(
                System.currentTimeMillis(),
                service.isRunning(),
                service.databaseStatus(),
                markets,
                Map.copyOf(prices),
                service.averageSpread(),
                buildPortfolio(portfolio),
                buildSignals(signals),
                countSignals(signals),
                buildOrders(service.openOrders(50)),
                buildOrders(service.orderHistory(50)),
                buildAlerts(service.recentAlerts(30)),
                new ViewSnapshot.OrderSummary(
                        stats.submitted(),
                        stats.executed(),
                        stats.rejected(),
                        Formatters.percent(stats.rejectionRate())),
                buildSystem(),
                buildIndicators(),
                service.recentLogs(120),
                service.marketDataStatus(),
                buildPaper(),
                buildPerformance());
    }

    /** Maps the paper trading view, keeping every figure sourced from the real broker. */
    private ViewSnapshot.PaperTradingView buildPaper() {
        var paper = service.paperTrading();
        List<ViewSnapshot.PaperOrderRow> rows = new ArrayList<>(paper.orders().size());
        for (var row : paper.orders()) {
            rows.add(new ViewSnapshot.PaperOrderRow(row.id(), row.symbol(), row.side(), row.kind(),
                    row.quantity(), row.filled(), row.averagePrice(), row.status(), row.reason()));
        }
        return new ViewSnapshot.PaperTradingView(paper.accountId(), paper.cash(),
                paper.usedMargin(), paper.equity(), paper.realizedPnl(),
                Long.toString(paper.openCount()), rows);
    }

    private ViewSnapshot.PerformanceView buildPerformance() {
        double cpu = service.processCpuLoad();
        String cpuText = cpu < 0.0
                ? "n/a"
                : String.format(Locale.ROOT, "%.1f%%", cpu * 100.0);
        return new ViewSnapshot.PerformanceView(
                micros(service.p50LatencyMicros()),
                micros(service.p95LatencyMicros()),
                micros(service.p99LatencyMicros()),
                cpuText,
                service.eventsRejected());
    }

    private List<MarketRow> buildMarkets(Map<String, Double> prices, Map<String, Long> volumes) {
        List<MarketRow> rows = new ArrayList<>(prices.size());
        for (Map.Entry<String, Double> entry : new TreeMap<>(prices).entrySet()) {
            String symbol = entry.getKey();
            double price = entry.getValue();
            double open = sessionOpen.getOrDefault(symbol, price);
            double changePercent = percentChange(open, price);

            rows.add(new MarketRow(
                    symbol,
                    Formatters.price(price),
                    signedPercent(changePercent),
                    Long.toString(volumes.getOrDefault(symbol, 0L)),
                    changePercent > 0.0));
        }
        return List.copyOf(rows);
    }

    /** Percentage with an explicit sign, so positive/negative styling can rely on it. */
    static String signedPercent(double percent) {
        String formatted = BigDecimal.valueOf(percent).setScale(2, RoundingMode.HALF_UP).toPlainString();
        if (percent > 0.0 && !formatted.startsWith("-")) {
            return "+" + formatted + "%";
        }
        return formatted + "%";
    }

    private ViewSnapshot.PortfolioView buildPortfolio(PortfolioSnapshot portfolio) {
        double equity = portfolio.totalEquity();
        double returnPercent = startingCash == 0.0 ? 0.0 : (equity - startingCash) / startingCash * 100.0;
        return new ViewSnapshot.PortfolioView(
                Formatters.money(equity),
                Formatters.money(portfolio.cash()),
                Formatters.money(portfolio.realizedProfitLoss()),
                Formatters.money(portfolio.unrealizedProfitLoss()),
                signedPercent(returnPercent),
                portfolio.realizedProfitLoss() >= 0.0);
    }

    private static List<ViewSnapshot.SignalRow> buildSignals(List<TradeSignal> signals) {
        List<ViewSnapshot.SignalRow> rows = new ArrayList<>(signals.size());
        for (int i = signals.size() - 1; i >= 0; i--) {
            TradeSignal signal = signals.get(i);
            rows.add(new ViewSnapshot.SignalRow(
                    signal.instrument().symbol(),
                    signal.type().name(),
                    Formatters.percent(signal.confidence()),
                    signal.rationale(),
                    Formatters.time(signal.generatedAt())));
        }
        return List.copyOf(rows);
    }

    static ViewSnapshot.SignalCounts countSignals(List<TradeSignal> signals) {
        long buy = 0;
        long sell = 0;
        long hold = 0;
        for (TradeSignal signal : signals) {
            switch (signal.type()) {
                case BUY -> buy++;
                case SELL -> sell++;
                default -> hold++;
            }
        }
        return new ViewSnapshot.SignalCounts(buy, sell, hold);
    }

    private static List<ViewSnapshot.OrderRow> buildOrders(List<Order> orders) {
        List<ViewSnapshot.OrderRow> rows = new ArrayList<>(orders.size());
        for (Order order : orders) {
            rows.add(new ViewSnapshot.OrderRow(
                    shortId(order.id().toString()),
                    order.instrument().symbol(),
                    order.side().name(),
                    Long.toString(order.quantity()),
                    order.status().name(),
                    order.rejectionReason() == null ? "" : order.rejectionReason(),
                    Formatters.time(order.createdAt())));
        }
        return List.copyOf(rows);
    }

    private static List<ViewSnapshot.AlertRow> buildAlerts(List<RiskAlert> alerts) {
        List<ViewSnapshot.AlertRow> rows = new ArrayList<>(alerts.size());
        for (int i = alerts.size() - 1; i >= 0; i--) {
            RiskAlert alert = alerts.get(i);
            rows.add(new ViewSnapshot.AlertRow(
                    alert.rule(),
                    alert.instrument().symbol(),
                    alert.message(),
                    alert.severity().name(),
                    Formatters.time(alert.raisedAt())));
        }
        return List.copyOf(rows);
    }

    private ViewSnapshot.SystemStats buildSystem() {
        return new ViewSnapshot.SystemStats(
                service.eventsProcessed(),
                service.eventsDropped(),
                service.queueDepth(),
                service.queueCapacity(),
                service.activeWorkerThreads(),
                micros(service.averageLatencyMicros()),
                micros(service.peakLatencyMicros()),
                String.format(Locale.ROOT, "%.1f/s", service.throughputPerSecond()));
    }

    private ViewSnapshot.IndicatorView buildIndicators() {
        String symbol = analyticsSymbol();
        if (symbol == null) {
            return new ViewSnapshot.IndicatorView("-", "-", "-", "-", "-", "-", 0, false,
                    "-", "-", "-", "-", "-", "-");
        }
        IndicatorSnapshot snapshot = service.indicatorsFor(symbol);
        return new ViewSnapshot.IndicatorView(
                symbol,
                Formatters.price(snapshot.simpleMovingAverage()),
                Formatters.price(snapshot.exponentialMovingAverage()),
                signedPercent(snapshot.rateOfChange() * 100.0),
                Formatters.price(snapshot.rollingVolatility()),
                signedPercent(snapshot.orderBookImbalance() * 100.0),
                snapshot.sampleCount(),
                snapshot.hasSufficientData(),
                snapshot.rsi() > 0.0 ? String.format(Locale.ROOT, "%.2f", snapshot.rsi()) : "-",
                String.format(Locale.ROOT, "%.5f", snapshot.macd()),
                snapshot.bollingerUpper() > 0.0
                        ? Formatters.price(snapshot.bollingerLower()) + " – "
                                + Formatters.price(snapshot.bollingerUpper())
                        : "-",
                snapshot.atr() > 0.0 ? Formatters.price(snapshot.atr()) : "-",
                snapshot.stochasticK() > 0.0
                        ? String.format(Locale.ROOT, "%.1f / %.1f",
                                snapshot.stochasticK(), snapshot.stochasticD())
                        : "-",
                snapshot.vwap() > 0.0 ? Formatters.price(snapshot.vwap()) : "-");
    }

    static String micros(double micros) {
        double rounded = Math.round(micros * 10.0) / 10.0;
        return String.format(Locale.ROOT, "%.1f us", rounded);
    }

    static String shortId(String uuid) {
        return uuid == null || uuid.length() < 8 ? String.valueOf(uuid) : uuid.substring(0, 8);
    }

    /** Session-open prices, exposed for the percentage-change chart. */
    public Map<String, Double> sessionOpen() {
        return Map.copyOf(sessionOpen);
    }

    /** Clears the session baseline, e.g. after a state reset. */
    public void resetSession() {
        sessionOpen.clear();
    }

    /** Percentage change of {@code price} against a session-open baseline. */
    public static double percentChange(Double open, double price) {
        if (open == null || open == 0.0) {
            return 0.0;
        }
        return (price - open) / open * 100.0;
    }
}
