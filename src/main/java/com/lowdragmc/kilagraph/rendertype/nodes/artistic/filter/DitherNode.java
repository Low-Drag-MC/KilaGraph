package com.lowdragmc.kilagraph.rendertype.nodes.artistic.filter;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.kilagraph.rendertype.nodes.artistic.ArtisticNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity's Dither: subtracts a per-pixel 4×4 ordered (Bayer) threshold from {@code in}, so a smooth value
 * thresholded afterwards (e.g. {@code Alpha Discard}) reads as a stable screen-door pattern instead of a
 * hard edge. Uses {@code gl_FragCoord} (screen pixel), hence {@link StageAffinity#FRAGMENT_ONLY}.
 */
@NodeAttribute(name = "rt_dither", group = "rendertype_artistic/filter", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class DitherNode extends ArtisticNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_dither.tooltip");
    }


    private static final String DITHER = """
            float kg_dither(float v, vec2 p) {
                float t[16] = float[16](
                    1.0 / 17.0,  9.0 / 17.0,  3.0 / 17.0, 11.0 / 17.0,
                    13.0 / 17.0, 5.0 / 17.0, 15.0 / 17.0,  7.0 / 17.0,
                    4.0 / 17.0, 12.0 / 17.0,  2.0 / 17.0, 10.0 / 17.0,
                    16.0 / 17.0, 8.0 / 17.0, 14.0 / 17.0,  6.0 / 17.0);
                int index = int(mod(p.x, 4.0)) * 4 + int(mod(p.y, 4.0));
                return v - t[index];
            }""";

    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.FRAGMENT_ONLY; // reads gl_FragCoord
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", TypeHandles.FLOAT);
        context.addOutputPort("out", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.function("kg_dither", DITHER);
        ctx.output("out", new ShaderExpr(
                "kg_dither(" + ctx.input("in").code() + ", gl_FragCoord.xy)", GlslType.FLOAT));
    }

    @Override
    public String glslExample() {
        return """
                // 4x4 ordered Bayer threshold
                out = kg_dither(in, gl_FragCoord.xy);""";
    }
}
