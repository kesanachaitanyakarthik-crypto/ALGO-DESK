package com.tradingdesk.ui.cells;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.layout.StackPane;

import java.util.Locale;

/**
 * Renders an enum-like status value as a colour-coded chip.
 *
 * <p>The style class is derived from the value itself, so any new status gets a consistent
 * neutral chip rather than needing a code change.
 */
public class StatusChipCell<S> extends TableCell<S, String> {

    private final Label chip = new Label();
    private final StackPane holder = new StackPane(chip);

    public StatusChipCell() {
        holder.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("status-chip");
    }

    @Override
    protected void updateItem(String status, boolean empty) {
        super.updateItem(status, empty);
        setText(null);
        if (empty || status == null || status.isBlank()) {
            setGraphic(null);
            return;
        }
        chip.setText(status);
        chip.getStyleClass().removeIf(cls -> cls.startsWith("chip-"));
        chip.getStyleClass().add("chip-" + status.toLowerCase(Locale.ROOT).replace('_', '-'));
        setGraphic(holder);
    }
}
