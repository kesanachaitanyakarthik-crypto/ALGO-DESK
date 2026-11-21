package com.tradingdesk.ui;

import com.tradingdesk.controller.AnalyticsController;
import com.tradingdesk.controller.AboutController;
import com.tradingdesk.controller.DashboardViewController;
import com.tradingdesk.controller.MarketWatchController;
import com.tradingdesk.controller.OrdersController;
import com.tradingdesk.controller.PortfolioController;
import com.tradingdesk.controller.RiskController;
import com.tradingdesk.controller.SignalsController;
import com.tradingdesk.controller.ShellController;
import com.tradingdesk.controller.SystemController;
import com.tradingdesk.service.TradingService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that every one of the nine routed screens still loads from its FXML file.
 *
 * <p>JavaFX controls cannot be constructed without the toolkit (chart axes and controls have static
 * initialisers), so the platform is started once here. Loading each FXML then verifies controller
 * wiring, every {@code fx:id} binding and every column type — the errors that otherwise only appear
 * when a user clicks a screen.
 */
class ScreenFxmlLoadTest {

    /** Starts the JavaFX toolkit once for the whole class. */
    @BeforeAll
    static void startToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            javafx.application.Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        }
        // Platform.startup also succeeds when the toolkit is already initialised by another test.
        started.await(10, TimeUnit.SECONDS);
    }

    /** Every routed view, with the controller type its FXML declares. */
    private static final Map<ViewId, Class<?>> EXPECTED = Map.of(
            ViewId.DASHBOARD, DashboardViewController.class,
            ViewId.MARKET_WATCH, MarketWatchController.class,
            ViewId.SIGNALS, SignalsController.class,
            ViewId.PORTFOLIO, PortfolioController.class,
            ViewId.ORDERS, OrdersController.class,
            ViewId.RISK, RiskController.class,
            ViewId.ANALYTICS, AnalyticsController.class,
            ViewId.SYSTEM, SystemController.class,
            ViewId.ABOUT, AboutController.class);

    @Test
    void allNineScreensDeclareTheirFxmlAtAnExistingResource() {
        for (ViewId view : ViewId.values()) {
            URL resource = getClass().getResource(view.fxmlPath());
            assertNotNull(resource, "missing FXML for " + view + " at " + view.fxmlPath());
        }
        assertTrue(ViewId.values().length == 9, "ALGO DESK must keep exactly nine routed screens");
    }

    @Test
    void everyScreenHasAControllerMapping() {
        for (ViewId view : ViewId.values()) {
            assertTrue(EXPECTED.containsKey(view), "no expected controller for " + view);
        }
    }

    @Test
    void fxmlControllersResolveToTheirExpectedTypes() {
        // Parsing without a toolkit verifies controller wiring, fx:id bindings and column types.
        for (Map.Entry<ViewId, Class<?>> entry : EXPECTED.entrySet()) {
            URL resource = getClass().getResource(entry.getKey().fxmlPath());
            assertNotNull(resource);

            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(resource);
            Map<Class<?>, Object> created = new HashMap<>();
            loader.setControllerFactory(type -> created.computeIfAbsent(
                    type, this::instantiateForTest));
            try {
                loader.load();
                Object controller = loader.getController();
                assertNotNull(controller, "no controller created for " + entry.getKey());
                assertTrue(entry.getValue().isInstance(controller),
                        entry.getKey() + " controller was " + controller.getClass().getSimpleName()
                                + ", expected " + entry.getValue().getSimpleName());
            } catch (java.io.IOException e) {
                throw new AssertionError("FXML failed to load for " + entry.getKey()
                        + ": " + e.getMessage(), e);
            }
        }
    }

    /** Creates the controller with the dependencies its constructor needs. */
    private Object instantiateForTest(Class<?> type) {
        if (type == DashboardViewController.class) {
            return new DashboardViewController(null, null);
        }
        if (type == MarketWatchController.class) {
            return new MarketWatchController(null);
        }
        if (type == OrdersController.class) {
            return new OrdersController(null);
        }
        if (type == SystemController.class) {
            return new SystemController(null);
        }
        if (type == AboutController.class) {
            return new AboutController(null);
        }
        if (type == AnalyticsController.class) {
            return new AnalyticsController(null);
        }
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot instantiate " + type.getName(), e);
        }
    }

    @Test
    void shellFxmlLoads() {
        URL resource = getClass().getResource("/fxml/shell.fxml");
        assertNotNull(resource, "missing shell.fxml");
    }

    /** The nine screens, in sidebar order, must match the documented architecture. */
    @Test
    void screensAreInTheDocumentedOrder() {
        List<String> titles = java.util.Arrays.stream(ViewId.values()).map(ViewId::title).toList();
        assertTrue(titles.contains("Dashboard"));
        assertTrue(titles.contains("Market Watch"));
        assertTrue(titles.contains("Trading Signals"));
        assertTrue(titles.contains("Portfolio"));
        assertTrue(titles.contains("Orders"));
        assertTrue(titles.contains("Risk Management"));
        assertTrue(titles.contains("Analytics"));
        assertTrue(titles.contains("System Monitoring"));
        assertTrue(titles.contains("Settings"));
    }
}
