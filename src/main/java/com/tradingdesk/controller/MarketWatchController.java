package com.tradingdesk.controller;

import com.tradingdesk.service.TradingService;
import com.tradingdesk.ui.MarketRow;
import com.tradingdesk.ui.Notifications;
import com.tradingdesk.ui.RoutedView;
import com.tradingdesk.ui.ViewSnapshot;
import com.tradingdesk.ui.cells.ChangeCell;
import com.tradingdesk.ui.cells.NumericCell;
import com.tradingdesk.ui.cells.SymbolCell;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.List;
import java.util.Locale;

/**
 * Full market watch list with a symbol filter driven by the shell search box.
 */
public class MarketWatchController implements RoutedView, ShellController.Filterable {

    private final TradingService tradingService;
    private final ObservableList<MarketRow> rows = FXCollections.observableArrayList();

    @FXML private TableView<MarketRow> marketTable;
    @FXML private TableColumn<MarketRow, String> symbolColumn;
    @FXML private TableColumn<MarketRow, String> lastColumn;
    @FXML private TableColumn<MarketRow, String> changeColumn;
    @FXML private TableColumn<MarketRow, String> volumeColumn;
    @FXML private TableColumn<MarketRow, String> momentumColumn;
    @FXML private Label summaryLabel;

    private List<MarketRow> source = List.of();
    private String query = "";

    public MarketWatchController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @FXML
    private void initialize() {
        symbolColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().symbol()));
        symbolColumn.setCellFactory(col -> new SymbolCell());
        lastColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().last()));
        lastColumn.setCellFactory(col -> new NumericCell());
        changeColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().changePercent()));
        changeColumn.setCellFactory(col -> new ChangeCell());
        volumeColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().volume()));
        volumeColumn.setCellFactory(col -> new NumericCell());
        momentumColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().up() ? "UP" : "DOWN"));
        momentumColumn.setCellFactory(col -> new com.tradingdesk.ui.cells.StatusChipCell());
        marketTable.setItems(rows);

        Label placeholder = new Label("Waiting for market data — press START");
        placeholder.getStyleClass().add("card-hint");
        marketTable.setPlaceholder(placeholder);
        Notifications.tooltip(marketTable, "Live simulated quotes for the selected instruments");
    }

    @Override
    public String title() {
        return "Market Watch";
    }

    @Override
    public void refresh(ViewSnapshot snapshot) {
        source = snapshot.markets();
        apply();
    }

    @Override
    public void applyFilter(String text) {
        query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        apply();
    }

    private void apply() {
        List<MarketRow> filtered = query.isEmpty()
                ? source
                : source.stream().filter(row -> row.symbol().toLowerCase(Locale.ROOT).contains(query)).toList();
        rows.setAll(filtered);
        if (summaryLabel != null) {
            summaryLabel.setText(filtered.size() + " instruments · spread " + tradingService.averageSpread());
        }
    }
}
