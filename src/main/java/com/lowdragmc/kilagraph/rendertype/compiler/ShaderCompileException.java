package com.lowdragmc.kilagraph.rendertype.compiler;

/** Thrown when a {@link RenderTypeGraph} cannot be compiled into valid shader sources. */
public class ShaderCompileException extends RuntimeException {
    public ShaderCompileException(String message) {
        super(message);
    }

    public ShaderCompileException(String message, Throwable cause) {
        super(message, cause);
    }
}
