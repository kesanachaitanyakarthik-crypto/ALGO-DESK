package com.tradingdesk.ui;

/**
 * Contract implemented by every routed screen controller.
 *
 * <p>The shell owns a single refresh timeline and pushes an immutable snapshot into whichever
 * view is currently visible, so inactive views cost nothing and no view owns a timer of its own.
 */
public interface RoutedView {

    /** Called when this view becomes the visible one. */
    default void onActivated() {
    }

    /** Called on every shell tick while this view is visible. */
    void refresh(ViewSnapshot snapshot);

    /** Called when this view is replaced. */
    default void onDeactivated() {
    }

    /** Title shown in the header for this view. */
    default String title() {
        return "";
    }
}
