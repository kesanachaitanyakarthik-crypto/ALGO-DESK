package com.tradingdesk.controller;

import com.tradingdesk.ui.RoutedView;
import com.tradingdesk.ui.SnapshotBuilder;
import com.tradingdesk.ui.ViewSnapshot;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-instrument technical view: live price with SMA and EMA overlays, plus the indicator values
 * that drove the latest signal.
 *
 * <p>History is kept per symbol so switching instruments does not wipe the chart, and it is
 * populated from live application data only.
 */
public class AnalyticsController implements RoutedView {

    private static final int POINTS = 60;

    private final SnapshotBuilder snapshotBuilder;

    /** Optional service reference so backtests can run on real historical data. */
    private com.tradingdesk.service.TradingService tradingService;
    private boolean comparisonLoaded;

    @FXML private ComboBox<String> symbolSelector;
    @FXML private LineChart<String, Number> indicatorChart;
    @FXML private CategoryAxis indicatorXAxis;
    @FXML private NumberAxis indicatorYAxis;
    @FXML private Label smaLabel;
    @FXML private Label emaLabel;
    @FXML private Label rocLabel;
    @FXML private Label volatilityLabel;
    @FXML private Label imbalanceLabel;
    @FXML private Label rsiLabel;
    @FXML private Label macdLabel;
    @FXML private Label bollingerLabel;
    @FXML private Label atrLabel;
    @FXML private Label stochasticLabel;
    @FXML private Label vwapLabel;
    @FXML private Label sampleLabel;
    @FXML private Label reliabilityLabel;

    @FXML private TableView<StrategyRow> strategyTable;
    @FXML private TableColumn<StrategyRow, String> strategyNameColumn;
    @FXML private TableColumn<StrategyRow, String> strategyReturnColumn;
    @FXML private TableColumn<StrategyRow, String> strategyWinRateColumn;
    @FXML private TableColumn<StrategyRow, String> strategyDrawdownColumn;
    @FXML private TableColumn<StrategyRow, String> strategyProfitFactorColumn;
    @FXML private TableColumn<StrategyRow, String> strategySharpeColumn;
    @FXML private TableColumn<StrategyRow, String> strategyTradesColumn;
    @FXML private Label backtestSummaryLabel;

    private final javafx.collections.ObservableList<StrategyRow> strategyRows =
            javafx.collections.FXCollections.observableArrayList();

    /** One row of the strategy comparison table, pre-formatted from real backtest metrics. */
    public record StrategyRow(String strategy, String totalReturn, String winRate,
                              String maxDrawdown, String profitFactor, String sharpe,
                              String trades) {
    }

    private final XYChart.Series<String, Number> priceSeries = new XYChart.Series<>();
    private final XYChart.Series<String, Number> smaSeries = new XYChart.Series<>();
    private final XYChart.Series<String, Number> emaSeries = new XYChart.Series<>();

    private final Map<String, Deque<Point>> history = new LinkedHashMap<>();
    private int tick;
    private boolean rebuilding;

    private record Point(String label, double price, double sma, double ema) {
    }

    public AnalyticsController(SnapshotBuilder snapshotBuilder) {
        this.snapshotBuilder = snapshotBuilder;
    }

    /** Supplies the service so the backtest comparison can run. */
    public void attachService(com.tradingdesk.service.TradingService tradingService) {
        this.tradingService = tradingService;
    }

