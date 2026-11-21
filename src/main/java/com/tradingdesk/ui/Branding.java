package com.tradingdesk.ui;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.SVGPath;

/**
 * ALGO DESK identity constants and vector brand artwork.
 */
public final class Branding {

    public static final String APP_NAME = "ALGO DESK";
    public static final String APP_SUBTITLE = "High-Frequency Algorithmic Trading System";
    public static final String VERSION = "1.0.0";
    public static final String WINDOW_TITLE = APP_NAME + " — " + APP_SUBTITLE;

    /** Electric blue accent. */
    public static final Color ACCENT = Color.web("#2f8cff");
    public static final Color ACCENT_BRIGHT = Color.web("#5cb0ff");
    public static final Color ACCENT_DEEP = Color.web("#1b5fc4");
    public static final Color NAVY = Color.web("#070d17");

    private Branding() {
    }

    /** Gradient used for the brand mark and the larger wordmark. */
    public static LinearGradient brandGradient() {
        return new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, ACCENT_BRIGHT),
                new Stop(0.55, ACCENT),
                new Stop(1, ACCENT_DEEP));
    }

    /** The glowing mark used inside the sidebar and About screen. */
    public static StackPane mark(double size) {
        SVGPath shape = IconLoader.logo(size);
        shape.setFill(brandGradient());

        StackPane holder = new StackPane(shape);
        holder.setMinSize(size, size);
        holder.setPrefSize(size, size);
        holder.setMaxSize(size, size);
        holder.getStyleClass().add("brand-mark-holder");
        return holder;
    }

    /**
     * Renders the mark to an {@link Image} for the OS window icon.
     *
     * <p>The node is snapshotted rather than loading a bitmap so the icon is generated from
     * the same vector source at any resolution. Must be called on the JavaFX thread.
     */
    public static Image windowIcon(double size) {
        StackPane holder = mark(size);
        holder.setStyle("-fx-background-color: #070d17;");
        holder.applyCss();
        holder.layout();
        return holder.snapshot(null, new WritableImage((int) size, (int) size));
    }
}
