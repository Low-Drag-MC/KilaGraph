package com.lowdragmc.kilagraph.graph.exec;

/**
 * Sentinel thrown by {@code BreakNode}. The nearest enclosing loop's {@code execute(...)} catches
 * it; outer loops let it propagate. Stack trace is suppressed — this is control flow, not an
 * error.
 */
public final class BreakException extends RuntimeException {
    public static final BreakException INSTANCE = new BreakException();

    private BreakException() {
        super(null, null, /*enableSuppression*/ false, /*writableStackTrace*/ false);
    }
}
