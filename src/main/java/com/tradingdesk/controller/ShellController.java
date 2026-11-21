package com.tradingdesk.controller;

import com.tradingdesk.service.TradingService;
import com.tradingdesk.ui.Branding;
import com.tradingdesk.ui.IconLoader;
import com.tradingdesk.ui.Notifications;
import com.tradingdesk.ui.RoutedView;
import com.tradingdesk.ui.SnapshotBuilder;
import com.tradingdesk.ui.ViewId;
import com.tradingdesk.ui.ViewSnapshot;
import com.tradingdesk.utils.SimulationDisclaimer;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Application shell: fixed chrome around a single routed content area.
 *
 * <p>Owns the one refresh timeline. Each tick builds a {@link ViewSnapshot} and pushes it into
 * the active view only, so inactive screens cost nothing and no view runs its own timer.
 */
public class ShellController {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final double STARTING_CASH = 1_000_000.0;

    private final TradingService tradingService;
    private final SnapshotBuilder snapshotBuilder;
    private final Map<ViewId, ToggleButton> navButtons = new EnumMap<>(ViewId.class);
    private final Map<ViewId, Node> viewCache = new EnumMap<>(ViewId.class);
    private final Map<ViewId, RoutedView> viewControllers = new EnumMap<>(ViewId.class);
    private final Map<String, CheckBox> symbolBoxes = new LinkedHashMap<>();
    private final ToggleGroup navGroup = new ToggleGroup();

    @FXML private VBox sidebar;
    @FXML private HBox brandBox;
    @FXML private StackPane logoHolder;
    @FXML private VBox brandText;
    @FXML private Label brandTitle;
    @FXML private Label brandTag;
    @FXML private VBox navBox;
    @FXML private FlowPane symbolChecks;
    @FXML private StackPane dbIconHolder;
    @FXML private Label dbStatusLabel;

    @FXML private StackPane headerLogoHolder;
    @FXML private Label appTitleLabel;
    @FXML private Label appSubtitleLabel;
    @FXML private Label viewTitleLabel;
    @FXML private HBox searchBox;
    @FXML private StackPane searchIconHolder;
    @FXML private TextField searchField;
    @FXML private HBox dbBadge;
    @FXML private StackPane dbBadgeIconHolder;
    @FXML private Label dbBadgeLabel;
    @FXML private Label runStateLabel;
    @FXML private Label clockLabel;
    @FXML private Label disclaimerLabel;

    @FXML private StackPane contentHost;

    @FXML private Button startButton;
    @FXML private Button stopButton;
    @FXML private Button resetButton;

    @FXML private Label statusMessage;
    @FXML private Label statusEvents;
    @FXML private Label statusQueue;
    @FXML private Label statusWorkers;
    @FXML private Label statusLatency;

    private Timeline refreshTimeline;
    private ViewId activeView;
    private ViewSnapshot lastSnapshot;
    private boolean starting;

    public ShellController(TradingService tradingService) {
        this.tradingService = tradingService;
        this.snapshotBuilder = new SnapshotBuilder(tradingService, STARTING_CASH);
    }

    @FXML
    private void initialize() {
        loadBranding();
        buildNavigation();
        buildSymbolControls();
        setDatabaseIndicator();

        disclaimerLabel.setText(SimulationDisclaimer.TEXT);
        appTitleLabel.setText(Branding.APP_NAME);
        appSubtitleLabel.setText(Branding.APP_SUBTITLE);

        Notifications.tooltip(startButton, "Start the market data simulator and processing pipeline");
        Notifications.tooltip(stopButton, "Stop the simulator; worker threads drain and exit");
        Notifications.tooltip(resetButton, "Clear portfolio, orders and metrics");
        Notifications.tooltip(searchField, "Filter rows by symbol or rationale");

        searchField.textProperty().addListener((obs, old, value) -> applyFilter(value));

        refreshTimeline = new Timeline(new KeyFrame(Duration.millis(600), event -> tick()));
        refreshTimeline.setCycleCount(Animation.INDEFINITE);
        refreshTimeline.play();

        navigateTo(ViewId.DASHBOARD);
        tick();
    }

    // ------------------------------------------------------------------ branding

