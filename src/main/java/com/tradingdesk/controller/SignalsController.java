package com.tradingdesk.controller;

import com.tradingdesk.ui.Notifications;
import com.tradingdesk.ui.RoutedView;
import com.tradingdesk.ui.ViewSnapshot;
import com.tradingdesk.ui.cells.NumericCell;
import com.tradingdesk.ui.cells.StatusChipCell;
import com.tradingdesk.ui.cells.SymbolCell;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.List;
import java.util.Locale;

/**
 * All generated signals plus a BUY/SELL/HOLD distribution chart.
 */
public class SignalsController implements RoutedView, ShellController.Filterable {

    private final ObservableList<ViewSnapshot.SignalRow> rows = FXCollections.observableArrayList();

    @FXML private TableView<ViewSnapshot.SignalRow> signalTable;
    @FXML private TableColumn<ViewSnapshot.SignalRow, String> symbolColumn;
    @FXML private TableColumn<ViewSnapshot.SignalRow, String> typeColumn;
    @FXML private TableColumn<ViewSnapshot.SignalRow, String> confidenceColumn;
    @FXML private TableColumn<ViewSnapshot.SignalRow, String> rationaleColumn;
    @FXML private TableColumn<ViewSnapshot.SignalRow, String> timeColumn;
    @FXML private BarChart<String, Number> mixChart;
    @FXML private CategoryAxis mixXAxis;
    @FXML private NumberAxis mixYAxis;
    @FXML private Label summaryLabel;

    private List<ViewSnapshot.SignalRow> source = List.of();
    private String query = "";

    @FXML
    private void initialize() {
        symbolColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().symbol()));
        symbolColumn.setCellFactory(col -> new SymbolCell());
        typeColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().type()));
        typeColumn.setCellFactory(col -> new StatusChipCell());
        confidenceColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().confidence()));
        confidenceColumn.setCellFactory(col -> new NumericCell());
        rationaleColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().rationale()));
        timeColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().generatedAt()));
        signalTable.setItems(rows);

        Label placeholder = new Label("No signals yet — signals appear after enough price history");
        placeholder.getStyleClass().add("card-hint");
        signalTable.setPlaceholder(placeholder);
        Notifications.tooltip(signalTable, "Rule-based signals: MA crossover plus momentum");
    }

    @Override
    public String title() {
        return "Trading Signals";
    }

    @Override
    public void refresh(ViewSnapshot snapshot) {
        source = snapshot.signals();
        apply();
        updateChart(snapshot.signalCounts());
        if (summaryLabel != null) {
            ViewSnapshot.SignalCounts counts = snapshot.signalCounts();
            summaryLabel.setText(counts.buy() + " buy · " + counts.sell() + " sell · " + counts.hold() + " hold");
        }
    }

    private void updateChart(ViewSnapshot.SignalCounts counts) {
        mixChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Signals");
        addBar(series, "BUY", counts.buy(), "bar-buy");
        addBar(series, "SELL", counts.sell(), "bar-sell");
        addBar(series, "HOLD", counts.hold(), "bar-hold");
        mixChart.getData().add(series);
    }

    /**
     * Adds a bar and colour-codes it by tag. Category colours cannot be expressed reliably
     * through {@code default-colorN} alone, so the node is tagged once it exists.
     */
    private static void addBar(XYChart.Series<String, Number> series, String label,
                               long value, String styleClass) {
        XYChart.Data<String, Number> data = new XYChart.Data<>(label, value);
        data.nodeProperty().addListener((obs, old, node) -> {
            if (node != null) {
                node.getStyleClass().add(styleClass);
            }
        });
        series.getData().add(data);
    }

    @Override
    public void applyFilter(String text) {
        query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        apply();
    }

    private void apply() {
        List<ViewSnapshot.SignalRow> filtered = query.isEmpty()
                ? source
                : source.stream()
                        .filter(row -> row.symbol().toLowerCase(Locale.ROOT).contains(query)
                                || row.rationale().toLowerCase(Locale.ROOT).contains(query)
                                || row.type().toLowerCase(Locale.ROOT).contains(query))
                        .toList();
        rows.setAll(filtered);
    }
}
