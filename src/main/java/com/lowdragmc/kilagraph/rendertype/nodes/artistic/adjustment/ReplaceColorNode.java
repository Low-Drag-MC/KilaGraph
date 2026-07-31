package com.lowdragmc.kilagraph.rendertype.nodes.artistic.adjustment;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.nodes.artistic.ArtisticNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity's Replace Color: swaps pixels near {@code from} (within {@code range}, softened by
 * {@code fuzziness}) for {@code to}, leaving the rest of {@code in} untouched.
 */
@NodeAttribute(name = "rt_replace_color", group = "rendertype_artistic/adjustment", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ReplaceColorNode extends ArtisticNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_replace_color.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", RenderTypeGraphTypes.VEC3);
        context.addInputPort("from", RenderTypeGraphTypes.VEC3);
        context.addInputPort("to", RenderTypeGraphTypes.VEC3);
        context.addInputPort("range", TypeHandles.FLOAT);
        context.addInputPort("fuzziness", TypeHandles.FLOAT);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String in = ctx.input("in").code();
        String from = ctx.input("from").code();
        String to = ctx.input("to").code();
        String range = ctx.input("range").code();
        String fuzz = ctx.input("fuzziness").code();
        ShaderExpr dist = ctx.temp(GlslType.FLOAT, "distance(" + from + ", " + in + ")");
        ctx.output("out", new ShaderExpr("mix(" + to + ", " + in + ", clamp((" + dist.code() + " - "
                + range + ") / max(" + fuzz + ", 1e-5), 0.0, 1.0))", GlslType.VEC3));
    }

    @Override
    public String glslExample() {
        return """
                float d = distance(from, in);
                out = mix(to, in, clamp((d - range)
                    / max(fuzziness, 1e-5), 0.0, 1.0));""";
    }
}
