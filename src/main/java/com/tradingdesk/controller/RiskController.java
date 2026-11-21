package com.tradingdesk.controller;

import com.tradingdesk.ui.RoutedView;
import com.tradingdesk.ui.ViewSnapshot;
import com.tradingdesk.ui.cells.StatusChipCell;
import com.tradingdesk.ui.cells.SymbolCell;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/** Risk alerts raised by the risk manager, plus the active threshold reference. */
public class RiskController implements RoutedView {

    private final ObservableList<ViewSnapshot.AlertRow> rows = FXCollections.observableArrayList();

    @FXML private TableView<ViewSnapshot.AlertRow> alertTable;
    @FXML private TableColumn<ViewSnapshot.AlertRow, String> ruleColumn;
    @FXML private TableColumn<ViewSnapshot.AlertRow, String> symbolColumn;
    @FXML private TableColumn<ViewSnapshot.AlertRow, String> severityColumn;
    @FXML private TableColumn<ViewSnapshot.AlertRow, String> messageColumn;
    @FXML private TableColumn<ViewSnapshot.AlertRow, String> timeColumn;
    @FXML private Label summaryLabel;

    @FXML
    private void initialize() {
        ruleColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().rule()));
        symbolColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().symbol()));
        symbolColumn.setCellFactory(col -> new SymbolCell());
        severityColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().severity()));
        severityColumn.setCellFactory(col -> new StatusChipCell());
        messageColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().message()));
        timeColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().raisedAt()));
        alertTable.setItems(rows);

        Label placeholder = new Label("No risk violations — all orders passed validation");
        placeholder.getStyleClass().add("card-hint");
        alertTable.setPlaceholder(placeholder);
    }

    @Override
    public String title() {
        return "Risk Management";
    }

    @Override
    public void refresh(ViewSnapshot snapshot) {
        rows.setAll(snapshot.alerts());
        if (summaryLabel != null) {
            long critical = snapshot.alerts().stream().filter(a -> "CRITICAL".equals(a.severity())).count();
            summaryLabel.setText(snapshot.alerts().size() + " alerts · " + critical + " critical");
        }
    }
}
