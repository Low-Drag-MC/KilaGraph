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
 * Unity's Parallax Mapping: shifts the uv along the tangent-space view direction in proportion to a height
 * map, so a flat face reads as though its texture had depth. Outputs the shifted uv — feed it to whatever
 * samples the surface (base colour, the normal map, everything, so they stay consistent).
 *
 * <p>One sample, one shift: it reads the height at the <em>original</em> uv rather than where the eye really
 * lands, so it skews on steep relief and at grazing angles. That is the honest trade for its cost; reach for
 * {@link ParallaxOcclusionMappingNode} when the relief is deep enough for the skew to show.
 * {@link StageAffinity#FRAGMENT_ONLY} — it samples a texture and needs a per-fragment view direction.</p>
 *
 * <p>The tangent-space view direction comes from {@link ShaderCompileContext#spaceToTangent}, i.e. the same
 * derived basis every other tangent consumer uses. It is therefore only as good as that basis: in the vertex
 * stage there is none worth having, which is the other reason this node is fragment-only.</p>
 */
@NodeAttribute(name = "rt_parallax_mapping", group = "rendertype_uv", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ParallaxMappingNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_parallax_mapping.tooltip");
    }

    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.FRAGMENT_ONLY;
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("heightmap", RenderTypeGraphTypes.SAMPLER2D).withoutConfigurator();
        context.addInputPort("uv", RenderTypeGraphTypes.UV);
        // In surface units: at amplitude 0.05 the deepest part of the groove reads 5% of a uv tile below the
        // face. Small numbers are the whole point — parallax exaggerates fast.
        context.addInputPort("amplitude", TypeHandles.FLOAT).withDefaultValue(0.05f);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC2);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr sampler = ctx.isConnected("heightmap") ? ctx.input("heightmap") : ctx.missingSampler();
        ShaderExpr viewTS = ctx.temp(GlslType.VEC3,
                "normalize(" + ctx.spaceToTangent("object", ctx.objectSpaceViewDir()).code() + ")");
        ctx.function(ParallaxGlsl.OFFSET_NAME, ParallaxGlsl.OFFSET);
        ctx.output("out", new ShaderExpr(ParallaxGlsl.OFFSET_NAME + "(" + sampler.code() + ", "
                + ctx.input("uv").code() + ", " + viewTS.code() + ", "
                + ctx.input("amplitude").code() + ")", GlslType.VEC2));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    @Override
    public String glslExample() {
        return """
                float h = texture(heightmap, uv).r;
                vec2 dir = viewTS.xy / max(abs(viewTS.z), 1e-4);
                out = uv - dir * (1.0 - h) * amplitude;""";
    }
}
