package com.lowdragmc.kilagraph.rendertype.nodes.transform;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Exposes KilaGraph's {@code KG_Transforms} uniform block — the space-conversion matrices we precompute
 * each draw (see {@link com.lowdragmc.kilagraph.rendertype.runtime.KGTransformUniforms}). Lets a graph do
 * its own space math without the {@code Transform} node: {@code IModelViewMat} (view→object), {@code
 * ViewMat} (world→view rotation), {@code IViewMat} (view→world), {@code IProjMat} (clip→view). Referencing
 * any output flags the pipeline to declare + bind the block.
 *
 * <p>The world camera <em>position</em> is not here — use the {@code Camera} node, which reads
 * {@code globals.glsl}'s precision-split form ({@code CameraBlockPos} + {@code CameraOffset}).</p>
 */
@NodeAttribute(name = "rt_kg_transforms_ubo", group = "rendertype_transform", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class KGTransformsUboNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_kg_transforms_ubo.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("IModelViewMat", RenderTypeGraphTypes.MAT4);
        context.addOutputPort("ViewMat", RenderTypeGraphTypes.MAT4);
        context.addOutputPort("IViewMat", RenderTypeGraphTypes.MAT4);
        context.addOutputPort("IProjMat", RenderTypeGraphTypes.MAT4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.output("IModelViewMat", ctx.transformField("IModelViewMat", GlslType.MAT4));
        ctx.output("ViewMat", ctx.transformField("ViewMat", GlslType.MAT4));
        ctx.output("IViewMat", ctx.transformField("IViewMat", GlslType.MAT4));
        ctx.output("IProjMat", ctx.transformField("IProjMat", GlslType.MAT4));
    }
}
