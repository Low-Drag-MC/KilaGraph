package com.lowdragmc.kilagraph.rendertype.nodes.channel;

import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicBinaryNode;

/** Shared GLSL helpers for the Channel nodes (Split / Swizzle). */
public final class ChannelGlsl {
    private ChannelGlsl() {}

    /**
     * Pad a dynamic float-vector expression up to a {@code vec4}, filling missing channels with {@code 0}
     * (so {@code .x/.y/.z/.w} swizzles never reference a component the value lacks — Unity's "missing
     * channels return 0"). A scalar fills only {@code x}; a {@code vec4} is returned unchanged.
     */
    public static String toVec4(ShaderExpr in) {
        String code = in.code();
        return switch (DynamicBinaryNode.components(in)) {
            case 1 -> "vec4(" + code + ", 0.0, 0.0, 0.0)";
            case 2 -> "vec4(" + code + ", 0.0, 0.0)";
            case 3 -> "vec4(" + code + ", 0.0)";
            default -> code; // already vec4
        };
    }
}
