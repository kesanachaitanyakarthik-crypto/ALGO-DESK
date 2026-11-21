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

    @FXML private ComboBox<String> symbolSelector;
    @FXML private LineChart<String, Number> indicatorChart;
    @FXML private CategoryAxis indicatorXAxis;
    @FXML private NumberAxis indicatorYAxis;
    @FXML private Label smaLabel;
    @FXML private Label emaLabel;
    @FXML private Label rocLabel;
    @FXML private Label volatilityLabel;
    @FXML private Label imbalanceLabel;
    @FXML private Label sampleLabel;
    @FXML private Label reliabilityLabel;

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
    }

    @Override
    public String title() {
        return "Analytics";
    }

    @Override
    public void refresh(ViewSnapshot snapshot) {
        ViewSnapshot.IndicatorView indicators = snapshot.indicators();
        syncSelector(snapshot, indicators.symbol());

        smaLabel.setText(indicators.sma());
        emaLabel.setText(indicators.ema());
        rocLabel.setText(indicators.roc());
        volatilityLabel.setText(indicators.volatility());
        imbalanceLabel.setText(indicators.imbalance());
        sampleLabel.setText(indicators.samples() + " samples");
        reliabilityLabel.setText(indicators.reliable() ? "Live" : "Warming up");

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

    private static double parse(String value) {
        try {
            return Double.parseDouble(value.replace("%", "").replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
