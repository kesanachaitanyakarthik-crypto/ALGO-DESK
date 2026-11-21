package com.tradingdesk.controller;

import com.tradingdesk.service.TradingService;
import com.tradingdesk.ui.MarketRow;
import com.tradingdesk.ui.Notifications;
import com.tradingdesk.ui.RoutedView;
import com.tradingdesk.ui.SnapshotBuilder;
import com.tradingdesk.ui.ViewSnapshot;
import com.tradingdesk.ui.cells.ChangeCell;
import com.tradingdesk.ui.cells.NumericCell;
import com.tradingdesk.ui.cells.StatusChipCell;
import com.tradingdesk.ui.cells.SymbolCell;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Portfolio overview, market watch, live chart, signal summary, concurrency metrics and log.
 */
public class DashboardViewController implements RoutedView {

    private static final int CHART_POINTS = 40;

    private final TradingService tradingService;
    private final SnapshotBuilder snapshotBuilder;

    @FXML private Label kpiEquity;
    @FXML private Label kpiReturn;
    @FXML private Label kpiRealized;
    @FXML private Label kpiUnrealized;
    @FXML private Label kpiCash;
    @FXML private Label kpiEvents;
    @FXML private Label kpiThroughput;
    @FXML private Label kpiWorkers;
    @FXML private Label kpiQueue;
    @FXML private Label kpiLatency;
    @FXML private Label kpiPeak;

    @FXML private Label signalMixLabel;

    @FXML private TableView<MarketRow> marketTable;
    @FXML private TableColumn<MarketRow, String> marketSymbolColumn;
    @FXML private TableColumn<MarketRow, String> marketLastColumn;
    @FXML private TableColumn<MarketRow, String> marketChangeColumn;
    @FXML private TableColumn<MarketRow, String> marketVolumeColumn;

    @FXML private TableView<ViewSnapshot.SignalRow> signalTable;
    @FXML private TableColumn<ViewSnapshot.SignalRow, String> signalSymbolColumn;
    @FXML private TableColumn<ViewSnapshot.SignalRow, String> signalTypeColumn;
    @FXML private TableColumn<ViewSnapshot.SignalRow, String> signalConfidenceColumn;
    @FXML private TableColumn<ViewSnapshot.SignalRow, String> signalTimeColumn;

    @FXML private LineChart<String, Number> priceChart;
    @FXML private CategoryAxis priceChartXAxis;
    @FXML private NumberAxis priceChartYAxis;

    @FXML private Label perfQueue;
    @FXML private Label perfCapacity;
    @FXML private Label perfPeak;
    @FXML private Label perfThroughput;
    @FXML private Label perfDropped;
    @FXML private Label perfExecuted;
    @FXML private Label perfRejected;
    @FXML private Label perfRejectionRate;

    @FXML private TextArea logArea;

    private final ObservableList<MarketRow> marketRows = FXCollections.observableArrayList();
    private final ObservableList<ViewSnapshot.SignalRow> signalRows = FXCollections.observableArrayList();
    private final Map<String, XYChart.Series<String, Number>> chartSeries = new LinkedHashMap<>();
    private final Map<String, List<Double>> chartHistory = new LinkedHashMap<>();
    private int chartTick;

    public DashboardViewController(TradingService tradingService, SnapshotBuilder snapshotBuilder) {
        this.tradingService = tradingService;
        this.snapshotBuilder = snapshotBuilder;
    }