    private void loadBranding() {
        StackPane sidebarMark = Branding.mark(30);
        logoHolder.getChildren().setAll(sidebarMark);
        brandTitle.setText(Branding.APP_NAME);

        headerLogoHolder.getChildren().setAll(Branding.mark(26));
        searchIconHolder.getChildren().setAll(IconLoader.icon("search", 13));
        dbBadgeIconHolder.getChildren().setAll(IconLoader.icon("database", 12));
        dbIconHolder.getChildren().setAll(IconLoader.icon("database", 12));
    }

    // ------------------------------------------------------------------ navigation

    private void buildNavigation() {
        for (ViewId view : ViewId.values()) {
            ToggleButton button = new ToggleButton(view.title());
            button.getStyleClass().add("nav-button");
            button.setGraphic(IconLoader.icon(view.iconName(), 15));
            button.setMaxWidth(Double.MAX_VALUE);
            button.setToggleGroup(navGroup);
            button.setOnAction(event -> navigateTo(view));
            Notifications.tooltip(button, view.title());
            navButtons.put(view, button);
            navBox.getChildren().add(button);
        }
    }

    private void navigateTo(ViewId view) {
        if (view == activeView) {
            ToggleButton current = navButtons.get(view);
            if (current != null) {
                current.setSelected(true);
            }
            return;
        }
        RoutedView previous = activeView == null ? null : viewControllers.get(activeView);
        if (previous != null) {
            previous.onDeactivated();
        }

        Node node = viewCache.computeIfAbsent(view, this::loadView);
        RoutedView controller = viewControllers.get(view);
        contentHost.getChildren().setAll(node);
        activeView = view;

        ToggleButton button = navButtons.get(view);
        if (button != null) {
            button.setSelected(true);
        }
        viewTitleLabel.setText(view.title().toUpperCase());

        if (controller != null) {
            controller.onActivated();
            if (lastSnapshot != null) {
                controller.refresh(lastSnapshot);
            }
        }
        applyFilter(searchField.getText());
    }

