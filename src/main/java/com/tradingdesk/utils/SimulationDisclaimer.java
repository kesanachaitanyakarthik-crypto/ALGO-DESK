package com.tradingdesk.utils;

/**
 * The permanent educational-only disclaimer shown in the UI.
 *
 * <p>ALGO DESK ingests <em>real</em> public market data but every transaction is virtual. This text
 * states both facts so the screen can never imply real trading.
 */
public final class SimulationDisclaimer {

    public static final String TEXT =
            "EDUCATIONAL SIMULATION ONLY \u2014 REAL MARKET DATA, VIRTUAL TRADING. "
                    + "NO REAL-MONEY TRANSACTIONS.";

    /** Shorter form for constrained spaces such as the header badge. */
    public static final String SHORT = "EDUCATIONAL SIMULATION ONLY \u2014 VIRTUAL TRADING";

    private SimulationDisclaimer() {
    }
}
