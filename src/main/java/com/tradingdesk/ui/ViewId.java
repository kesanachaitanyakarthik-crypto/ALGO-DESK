package com.tradingdesk.ui;

import java.util.Locale;

/**
 * The routed screens of ALGO DESK, in sidebar order.
 */
public enum ViewId {

    DASHBOARD("Dashboard", "grid", "/fxml/dashboard-view.fxml"),
    MARKET_WATCH("Market Watch", "chart-line", "/fxml/market-watch.fxml"),
    SIGNALS("Trading Signals", "activity", "/fxml/signals-view.fxml"),
    PORTFOLIO("Portfolio", "wallet", "/fxml/portfolio-view.fxml"),
    ORDERS("Orders", "clipboard", "/fxml/orders-view.fxml"),
    RISK("Risk Management", "shield", "/fxml/risk-view.fxml"),
    ANALYTICS("Analytics", "bar-chart", "/fxml/analytics-view.fxml"),
    SYSTEM("System Monitoring", "cpu", "/fxml/system-view.fxml"),
    ABOUT("Settings", "gear", "/fxml/about-view.fxml");

    private final String title;
    private final String icon;
    private final String fxml;

    ViewId(String title, String icon, String fxml) {
        this.title = title;
        this.icon = icon;
        this.fxml = fxml;
    }

    public String title() {
        return title;
    }

    public String iconName() {
        return icon;
    }

    public String fxmlPath() {
        return fxml;
    }

    public String cssTag() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
