package com.tradingdesk.exchange;

/**
 * Thrown when live trading is attempted without the required explicit authorization.
 *
 * <p>A distinct type so callers — and tests — can prove that the guard, rather than some unrelated
 * failure, prevented the operation.
 */
public class LiveTradingDisabledException extends RuntimeException {

    public LiveTradingDisabledException(String message) {
        super(message);
    }
}
