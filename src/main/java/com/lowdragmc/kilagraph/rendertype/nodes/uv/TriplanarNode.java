package com.lowdragmc.kilagraph.rendertype.nodes.uv;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity's Triplanar: projects {@code texture} onto the surface along the three world axes and blends the
 * three samples by the surface normal, avoiding uv seams/stretching. {@link StageAffinity#FRAGMENT_ONLY}
 * (samples a texture). Unconnected {@code position}/{@code normal} default to the mesh model-space position
 * and world normal (both overridable, as in Unity); {@code texture} unconnected → the missing-texture
 * sampler. Outputs the blended colour (vec4).
 *
 * <p>Caveat: the default {@code position} is model space while the default {@code normal} is world space —
 * consistent for an unrotated mesh; wire matching-space inputs if you need exactness under rotation.</p>
 */
@NodeAttribute(name = "rt_triplanar", group = "rendertype_uv", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class TriplanarNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_triplanar.tooltip");
    }

    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.FRAGMENT_ONLY;
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("texture", RenderTypeGraphTypes.SAMPLER2D).withoutConfigurator();
        context.addInputPort("position", RenderTypeGraphTypes.VEC3).withoutConfigurator();
        context.addInputPort("normal", RenderTypeGraphTypes.VEC3).withoutConfigurator();
        context.addInputPort("tile", TypeHandles.FLOAT).withDefaultValue(1f);
        context.addInputPort("blend", TypeHandles.FLOAT).withDefaultValue(1f);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String tex = (ctx.isConnected("texture") ? ctx.input("texture") : ctx.missingSampler()).code();
        ShaderExpr position = ctx.isConnected("position") ? ctx.input("position") : ctx.meshPosition();
        ShaderExpr normal = ctx.isConnected("normal") ? ctx.input("normal") : ctx.meshNormal();
        String tile = ctx.input("tile").code();
        String blend = ctx.input("blend").code();

        ShaderExpr uvw = ctx.temp(GlslType.VEC3, "(" + position.code() + " * " + tile + ")");
        ShaderExpr w = ctx.temp(GlslType.VEC3, "pow(abs(" + normal.code() + "), vec3(" + blend + "))");
        ShaderExpr wn = ctx.temp(GlslType.VEC3, "(" + w.code() + " / (" + w.code() + ".x + " + w.code() + ".y + " + w.code() + ".z))");
        String x = "texture(" + tex + ", " + uvw.code() + ".zy)";
        String y = "texture(" + tex + ", " + uvw.code() + ".xz)";
        String z = "texture(" + tex + ", " + uvw.code() + ".xy)";
        String code = "(" + x + " * " + wn.code() + ".x + " + y + " * " + wn.code() + ".y + " + z + " * " + wn.code() + ".z)";
        ctx.output("out", new ShaderExpr(code, GlslType.VEC4));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }
}
