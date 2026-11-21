package com.tradingdesk.controller;

import com.tradingdesk.service.TradingService;
import com.tradingdesk.ui.Branding;
import com.tradingdesk.ui.RoutedView;
import com.tradingdesk.ui.ViewSnapshot;
import com.tradingdesk.utils.SimulationDisclaimer;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.util.Set;

/**
 * Read-only About/Settings screen: identity, build/stack reference and the live database status.
 *
 * <p>Deliberately performs no configuration writes — business thresholds stay owned by the
 * domain layer.
 */
public class AboutController implements RoutedView {

    private final TradingService tradingService;

    @FXML private StackPane logoHolder;
    @FXML private Label nameLabel;
    @FXML private Label subtitleLabel;
    @FXML private Label versionLabel;
    @FXML private Label dbStatusValue;
    @FXML private Label runtimeValue;
    @FXML private Label disclaimerValue;
    @FXML private Label instrumentsValue;
    @FXML private Label workersValue;
    @FXML private Label modeValue;

    public AboutController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @FXML
    private void initialize() {
        logoHolder.getChildren().setAll(Branding.mark(64));
        nameLabel.setText(Branding.APP_NAME);
        subtitleLabel.setText(Branding.APP_SUBTITLE);
        versionLabel.setText("Version " + Branding.VERSION + " · academic simulation");
        disclaimerValue.setText(SimulationDisclaimer.TEXT);
        runtimeValue.setText(System.getProperty("java.vendor") + " " + System.getProperty("java.version"));
        modeValue.setText("Rule-based signals (MA crossover + momentum) — not a trained ML model");
        // Guard the service: a missing reference must never break FXML loading.
        if (tradingService != null) {
            instrumentsValue.setText(String.join(", ", tradingService.availableSymbols()));
            workersValue.setText(Integer.toString(tradingService.activeWorkerThreads()));
        }
    }

    @Override
    public String title() {
        return "Settings";
    }

    @Override
    public void refresh(ViewSnapshot snapshot) {
        dbStatusValue.setText(snapshot.databaseStatus());
        workersValue.setText(Integer.toString(snapshot.system().activeWorkers()));
        Set<String> symbols = snapshot.prices().keySet().isEmpty()
                ? (tradingService == null ? java.util.Set.<String>of() : tradingService.availableSymbols())
                : snapshot.prices().keySet();
        instrumentsValue.setText(String.join(", ", symbols));
        if (modeValue != null) {
            modeValue.setText("Rule-based strategies plus a trainable logistic-regression "
                    + "classifier — educational outputs, not investment advice");
        }
    }
}
