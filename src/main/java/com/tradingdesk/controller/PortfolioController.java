package com.tradingdesk.controller;

import com.tradingdesk.ui.RoutedView;
import com.tradingdesk.ui.ViewSnapshot;
import com.tradingdesk.ui.cells.NumericCell;

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

import java.util.ArrayList;
import java.util.List;

/**
 * Portfolio overview: equity curve, cash and P&L cards, and the executed-order ledger.
 */
public class PortfolioController implements RoutedView {

    private static final int CURVE_POINTS = 60;

    @FXML private Label equityLabel;
    @FXML private Label cashLabel;
    @FXML private Label realizedLabel;
    @FXML private Label unrealizedLabel;
    @FXML private Label returnLabel;

    @FXML private TableView<ViewSnapshot.OrderRow> ledgerTable;
    @FXML private TableColumn<ViewSnapshot.OrderRow, String> ledgerSymbolColumn;
    @FXML private TableColumn<ViewSnapshot.OrderRow, String> ledgerSideColumn;
    @FXML private TableColumn<ViewSnapshot.OrderRow, String> ledgerQtyColumn;
    @FXML private TableColumn<ViewSnapshot.OrderRow, String> ledgerStatusColumn;
    @FXML private TableColumn<ViewSnapshot.OrderRow, String> ledgerTimeColumn;

    @FXML private LineChart<String, Number> equityChart;
    @FXML private CategoryAxis equityXAxis;
    @FXML private NumberAxis equityYAxis;

    private final ObservableList<ViewSnapshot.OrderRow> ledgerRows = FXCollections.observableArrayList();
    private final List<Double> equityHistory = new ArrayList<>();
    private final XYChart.Series<String, Number> equitySeries = new XYChart.Series<>();
    private int tick;

    @FXML
    private void initialize() {
        equityYAxis.setLabel("Total equity");
        equityXAxis.setAnimated(false);
        equityYAxis.setAnimated(false);
        equityChart.setAnimated(false);
        equitySeries.setName("Equity");
        equityChart.getData().add(equitySeries);

        ledgerSymbolColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().symbol()));
        ledgerSymbolColumn.setCellFactory(col -> new com.tradingdesk.ui.cells.SymbolCell());
        ledgerSideColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().side()));
        ledgerSideColumn.setCellFactory(col -> new com.tradingdesk.ui.cells.StatusChipCell());
        ledgerQtyColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().quantity()));
        ledgerQtyColumn.setCellFactory(col -> new NumericCell());
        ledgerStatusColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status()));
        ledgerStatusColumn.setCellFactory(col -> new com.tradingdesk.ui.cells.StatusChipCell());
        ledgerTimeColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().createdAt()));
        ledgerTable.setItems(ledgerRows);

        Label placeholder = new Label("No filled orders yet");
        placeholder.getStyleClass().add("card-hint");
        ledgerTable.setPlaceholder(placeholder);
    }

    @Override
    public String title() {
        return "Portfolio";
    }

    @Override
    public void refresh(ViewSnapshot snapshot) {
        ViewSnapshot.PortfolioView portfolio = snapshot.portfolio();
        equityLabel.setText(portfolio.totalEquity());
        cashLabel.setText(portfolio.cash());
        realizedLabel.setText(portfolio.realizedPnl());
        unrealizedLabel.setText(portfolio.unrealizedPnl());
        returnLabel.setText(portfolio.returnPercent());

        ledgerRows.setAll(snapshot.orderHistory().stream()
                .filter(row -> "EXECUTED".equals(row.status()))
                .toList());

        double equity = parse(portfolio.totalEquity());
        equityHistory.add(equity);
        while (equityHistory.size() > CURVE_POINTS) {
            equityHistory.remove(0);
        }
        tick++;
        equitySeries.getData().clear();
        for (int i = 0; i < equityHistory.size(); i++) {
            equitySeries.getData().add(new XYChart.Data<>(
                    Integer.toString(tick - equityHistory.size() + i + 1), equityHistory.get(i)));
        }
    }

    private static double parse(String value) {
        try {
            return Double.parseDouble(value.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
