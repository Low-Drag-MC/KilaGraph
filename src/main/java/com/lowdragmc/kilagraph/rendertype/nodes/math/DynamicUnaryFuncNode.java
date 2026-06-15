package com.lowdragmc.kilagraph.rendertype.nodes.math;

/**
 * A {@link DynamicUnaryNode} whose result is a one-argument GLSL builtin call ({@code func(a)}) — for
 * vecN-overloaded builtins like {@code sin}/{@code abs}/{@code floor}/{@code exp}. Subclasses only
 * supply {@link #glslFunc()}.
 */
public abstract class DynamicUnaryFuncNode extends DynamicUnaryNode {

    /** The one-argument GLSL builtin, e.g. {@code "sin"} (must be overloaded for vecN). */
    protected abstract String glslFunc();

    @Override
    protected String emit(String a) {
        return glslFunc() + "(" + a + ")";
    }
}
