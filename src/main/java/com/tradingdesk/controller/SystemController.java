package com.tradingdesk.controller;

import com.tradingdesk.ui.RoutedView;
import com.tradingdesk.ui.ViewSnapshot;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;

/** Concurrency and throughput monitoring, plus the live log stream. */
public class SystemController implements RoutedView {

    @FXML private Label eventsValue;
    @FXML private Label droppedValue;
    @FXML private Label queueValue;
    @FXML private Label capacityValue;
    @FXML private Label workersValue;
    @FXML private Label avgLatencyValue;
    @FXML private Label avgLatencyMirror;
    @FXML private Label peakLatencyValue;
    @FXML private Label throughputValue;
    @FXML private Label queueUtilizationLabel;
    @FXML private ProgressBar queueBar;
    @FXML private TextArea logArea;

    @FXML
    private void initialize() {
        queueBar.setPrefWidth(220);
    }

    @Override
    public String title() {
        return "System Monitoring";
    }

    @Override
    public void refresh(ViewSnapshot snapshot) {
        ViewSnapshot.SystemStats stats = snapshot.system();
        eventsValue.setText(Long.toString(stats.eventsProcessed()));
        droppedValue.setText(Long.toString(stats.eventsDropped()));
        queueValue.setText(Integer.toString(stats.queueDepth()));
        capacityValue.setText(Integer.toString(stats.queueCapacity()));
        workersValue.setText(Integer.toString(stats.activeWorkers()));
        avgLatencyValue.setText(stats.averageLatency());
        avgLatencyMirror.setText(stats.averageLatency());
        peakLatencyValue.setText(stats.peakLatency());
        throughputValue.setText(stats.throughput());

        double utilization = stats.queueCapacity() == 0
                ? 0.0
                : (double) stats.queueDepth() / stats.queueCapacity();
        queueBar.setProgress(Math.min(1.0, utilization));
        queueUtilizationLabel.setText(String.format(java.util.Locale.ROOT, "%.1f%% of capacity", utilization * 100.0));

        logArea.setText(String.join(System.lineSeparator(), snapshot.logs()));
        logArea.setScrollTop(Double.MAX_VALUE);
    }
}