    private Node loadView(ViewId view) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(view.fxmlPath()));
            loader.setControllerFactory(type -> instantiateController(type));
            Parent root = loader.load();
            Object controller = loader.getController();
            if (controller instanceof RoutedView routed) {
                viewControllers.put(view, routed);
            }
            return root;
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            Label failure = new Label("Unable to load " + view.title() + ": " + e);
            failure.getStyleClass().add("about-body");
            failure.setWrapText(true);
            return new StackPane(failure);
        }
    }

    private Object instantiateController(Class<?> type) {
        if (type == DashboardViewController.class) {
            return new DashboardViewController(tradingService, snapshotBuilder);
        }
        if (type == MarketWatchController.class) {
            return new MarketWatchController(tradingService);
        }
        if (type == SignalsController.class) {
            return new SignalsController();
        }
        if (type == PortfolioController.class) {
            return new PortfolioController();
        }
        if (type == OrdersController.class) {
            return new OrdersController(tradingService);
        }
        if (type == RiskController.class) {
            return new RiskController();
        }
        if (type == AnalyticsController.class) {
            return new AnalyticsController(snapshotBuilder);
        }
        if (type == SystemController.class) {
            return new SystemController();
        }
        if (type == AboutController.class) {
            return new AboutController(tradingService);
        }
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot instantiate controller " + type.getName(), e);
        }
    }

    // ------------------------------------------------------------------ symbols

    private void buildSymbolControls() {
        for (String symbol : tradingService.availableSymbols()) {
            CheckBox checkBox = new CheckBox(symbol);
            checkBox.setSelected(tradingService.selectedSymbols().contains(symbol));
            checkBox.selectedProperty().addListener((obs, was, selected) -> applySymbolSelection());
            Notifications.tooltip(checkBox, "Include " + symbol + " in the pipeline");
            symbolBoxes.put(symbol, checkBox);
            symbolChecks.getChildren().add(checkBox);
        }
    }

    private void applySymbolSelection() {
        Set<String> selected = new LinkedHashSet<>();
        symbolBoxes.forEach((symbol, box) -> {
            if (box.isSelected()) {
                selected.add(symbol);
            }
        });
        tradingService.selectSymbols(selected);
        snapshotBuilder.resetSession();
        statusMessage.setText("Instruments: " + String.join(", ", tradingService.selectedSymbols()));
    }

    // ------------------------------------------------------------------ actions

    @FXML
    private void onStart() {
        if (tradingService.isRunning() || starting) {
            return;
        }
        starting = true;
        startButton.setText("STARTING…");
        startButton.setDisable(true);
        statusMessage.setText("Starting simulation…");
        runStateLabel.setText("STARTING");
        runStateLabel.getStyleClass().removeAll("state-running", "state-stopped", "state-starting");
        runStateLabel.getStyleClass().add("state-starting");

        Platform.runLater(() -> {
            try {
                tradingService.start();
                statusMessage.setText("Simulation running");
            } catch (RuntimeException e) {
                statusMessage.setText("Failed to start simulation");
                Notifications.error("The simulation could not be started", e);
            } finally {
                starting = false;
                updateControls(tradingService.isRunning());
            }
        });
    }

    @FXML
    private void onStop() {
        try {
            tradingService.stop();
            statusMessage.setText("Simulation stopped");
        } catch (RuntimeException e) {
            statusMessage.setText("Failed to stop simulation");
            Notifications.error("The simulation could not be stopped", e);
        }
        updateControls(tradingService.isRunning());
    }

    @FXML
    private void onReset() {
        boolean confirmed = Notifications.confirm(
                "Reset simulation state?",
                "This clears the portfolio, order book, signals and metrics. Market data will restart "
                        + "from the current price levels. This cannot be undone.");
        if (!confirmed) {
            statusMessage.setText("Reset cancelled");
            return;
        }
        tradingService.reset();
        snapshotBuilder.resetSession();
        statusMessage.setText("State reset");
        tick();
    }

    // ------------------------------------------------------------------ refresh

    private void tick() {
        clockLabel.setText(LocalTime.now().format(CLOCK) + "  " + LocalDate.now().format(STAMP));

        ViewSnapshot snapshot;
        try {
            snapshot = snapshotBuilder.build();
        } catch (RuntimeException e) {
            statusMessage.setText("Snapshot failed: " + e.getMessage());
            return;
        }
        lastSnapshot = snapshot;

        updateHeader(snapshot);
        updateStatusBar(snapshot);

        RoutedView active = activeView == null ? null : viewControllers.get(activeView);
        if (active != null) {
            try {
                active.refresh(snapshot);
            } catch (RuntimeException e) {
                statusMessage.setText("View refresh failed: " + e.getMessage());
            }
        }
    }

    private void updateHeader(ViewSnapshot snapshot) {
        boolean running = snapshot.running();
        if (!starting) {
            runStateLabel.setText(running ? "RUNNING" : "STOPPED");
            runStateLabel.getStyleClass().removeAll("state-running", "state-stopped", "state-starting");
            runStateLabel.getStyleClass().add(running ? "state-running" : "state-stopped");
            updateControls(running);
        }
        dbBadgeLabel.setText(snapshot.databaseStatus());
        dbStatusLabel.setText(snapshot.databaseStatus());
    }

    private void updateControls(boolean running) {
        startButton.setDisable(running || starting);
        stopButton.setDisable(!running);
        startButton.setText(starting ? "STARTING…" : "START");
    }

    private void updateStatusBar(ViewSnapshot snapshot) {
        ViewSnapshot.SystemStats stats = snapshot.system();
        statusEvents.setText("events " + stats.eventsProcessed());
        statusQueue.setText("queue " + stats.queueDepth());
        statusWorkers.setText("workers " + stats.activeWorkers());
        statusLatency.setText("latency " + stats.averageLatency());
    }

    private void setDatabaseIndicator() {
        dbStatusLabel.setText(tradingService.databaseStatus());
        dbBadgeLabel.setText(tradingService.databaseStatus());
    }

    private void applyFilter(String query) {
        RoutedView active = activeView == null ? null : viewControllers.get(activeView);
        if (active instanceof Filterable filterable) {
            filterable.applyFilter(query);
        }
    }

    public void shutdown() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
        if (activeView != null) {
            RoutedView active = viewControllers.get(activeView);
            if (active != null) {
                active.onDeactivated();
            }
        }
        tradingService.stop();
    }

    /** Implemented by views that can narrow their rows by a search term. */
    public interface Filterable {
        void applyFilter(String query);
    }
}
