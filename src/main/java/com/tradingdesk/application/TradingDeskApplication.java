package com.tradingdesk.application;

import com.tradingdesk.controller.ShellController;
import com.tradingdesk.ui.Branding;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class TradingDeskApplication extends Application {

    private ApplicationContext context;
    private ShellController shellController;

    @Override
    public void start(Stage stage) throws Exception {
        this.context = new ApplicationContext(true);

        FXMLLoader loader = new FXMLLoader(TradingDeskApplication.class.getResource("/fxml/shell.fxml"));
        loader.setControllerFactory(type -> {
            if (type == ShellController.class) {
                this.shellController = new ShellController(context.tradingService());
                return shellController;
            }
            throw new IllegalStateException("Unsupported controller: " + type);
        });

        Parent root = loader.load();
        Scene scene = new Scene(root, 1366, 768);
        scene.getStylesheets().add(
                TradingDeskApplication.class.getResource("/css/theme.css").toExternalForm());

        stage.setTitle(Branding.WINDOW_TITLE);
        stage.setScene(scene);
        stage.setMinWidth(1180);
        stage.setMinHeight(700);
        stage.getIcons().add(Branding.windowIcon(64));
        stage.getIcons().add(Branding.windowIcon(32));
        stage.show();

        // Connect the live market data source on a background thread (the WebSocket handshake can
        // block for up to 10 s), so the window stays responsive and the audit strip can show the
        // real provenance as soon as it is known.
        context.startMarketDataAsync();
    }

    @Override
    public void stop() {
        if (shellController != null) {
            shellController.shutdown();
        }
        if (context != null) {
            context.close();
        }
    }
}
