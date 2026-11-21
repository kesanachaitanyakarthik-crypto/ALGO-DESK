package com.tradingdesk.controller;

import com.tradingdesk.service.TradingService;
import com.tradingdesk.ui.Notifications;
import com.tradingdesk.ui.RoutedView;
import com.tradingdesk.ui.ViewSnapshot;
import com.tradingdesk.ui.cells.NumericCell;
import com.tradingdesk.ui.cells.StatusChipCell;
import com.tradingdesk.ui.cells.SymbolCell;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Simulated order management.
 *
 * <p>Two tables: the automated signal path's orders, and the paper-trading book where the user can
 * place virtual orders and watch them move through the lifecycle. Placement runs off the JavaFX
 * thread and the result is published back with {@code Platform.runLater}.
 */
public class OrdersController implements RoutedView, ShellController.Filterable {

    private final TradingService tradingService;
    private final ObservableList<ViewSnapshot.OrderRow> openRows = FXCollections.observableArrayList();
    private final ObservableList<ViewSnapshot.OrderRow> historyRows = FXCollections.observableArrayList();
    private final ObservableList<ViewSnapshot.PaperOrderRow> paperRows =
            FXCollections.observableArrayList();

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

    @FXML private TableView<ViewSnapshot.PaperOrderRow> paperTable;
    @FXML private TableColumn<ViewSnapshot.PaperOrderRow, String> paperSymbolColumn;
    @FXML private TableColumn<ViewSnapshot.PaperOrderRow, String> paperSideColumn;
    @FXML private TableColumn<ViewSnapshot.PaperOrderRow, String> paperKindColumn;
    @FXML private TableColumn<ViewSnapshot.PaperOrderRow, String> paperQtyColumn;
    @FXML private TableColumn<ViewSnapshot.PaperOrderRow, String> paperFilledColumn;
    @FXML private TableColumn<ViewSnapshot.PaperOrderRow, String> paperAvgColumn;
    @FXML private TableColumn<ViewSnapshot.PaperOrderRow, String> paperStatusColumn;
    @FXML private TableColumn<ViewSnapshot.PaperOrderRow, String> paperReasonColumn;

    @FXML private ComboBox<String> orderSymbol;
    @FXML private ComboBox<String> orderSide;
    @FXML private ComboBox<String> orderKind;
    @FXML private TextField orderQuantity;
    @FXML private TextField orderPrice;
    @FXML private Label orderResultLabel;
    @FXML private Label paperAccountLabel;

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

        Label openPlaceholder = new Label("No open automated orders — they fill immediately");
        openPlaceholder.getStyleClass().add("card-hint");
        openTable.setPlaceholder(openPlaceholder);

        Label historyPlaceholder = new Label("No orders yet");
        historyPlaceholder.getStyleClass().add("card-hint");
        historyTable.setPlaceholder(historyPlaceholder);

        Notifications.tooltip(historyTable, "Rejected orders always carry a reason");

        bindPaperTable();
        buildOrderForm();
    }

    /** Wires the paper-trading table (virtual orders with partial fills and fees). */
    private void bindPaperTable() {
        if (paperTable == null) {
            return;
        }
        paperSymbolColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().symbol()));
        paperSymbolColumn.setCellFactory(col -> new SymbolCell<>());
        paperSideColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().side()));
        paperSideColumn.setCellFactory(col -> new StatusChipCell<>());
        paperKindColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().kind()));
        paperQtyColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().quantity()));
        paperQtyColumn.setCellFactory(col -> new NumericCell<>());
        paperFilledColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().filled()));
        paperFilledColumn.setCellFactory(col -> new NumericCell<>());
        paperAvgColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().averagePrice()));
        paperAvgColumn.setCellFactory(col -> new NumericCell<>());
        paperStatusColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status()));
        paperStatusColumn.setCellFactory(col -> new StatusChipCell<>());
        paperReasonColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().reason()));
        paperTable.setItems(paperRows);

        Label placeholder = new Label("No paper orders yet — place one using the form above");
        placeholder.getStyleClass().add("card-hint");
        paperTable.setPlaceholder(placeholder);
    }

    /** Populates the virtual order form from the live instrument list. */
    private void buildOrderForm() {
        if (orderSide == null || orderKind == null || orderSymbol == null) {
            return;
        }
        orderSide.setItems(FXCollections.observableArrayList("BUY", "SELL"));
        orderSide.getSelectionModel().selectFirst();
        orderKind.setItems(FXCollections.observableArrayList(
                "MARKET", "LIMIT", "STOP", "STOP_LIMIT", "TAKE_PROFIT"));
        orderKind.getSelectionModel().selectFirst();
        // Guard against a missing service: the symbol list must never break FXML loading.
        if (tradingService != null) {
            orderSymbol.setItems(FXCollections.observableArrayList(tradingService.availableSymbols()));
            orderSymbol.getSelectionModel().selectFirst();
        }
        orderQuantity.setText("10");
        orderPrice.setText("0");

        Notifications.tooltip(orderQuantity, "Virtual quantity — no real money is involved");
        Notifications.tooltip(orderPrice, "Required for LIMIT / STOP / STOP_LIMIT / TAKE_PROFIT");
    }

    /** Places a virtual order. All execution is simulated. */
    @FXML
    private void onPlaceOrder() {
        if (orderResultLabel == null) {
            return;
        }
        String symbol = orderSymbol == null ? null : orderSymbol.getValue();
        String side = orderSide == null ? null : orderSide.getValue();
        String kind = orderKind == null ? null : orderKind.getValue();
        if (symbol == null || side == null || kind == null) {
            orderResultLabel.setText("Select symbol, side and order type");
            return;
        }

        long quantity;
        double price;
        try {
            quantity = Long.parseLong(orderQuantity.getText().trim());
            price = Double.parseDouble(orderPrice.getText().trim());
        } catch (NumberFormatException e) {
            orderResultLabel.setText("Invalid quantity or price");
            return;
        }

        orderResultLabel.setText("Submitting…");
        CompletableFuture
                .supplyAsync(() -> tradingService.placePaperOrder(symbol, side, kind, quantity, price))
                .thenAccept(result -> Platform.runLater(() -> orderResultLabel.setText(result)))
                .exceptionally(failure -> {
                    Platform.runLater(() -> orderResultLabel.setText("Failed: " + failure.getMessage()));
                    return null;
                });
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
        if (paperTable != null) {
            paperRows.setAll(snapshot.paper().orders());
        }
        if (paperAccountLabel != null) {
            paperAccountLabel.setText(String.format(Locale.ROOT,
                    "Virtual account %s · cash %s · equity %s · margin %s · realised %s · open %s",
                    snapshot.paper().accountId(), snapshot.paper().cash(), snapshot.paper().equity(),
                    snapshot.paper().usedMargin(), snapshot.paper().realizedPnl(),
                    snapshot.paper().openOrders()));
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
