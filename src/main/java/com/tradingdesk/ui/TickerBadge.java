package com.tradingdesk.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.util.Locale;

/**
 * Clean monogram badges used as company marks beside ticker symbols.
 *
 * <p>These are deliberately <em>badges</em> (initial + a per-symbol accent colour), not
 * company logos: reproducing corporate trademarks would require licensed artwork, which this
 * project does not ship. The colour assignment is deterministic, so a symbol keeps its colour
 * for the whole session and across restarts.
 */
public final class TickerBadge {

    private static final Color[] PALETTE = {
            Color.web("#2f8cff"),
            Color.web("#22b8a6"),
            Color.web("#a06bf5"),
            Color.web("#e2894a"),
            Color.web("#e0575f"),
            Color.web("#3f9c6d"),
            Color.web("#c9a227"),
            Color.web("#4a7fd4")
    };

    public static final double DEFAULT_SIZE = 24.0;

    private TickerBadge() {
    }

    /** Deterministic accent colour for a symbol, stable across JVM runs. */
    public static Color colorFor(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return PALETTE[0];
        }
        int hash = 0;
        for (char c : symbol.toUpperCase(Locale.ROOT).toCharArray()) {
            hash = (hash * 31 + c) & 0x7fffffff;
        }
        return PALETTE[hash % PALETTE.length];
    }

    /** Initial(s) shown inside the badge. */
    public static String monogram(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "?";
        }
        String upper = symbol.toUpperCase(Locale.ROOT);
        int dash = upper.indexOf('-');
        if (dash > 0) {
            upper = upper.substring(0, dash);
        }
        return upper.substring(0, 1);
    }

    public static StackPane of(String symbol) {
        return of(symbol, DEFAULT_SIZE);
    }

    public static StackPane of(String symbol, double size) {
        Color accent = colorFor(symbol);

        Label monogram = new Label(monogram(symbol));
        monogram.getStyleClass().add("ticker-badge-text");
        monogram.setStyle("-fx-text-fill: " + toHex(accent.brighter()) + ";");

        StackPane badge = new StackPane(monogram);
        badge.setAlignment(Pos.CENTER);
        badge.setMinSize(size, size);
        badge.setPrefSize(size, size);
        badge.setMaxSize(size, size);
        badge.getStyleClass().add("ticker-badge");
        badge.setStyle(
                "-fx-background-color: " + toHex(accent.deriveColor(0, 1, 1, 0.20)) + ";"
                        + "-fx-border-color: " + toHex(accent.deriveColor(0, 1, 1, 0.55)) + ";"
                        + "-fx-border-width: 1;"
                        + "-fx-border-radius: " + (size * 0.30) + ";"
                        + "-fx-background-radius: " + (size * 0.30) + ";");
        return badge;
    }

    private static String toHex(Color color) {
        return String.format(Locale.ROOT, "#%02x%02x%02x",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }
}
