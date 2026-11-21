package com.tradingdesk.ui.cells;

import javafx.geometry.Pos;
import javafx.scene.control.TableCell;

/**
 * Right-aligned numeric cell that colours positive values green and negative values red.
 * The value is supplied as a pre-formatted string; the sign is read from the leading character.
 */
public class ChangeCell<S> extends TableCell<S, String> {

    public ChangeCell() {
        setAlignment(Pos.CENTER_RIGHT);
    }

    @Override
    protected void updateItem(String value, boolean empty) {
        super.updateItem(value, empty);
        getStyleClass().removeAll("value-up", "value-down", "value-flat");
        if (empty || value == null || value.isBlank()) {
            setText(null);
            return;
        }
        setText(value);
        getStyleClass().add(classify(value));
    }

    static String classify(String value) {
        String trimmed = value.trim();
        boolean zero = trimmed.replace("+", "").replace("-", "")
                .replace("%", "").replace(",", "").trim()
                .matches("0*(\\.0*)?");
        if (zero) {
            return "value-flat";
        }
        if (trimmed.startsWith("+")) {
            return "value-up";
        }
        if (trimmed.startsWith("-")) {
            return "value-down";
        }
        return "value-flat";
    }
}
