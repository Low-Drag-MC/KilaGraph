package com.lowdragmc.kilagraph.rendertype.compiler;

import com.lowdragmc.kilagraph.rendertype.preview.ShaderPreviewSupport;
import com.lowdragmc.kilagraph.graph.util.NodeTooltipHelper;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.node.NodePreviewContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for all RenderTypeGraph shader value nodes. Beyond declaring ports
 * ({@code onDefinePorts}), a shader node knows how to emit GLSL for its outputs via
 * {@link #compile(ShaderCompileContext)}.
 *
 * <p>The compiler ({@link ShaderGraphCompiler}) drives compilation backward (demand-driven, like
 * {@code GraphExecutor}): when an output of this node is needed, {@code compile} is invoked once.
 * Read inputs with {@link ShaderCompileContext#input(String)} and publish outputs with
 * {@link ShaderCompileContext#output(String, ShaderExpr)}. The compiler hoists each non-opaque
 * output into a temp variable so an expression is only evaluated once even if consumed many times.</p>
 *
 * <p>Nodes that return an output port id from {@link #previewOutputPortId()} get a live Unity-style
 * thumbnail of that output in the editor (compiled via {@link ShaderGraphCompiler#compilePreview}).</p>
 */
public abstract class ShaderNode extends Node {

    @Override
    public void setImplementation(NodeModel nodeModel) {
        super.setImplementation(nodeModel);
        NodeTooltipHelper.apply(nodeModel, getNodeTooltip());
    }

    protected @Nullable Component getNodeTooltip() {
        return null;
    }

    /**
     * Emit GLSL for this node's outputs. Called at most once per compilation pass. Implementations
     * read inputs via {@code ctx.input(id)}, build GLSL expressions, and publish results via
     * {@code ctx.output(id, expr)}. Side declarations (includes, uniforms, samplers) go through the
     * matching {@code ctx} helpers.
     */
    public abstract void compile(ShaderCompileContext ctx);

    @Override
    public Component getDisplayName() {
        return NodeDisplayNames.fromAttribute(this);
    }

    /**
     * Which stage(s) this node may be compiled into. Default {@link StageAffinity#ANY}. Override to
     * {@code VERTEX_ONLY}/{@code FRAGMENT_ONLY} for nodes that read stage-specific data (e.g. a vertex
     * attribute). The compiler flags a {@link StageError} when a node is pulled into a forbidden stage.
     */
    public StageAffinity stageAffinity() {
        return StageAffinity.ANY;
    }

    /** The output port id to render a live preview for, or {@code null} (default) for no preview. */
    @Nullable
    protected String previewOutputPortId() {
        return null;
    }

    /**
     * The {@link com.lowdragmc.kilagraph.rendertype.preview.KGPreviewContents} key of the geometry this
     * node's preview should default to (e.g. {@code "sphere"} for a Fresnel/normal-based effect that's
     * invisible on a flat quad), or {@code null} for the default flat quad. The user can still switch it
     * via right-click — this only sets the initial geometry.
     */
    @Nullable
    protected String defaultPreviewContentKey() {
        return null;
    }

    @Override
    public boolean hasNodePreview() {
        // The per-node preview renders the port's value on a flat fragment quad. A VERTEX_ONLY node
        // (e.g. a vertex attribute like Position/Normal) has no meaning in the fragment stage, so
        // compiling its preview would both fail and spuriously record a stage error — skip it.
        return previewOutputPortId() != null && stageAffinity() != StageAffinity.VERTEX_ONLY;
    }

    @Override
    public void onBuildNodePreview(NodePreviewContext context) {
        // Pass the id as a supplier so the preview re-resolves the (recreatable) port each frame — a node
        // that redefines its outputs (Expression) would otherwise freeze on a stale PortModel.
        ShaderPreviewSupport.build(context, this::previewOutputPortId, defaultPreviewContentKey());
    }
}
