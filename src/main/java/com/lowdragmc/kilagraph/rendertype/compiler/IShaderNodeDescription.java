package com.lowdragmc.kilagraph.rendertype.compiler;

import com.lowdragmc.kilagraph.graph.util.INodeDescription;
import com.lowdragmc.kilagraph.graph.util.NodeDescriptions;
import org.jetbrains.annotations.Nullable;

/**
 * The shader graph's face of {@link INodeDescription}: the same hooks, with the code slot named for the
 * language it actually holds. Implemented by the three unrelated hierarchies that make up the shader
 * graph ({@link ShaderNode}, {@link ShaderBlockNode} and the stage {@code ContextNode}s) — the same split
 * {@link NodeDisplayNames} exists for.
 */
public interface IShaderNodeDescription extends INodeDescription {

    /**
     * The GLSL this node compiles to, shown as a syntax-highlighted card. Keep lines short (~48 chars);
     * the panel is narrow. Nodes whose GLSL is built from a single expression should derive this from the
     * same method the compiler uses, so the example can never drift from the emitted code.
     *
     * @return the snippet, or {@code null} for no GLSL section
     */
    @Nullable
    default String glslExample() {
        return null;
    }

    /** {@link NodeDescriptions} reads the generic slot; for a shader node that is its GLSL. */
    @Override
    @Nullable
    default String codeExample() {
        return glslExample();
    }
}
