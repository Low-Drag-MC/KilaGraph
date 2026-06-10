package com.lowdragmc.kilagraph.rendertype.compiler;

import com.lowdragmc.kilagraph.rendertype.preview.ShaderPreviewSupport;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.BlockNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.node.NodePreviewContext;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for block nodes that live inside a stage context ({@code VertexStageNode} /
 * {@code FragmentStageNode}). Block nodes do not produce ordinary output ports consumed by the
 * pull graph in the usual way; instead they contribute to their stage's semantic outputs.
 *
 * <ul>
 *   <li>Vertex blocks additionally implement {@link IVaryingBlock}: their (optional) output port is
 *       a varying boundary — its value is computed in the vertex shader and read in the fragment
 *       shader.</li>
 *   <li>Fragment blocks implement {@link IFragmentOutputBlock}: they read their inputs and write
 *       into the fragment stage's accumulated outputs (base color / alpha / emission / discard).</li>
 * </ul>
 *
 * <p>A block that returns an output port id from {@link #previewOutputPortId()} gets a live preview
 * thumbnail of that output (e.g. a {@code TexCoord} varying block previews the uv gradient).</p>
 */
public abstract class ShaderBlockNode extends BlockNode {

    @Override
    public Component getDisplayName() {
        return NodeDisplayNames.fromAttribute(this);
    }

    /** The output port id to render a live preview for, or {@code null} (default) for no preview. */
    @Nullable
    protected String previewOutputPortId() {
        return null;
    }

    @Override
    public boolean hasNodePreview() {
        return previewOutputPortId() != null;
    }

    @Override
    public void onBuildNodePreview(NodePreviewContext context) {
        ShaderPreviewSupport.build(context, previewOutputPortId());
    }
}
