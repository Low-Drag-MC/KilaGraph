package com.lowdragmc.kilagraph.rendertype.nodes.math.vector;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * {@code pow(1 - saturate(dot(normalize(normal), normalize(viewDir))), power)}: the Fresnel term — a
 * rim factor that is brightest where the surface faces away from the view direction.
 *
 * <p>Like Unity, {@code normal} and {@code viewDir} are optional: an unconnected port falls back to the
 * mesh's interpolated world-space normal / view direction ({@link ShaderCompileContext#meshNormal()} /
 * {@link ShaderCompileContext#meshViewDir()}), so the node produces a rim out of the box.</p>
 */
@NodeAttribute(name = "rt_fresnel", group = "rendertype_math/vector", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class FresnelNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("normal", RenderTypeGraphTypes.VEC3).withoutConfigurator();
        context.addInputPort("viewDir", RenderTypeGraphTypes.VEC3).withoutConfigurator();
        context.addInputPort("power", TypeHandles.FLOAT).withDefaultValue(1f);
        context.addOutputPort("out", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String n = (ctx.isConnected("normal") ? ctx.input("normal") : ctx.meshNormal()).code();
        String v = (ctx.isConnected("viewDir") ? ctx.input("viewDir") : ctx.meshViewDir()).code();
        String p = ctx.input("power").code();
        String code = "pow(1.0 - clamp(dot(normalize(" + n + "), normalize(" + v + ")), 0.0, 1.0), " + p + ")";
        ctx.output("out", new ShaderExpr(code, GlslType.FLOAT));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    /** Fresnel is a rim term driven by the surface normal vs. view direction — invisible on a flat quad,
     *  so default the preview to a sphere where the rim reads clearly (like Unity's node preview). */
    @Override
    protected String defaultPreviewContentKey() {
        return "sphere";
    }
}
