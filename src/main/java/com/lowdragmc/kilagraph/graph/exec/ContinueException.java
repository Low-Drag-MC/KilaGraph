package com.lowdragmc.kilagraph.graph.exec;

/**
 * Sentinel thrown by {@code ContinueNode}. Caught by the nearest enclosing loop to skip to the
 * next iteration. Stack trace is suppressed.
 */
public final class ContinueException extends RuntimeException {
    public static final ContinueException INSTANCE = new ContinueException();

    private ContinueException() {
        super(null, null, /*enableSuppression*/ false, /*writableStackTrace*/ false);
    }
}
