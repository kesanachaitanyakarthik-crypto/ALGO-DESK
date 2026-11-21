package com.tradingdesk.ui;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.util.Duration;

import java.util.Optional;

/**
 * Small helpers for tooltips, alerts and confirmation dialogs, so styling and behaviour stay
 * consistent across views.
 */
public final class Notifications {

    private static final String STYLESHEET = "/css/theme.css";

    private Notifications() {
    }

    public static void tooltip(Node node, String text) {
        if (node == null || text == null || text.isBlank()) {
            return;
        }
        Tooltip tooltip = new Tooltip(text);
        tooltip.getStyleClass().add("td-tooltip");
        tooltip.setShowDelay(Duration.millis(320));
        tooltip.setHideDelay(Duration.millis(80));
        Tooltip.install(node, tooltip);
    }

    /** Non-blocking error dialog used when a control action fails unexpectedly. */
    public static void error(String header, Throwable failure) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(Branding.APP_NAME);
        alert.setHeaderText(header);
        alert.setContentText(failure == null
                ? "An unexpected error occurred."
                : failure.getMessage() == null ? failure.toString() : failure.getMessage());
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        applyStyles(alert);
        alert.showAndWait();
    }

    /** Destructive-action confirmation. Returns true only on explicit OK. */
    public static boolean confirm(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(Branding.APP_NAME);
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        applyStyles(alert);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private static void applyStyles(Alert alert) {
        var location = Notifications.class.getResource(STYLESHEET);
        if (location != null) {
            alert.getDialogPane().getStylesheets().add(location.toExternalForm());
            alert.getDialogPane().getStyleClass().add("td-dialog");
        }
        Label header = new Label(alert.getHeaderText());
        header.setWrapText(true);
        alert.getDialogPane().setHeader(header);
    }
}
