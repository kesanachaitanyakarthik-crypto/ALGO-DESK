package com.tradingdesk.controller;

import com.tradingdesk.service.TradingService;
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
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.List;
import java.util.Locale;

/** Open orders and full order history, including rejection reasons. */
public class OrdersController implements RoutedView, ShellController.Filterable {

    private final TradingService tradingService;
    private final ObservableList<ViewSnapshot.OrderRow> openRows = FXCollections.observableArrayList();
    private final ObservableList<ViewSnapshot.OrderRow> historyRows = FXCollections.observableArrayList();

    @FXML private TableView<ViewSnapshot.OrderRow> openTable;
    @FXML private TableColumn<ViewSnapshot.OrderRow, String> openIdColumn;
    @FXML private TableColumn<ViewSnapshot.OrderRow, String> openSymbolColumn;
    @FXML private TableColumn<ViewSnapshot.OrderRow, String> openSideColumn;
    @FXML private TableColumn<ViewSnapshot.OrderRow, String> openQtyColumn;
    @FXML private TableColumn<ViewSnapshot.OrderRow, String> openStatusColumn;

    @FXML private TableView<ViewSnapshot.OrderRow> historyTable;
    @FXML private TableColumn<ViewSnapshot.OrderRow, String> historySymbolColumn;
    @FXML private TableColumn<ViewSnapshot.OrderRow, String> historySideColumn;
    @FXML private TableColumn<ViewSnapshot.OrderRow, String> historyQtyColumn;
    @FXML private TableColumn<ViewSnapshot.OrderRow, String> historyStatusColumn;
    @FXML private TableColumn<ViewSnapshot.OrderRow, String> historyReasonColumn;
    @FXML private TableColumn<ViewSnapshot.OrderRow, String> historyTimeColumn;

    @FXML private Label summaryLabel;

    private List<ViewSnapshot.OrderRow> sourceOpen = List.of();
    private List<ViewSnapshot.OrderRow> sourceHistory = List.of();
    private String query = "";

    public OrdersController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @FXML
    private void initialize() {
        openIdColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().id()));
        openSymbolColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().symbol()));
        openSymbolColumn.setCellFactory(col -> new SymbolCell());
        openSideColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().side()));
        openSideColumn.setCellFactory(col -> new StatusChipCell());
        openQtyColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().quantity()));
        openQtyColumn.setCellFactory(col -> new NumericCell());
        openStatusColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status()));
        openStatusColumn.setCellFactory(col -> new StatusChipCell());
        openTable.setItems(openRows);

        historySymbolColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().symbol()));
        historySymbolColumn.setCellFactory(col -> new SymbolCell());
        historySideColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().side()));
        historySideColumn.setCellFactory(col -> new StatusChipCell());
        historyQtyColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().quantity()));
        historyQtyColumn.setCellFactory(col -> new NumericCell());
        historyStatusColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status()));
        historyStatusColumn.setCellFactory(col -> new StatusChipCell());
        historyReasonColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().reason()));
        historyTimeColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().createdAt()));
        historyTable.setItems(historyRows);

        Label openPlaceholder = new Label("No open orders — orders fill immediately in simulation");
        openPlaceholder.getStyleClass().add("card-hint");
        openTable.setPlaceholder(openPlaceholder);

        Label historyPlaceholder = new Label("No orders yet");
        historyPlaceholder.getStyleClass().add("card-hint");
        historyTable.setPlaceholder(historyPlaceholder);

        Notifications.tooltip(historyTable, "Rejected orders always carry a reason");
    }

    @Override
    public String title() {
        return "Orders";
    }

    @Override
    public void refresh(ViewSnapshot snapshot) {
        sourceOpen = snapshot.openOrders();
        sourceHistory = snapshot.orderHistory();
        apply();
        if (summaryLabel != null) {
            ViewSnapshot.OrderSummary summary = snapshot.orderSummary();
            summaryLabel.setText(summary.submitted() + " submitted · " + summary.executed()
                    + " executed · " + summary.rejected() + " rejected (" + summary.rejectionRate() + ")");
        }
    }

    @Override
    public void applyFilter(String text) {
        query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        apply();
    }

    private void apply() {
        openRows.setAll(filter(sourceOpen));
        historyRows.setAll(filter(sourceHistory));
    }

    private List<ViewSnapshot.OrderRow> filter(List<ViewSnapshot.OrderRow> rows) {
        if (query.isEmpty()) {
            return rows;
        }
        return rows.stream()
                .filter(row -> row.symbol().toLowerCase(Locale.ROOT).contains(query)
                        || row.status().toLowerCase(Locale.ROOT).contains(query)
                        || row.side().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }
}
