package com.lowdragmc.kilagraph.rendertype.nodes.artistic;

import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;

/**
 * Shared base for Unity-style Artistic nodes (colour adjustment, blend, masks, normals, …). They all
 * expose a live preview of their single {@code out} output. Concrete nodes declare their ports and emit
 * the GLSL; colour nodes operate on {@code vec3} (compose alpha separately).
 */
public abstract class ArtisticNode extends ShaderNode {
    @Override
    protected String previewOutputPortId() {
        return "out";
    }
}
