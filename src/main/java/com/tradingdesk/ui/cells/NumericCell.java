package com.tradingdesk.ui.cells;

import javafx.geometry.Pos;
import javafx.scene.control.TableCell;

/** Right-aligned monospaced cell for prices, quantities and counters. */
public class NumericCell<S> extends TableCell<S, String> {

    public NumericCell() {
        setAlignment(Pos.CENTER_RIGHT);
        getStyleClass().add("numeric-cell");
    }

    @Override
    protected void updateItem(String value, boolean empty) {
        super.updateItem(value, empty);
        setText(empty || value == null ? null : value);
    }
}
