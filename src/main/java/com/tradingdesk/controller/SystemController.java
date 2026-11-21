package com.tradingdesk.controller;

import com.tradingdesk.service.TradingService;
import com.tradingdesk.ui.Notifications;
import com.tradingdesk.ui.RoutedView;
import com.tradingdesk.ui.ViewSnapshot;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;

import java.util.concurrent.CompletableFuture;

/**
 * Concurrency and throughput monitoring: queue, workers, latency percentiles, CPU, market data
 * status and a real stress-test runner.
 */
public class SystemController implements RoutedView {

    private final TradingService tradingService;

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
    @FXML private Label p50Value;
    @FXML private Label p95Value;
    @FXML private Label p99Value;
    @FXML private Label cpuValue;
    @FXML private Label rejectedValue;
    @FXML private Label marketDataValue;
    @FXML private Label stressResultLabel;
    @FXML private Button stressButton;
    @FXML private ProgressBar queueBar;
    @FXML private TextArea logArea;

    public SystemController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @FXML
    private void initialize() {
        queueBar.setPrefWidth(220);
        if (stressButton != null) {
            Notifications.tooltip(stressButton,
                    "Run a measured concurrency stress test (producers, backpressure, latency)");
        }
    }

    @Override
    public String title() {
        return "System Monitoring";
    }

    /** Runs the stress test off the FX thread and reports the measured result. */
    @FXML
    private void onRunStressTest() {
        if (stressButton != null) {
            stressButton.setDisable(true);
            stressButton.setText("RUNNING…");
        }
        if (stressResultLabel != null) {
            stressResultLabel.setText("Running stress test…");
        }
        CompletableFuture.supplyAsync(tradingService::runStressTest)
                .thenAccept(summary -> Platform.runLater(() -> {
                    if (stressResultLabel != null) {
                        stressResultLabel.setText(summary);
                    }
                    if (stressButton != null) {
                        stressButton.setDisable(false);
                        stressButton.setText("RUN STRESS TEST");
                    }
                }))
                .exceptionally(failure -> {
                    Platform.runLater(() -> {
                        if (stressResultLabel != null) {
                            stressResultLabel.setText("Stress test failed: " + failure.getMessage());
                        }
                        if (stressButton != null) {
                            stressButton.setDisable(false);
                            stressButton.setText("RUN STRESS TEST");
                        }
                    });
                    return null;
                });
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

        ViewSnapshot.PerformanceView performance = snapshot.performance();
        p50Value.setText(performance.p50Latency());
        p95Value.setText(performance.p95Latency());
        p99Value.setText(performance.p99Latency());
        cpuValue.setText(performance.cpuLoad());
        rejectedValue.setText(Long.toString(performance.eventsRejected()));
        marketDataValue.setText(snapshot.marketDataStatus());

        double utilization = stats.queueCapacity() == 0
                ? 0.0
                : (double) stats.queueDepth() / stats.queueCapacity();
        queueBar.setProgress(Math.min(1.0, utilization));
        queueUtilizationLabel.setText(String.format(java.util.Locale.ROOT,
                "%.1f%% of capacity", utilization * 100.0));

        logArea.setText(String.join(System.lineSeparator(), snapshot.logs()));
        logArea.setScrollTop(Double.MAX_VALUE);
    }
}
