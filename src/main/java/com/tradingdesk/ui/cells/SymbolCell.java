package com.tradingdesk.ui.cells;

import com.tradingdesk.ui.TickerBadge;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.layout.HBox;

/**
 * Renders a ticker symbol as its monogram badge followed by the symbol text.
 * Generic in the row type so it can be reused by any table whose symbol column holds a ticker.
 */
public class SymbolCell<S> extends TableCell<S, String> {

    private final HBox container = new HBox(8);
    private final Label symbolLabel = new Label();

    public SymbolCell() {
        container.setAlignment(Pos.CENTER_LEFT);
        symbolLabel.getStyleClass().add("symbol-cell-text");
        container.getChildren().add(symbolLabel);
    }

    @Override
    protected void updateItem(String symbol, boolean empty) {
        super.updateItem(symbol, empty);
        setText(null);
        if (empty || symbol == null || symbol.isBlank()) {
            setGraphic(null);
            return;
        }
        symbolLabel.setText(symbol);
        container.getChildren().setAll(TickerBadge.of(symbol), symbolLabel);
        setGraphic(container);
    }
}
