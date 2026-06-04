package com.lowdragmc.kilagraph.graph.exec;

public class TypeMismatchException extends RuntimeException {
    public TypeMismatchException(String message) {
        super(message);
    }

    public TypeMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
