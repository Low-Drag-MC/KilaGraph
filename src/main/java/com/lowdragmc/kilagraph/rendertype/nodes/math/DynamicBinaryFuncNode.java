package com.lowdragmc.kilagraph.rendertype.nodes.math;

/**
 * A {@link DynamicBinaryNode} whose result is a two-argument GLSL builtin call ({@code func(a, b)}) —
 * for vecN-overloaded builtins like {@code pow}/{@code min}/{@code max}/{@code mod}/{@code step}/{@code
 * atan}. Subclasses only supply {@link #glslFunc()}.
 */
public abstract class DynamicBinaryFuncNode extends DynamicBinaryNode {

    /** The two-argument GLSL builtin, e.g. {@code "pow"} (must be overloaded for vecN). */
    protected abstract String glslFunc();

    @Override
    protected String emit(String a, String b) {
        return glslFunc() + "(" + a + ", " + b + ")";
    }
}