    /**
     * Runs the strategy comparison off the FX thread, once, when the view first becomes visible.
     * Metrics displayed are the measured results of that run.
     */
    @FXML
    private void onRunBacktest() {
        if (tradingService == null || backtestSummaryLabel == null) {
            return;
        }
        backtestSummaryLabel.setText("Running backtest…");
        javafx.concurrent.Task<java.util.List<com.tradingdesk.service.StrategyComparisonRow>> task =
                new javafx.concurrent.Task<>() {
                    @Override
                    protected java.util.List<com.tradingdesk.service.StrategyComparisonRow> call() {
                        return tradingService.strategyComparison();
                    }
                };
        task.setOnSucceeded(event -> {
            var rows = task.getValue();
            if (rows.isEmpty()) {
                backtestSummaryLabel.setText(
                        "No historical data available — " + tradingService.historicalDataSource());
                return;
            }
            java.util.List<StrategyRow> display = new java.util.ArrayList<>(rows.size());
            for (var row : rows) {
                display.add(new StrategyRow(row.strategy(), row.totalReturn(), row.winRate(),
                        row.maxDrawdown(), row.profitFactor(), row.sharpe(), row.trades()));
            }
            strategyRows.setAll(display);
            backtestSummaryLabel.setText("Source: " + tradingService.historicalDataSource());
            comparisonLoaded = true;
        });
        task.setOnFailed(event -> backtestSummaryLabel.setText(
                "Backtest failed: " + task.getException().getMessage()));
        Thread thread = new Thread(task, "td-backtest");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void initialize() {
        indicatorXAxis.setAnimated(false);
        indicatorYAxis.setAnimated(false);
        indicatorChart.setAnimated(false);
        indicatorYAxis.setLabel("Price");

        priceSeries.setName("Price");
        smaSeries.setName("SMA");
        emaSeries.setName("EMA");
        indicatorChart.getData().addAll(priceSeries, smaSeries, emaSeries);

        symbolSelector.setOnAction(event -> {
            if (!rebuilding && symbolSelector.getValue() != null) {
                snapshotBuilder.setAnalyticsSymbol(symbolSelector.getValue());
                render(symbolSelector.getValue());
            }
        });

        bindStrategyTable();
    }

    /** Wires the strategy comparison table so backtest metrics are displayed, not invented. */
    private void bindStrategyTable() {
        if (strategyTable == null) {
            return;
        }
        strategyNameColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().strategy()));
        strategyReturnColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().totalReturn()));
        strategyWinRateColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().winRate()));
        strategyDrawdownColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().maxDrawdown()));
        strategyProfitFactorColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().profitFactor()));
        strategySharpeColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().sharpe()));
        strategyTradesColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().trades()));
        strategyTable.setItems(strategyRows);
    }

    @Override
    public String title() {
        return "Analytics";
    }

    @Override
    public void refresh(ViewSnapshot snapshot) {
        ViewSnapshot.IndicatorView indicators = snapshot.indicators();
        syncSelector(snapshot, indicators.symbol());

        sampleLabel.setText(indicators.samples() + " samples");
        reliabilityLabel.setText(indicators.reliable() ? "Live" : "Warming up");

        setIfPresent(smaLabel, indicators.sma());
        setIfPresent(emaLabel, indicators.ema());
        setIfPresent(rocLabel, indicators.roc());
        setIfPresent(volatilityLabel, indicators.volatility());
        setIfPresent(imbalanceLabel, indicators.imbalance());
        setIfPresent(rsiLabel, indicators.rsi());
        setIfPresent(macdLabel, indicators.macd());
        setIfPresent(bollingerLabel, indicators.bollinger());
        setIfPresent(atrLabel, indicators.atr());
        setIfPresent(stochasticLabel, indicators.stochastic());
        setIfPresent(vwapLabel, indicators.vwap());

        String symbol = indicators.symbol();
        Double price = snapshot.prices().get(symbol);
        if (price == null || "-".equals(symbol)) {
            return;
        }

        tick++;
        Deque<Point> series = history.computeIfAbsent(symbol, key -> new ArrayDeque<>());
        series.addLast(new Point(Integer.toString(tick), price,
                parse(indicators.sma()), parse(indicators.ema())));
        while (series.size() > POINTS) {
            series.removeFirst();
        }
        render(symbol);
    }

    private void render(String symbol) {
        Deque<Point> points = history.get(symbol);
        if (points == null || points.isEmpty()) {
            priceSeries.getData().clear();
            smaSeries.getData().clear();
            emaSeries.getData().clear();
            return;
        }
        List<XYChart.Data<String, Number>> priceData = new ArrayList<>(points.size());
        List<XYChart.Data<String, Number>> smaData = new ArrayList<>(points.size());
        List<XYChart.Data<String, Number>> emaData = new ArrayList<>(points.size());
        for (Point point : points) {
            priceData.add(new XYChart.Data<>(point.label(), point.price()));
            smaData.add(new XYChart.Data<>(point.label(), point.sma()));
            emaData.add(new XYChart.Data<>(point.label(), point.ema()));
        }
        priceSeries.getData().setAll(priceData);
        smaSeries.getData().setAll(smaData);
        emaSeries.getData().setAll(emaData);
    }

    private void syncSelector(ViewSnapshot snapshot, String symbol) {
        List<String> symbols = new ArrayList<>(snapshot.prices().keySet());
        if (symbols.isEmpty()) {
            return;
        }
        if (!symbols.equals(new ArrayList<>(symbolSelector.getItems()))) {
            rebuilding = true;
            String previous = symbolSelector.getValue();
            symbolSelector.setItems(FXCollections.observableArrayList(symbols));
            if (previous != null && symbols.contains(previous)) {
                symbolSelector.setValue(previous);
            } else if (symbol != null && symbols.contains(symbol)) {
                symbolSelector.setValue(symbol);
            } else {
                symbolSelector.getSelectionModel().selectFirst();
            }
            rebuilding = false;
        }
    }

    /** Sets a label only when both exist, so optional fields never cause an NPE. */
    private static void setIfPresent(Label label, String value) {
        if (label != null && value != null) {
            label.setText(value);
        }
    }

    private static double parse(String value) {
        try {
            return Double.parseDouble(value.replace("%", "").replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
