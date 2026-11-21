package com.tradingdesk.utils;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Logging {

    private static final Logger ROOT = Logger.getLogger("com.tradingdesk");

    private Logging() {
    }

    public static void configure(Level level) {
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(level);
        handler.setFormatter(new java.util.logging.SimpleFormatter());
        ROOT.setLevel(level);
        ROOT.addHandler(handler);
        ROOT.setUseParentHandlers(false);
    }

    public static Logger logger(String name) {
        return Logger.getLogger(name);
    }
}