    @FXML
    private void initialize() {
        priceChart.setAnimated(false);
        priceChartXAxis.setAnimated(false);
        priceChartYAxis.setAnimated(false);
        priceChartYAxis.setLabel("% change");

        marketSymbolColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().symbol()));
        marketSymbolColumn.setCellFactory(col -> new SymbolCell());
        marketLastColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().last()));
        marketLastColumn.setCellFactory(col -> new NumericCell());
        marketChangeColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().changePercent()));
        marketChangeColumn.setCellFactory(col -> new ChangeCell());
        marketVolumeColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().volume()));
        marketVolumeColumn.setCellFactory(col -> new NumericCell());
        marketTable.setItems(marketRows);
        marketTable.setPlaceholder(placeholder("Waiting for market data — press START"));

        signalSymbolColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().symbol()));
        signalSymbolColumn.setCellFactory(col -> new SymbolCell());
        signalTypeColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().type()));
        signalTypeColumn.setCellFactory(col -> new StatusChipCell());
        signalConfidenceColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().confidence()));
        signalConfidenceColumn.setCellFactory(col -> new NumericCell());
        signalTimeColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().generatedAt()));
        signalTable.setItems(signalRows);
        signalTable.setPlaceholder(placeholder("No signals yet"));
    }

    private static Label placeholder(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("card-hint");
        return label;
    }

    @Override
    public String title() {
        return "Dashboard";
    }

    @Override
    public void refresh(ViewSnapshot snapshot) {
        ViewSnapshot.PortfolioView portfolio = snapshot.portfolio();
        kpiEquity.setText(portfolio.totalEquity());
        kpiReturn.setText(portfolio.returnPercent());
        kpiReturn.getStyleClass().removeAll("value-up", "value-down", "value-flat");
        kpiReturn.getStyleClass().add(portfolio.returnPercent().startsWith("+") ? "value-up" : "value-down");
        kpiRealized.setText(portfolio.realizedPnl());
        kpiRealized.getStyleClass().removeAll("value-up", "value-down", "value-flat");
        kpiRealized.getStyleClass().add(portfolio.pnlPositive() ? "value-up" : "value-down");
        kpiUnrealized.setText(portfolio.unrealizedPnl());
        kpiCash.setText("cash " + portfolio.cash());

        ViewSnapshot.SystemStats system = snapshot.system();
        kpiEvents.setText(Long.toString(system.eventsProcessed()));
        kpiThroughput.setText(system.throughput());
        kpiWorkers.setText(Integer.toString(system.activeWorkers()));
        kpiQueue.setText("queue " + system.queueDepth());
        kpiLatency.setText(system.averageLatency());
        kpiPeak.setText("peak " + system.peakLatency());

        perfQueue.setText(Integer.toString(system.queueDepth()));
        perfCapacity.setText(Integer.toString(system.queueCapacity()));
        perfPeak.setText(system.peakLatency());
        perfThroughput.setText(system.throughput());
        perfDropped.setText(Long.toString(system.eventsDropped()));

        ViewSnapshot.OrderSummary orders = snapshot.orderSummary();
        perfExecuted.setText(Long.toString(orders.executed()));
        perfRejected.setText(Long.toString(orders.rejected()));
        perfRejectionRate.setText(orders.rejectionRate());

        marketRows.setAll(snapshot.markets());
        signalRows.setAll(snapshot.signals().stream().limit(12).toList());

        ViewSnapshot.SignalCounts counts = snapshot.signalCounts();
        signalMixLabel.setText(counts.buy() + " buy / " + counts.sell() + " sell / " + counts.hold() + " hold");

        updateChart(snapshot);
        logArea.setText(String.join(System.lineSeparator(), snapshot.logs()));
        logArea.setScrollTop(Double.MAX_VALUE);
    }

    private void updateChart(ViewSnapshot snapshot) {
        Set<String> symbols = new LinkedHashSet<>(snapshot.prices().keySet());
        if (!chartSeries.keySet().equals(symbols)) {
            priceChart.getData().clear();
            chartSeries.clear();
            chartHistory.clear();
            for (String symbol : symbols) {
                XYChart.Series<String, Number> series = new XYChart.Series<>();
                series.setName(symbol);
                chartSeries.put(symbol, series);
                chartHistory.put(symbol, new ArrayList<>());
                priceChart.getData().add(series);
            }
        }

        chartTick++;
        Map<String, Double> opens = snapshotBuilder.sessionOpen();
        for (Map.Entry<String, XYChart.Series<String, Number>> entry : chartSeries.entrySet()) {
            String symbol = entry.getKey();
            Double price = snapshot.prices().get(symbol);
            if (price == null) {
                continue;
            }
            double percent = SnapshotBuilder.percentChange(opens.get(symbol), price);
            List<Double> history = chartHistory.get(symbol);
            history.add(percent);
            while (history.size() > CHART_POINTS) {
                history.remove(0);
            }
            entry.getValue().getData().clear();
            for (int i = 0; i < history.size(); i++) {
                String label = Integer.toString(chartTick - history.size() + i + 1);
                XYChart.Data<String, Number> point = new XYChart.Data<>(label, history.get(i));
                entry.getValue().getData().add(point);
                Notifications.tooltip(point.getNode(), symbol + " " + String.format("%.2f%%", history.get(i)));
            }
        }
    }
}
