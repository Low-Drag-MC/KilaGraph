package com.lowdragmc.kilagraph.rendertype.nodes.artistic.mask;

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
 * Unity's Color Mask: outputs a {@code [0,1]} mask that's 1 where {@code in} is within {@code range} of
 * {@code maskColor}, falling off over {@code fuzziness}.
 */
@NodeAttribute(name = "rt_color_mask", group = "rendertype_artistic/mask", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ColorMaskNode extends ArtisticNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", RenderTypeGraphTypes.VEC3);
        context.addInputPort("maskColor", RenderTypeGraphTypes.VEC3);
        context.addInputPort("range", TypeHandles.FLOAT);
        context.addInputPort("fuzziness", TypeHandles.FLOAT);
        context.addOutputPort("out", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String in = ctx.input("in").code();
        String maskColor = ctx.input("maskColor").code();
        String range = ctx.input("range").code();
        String fuzz = ctx.input("fuzziness").code();
        ShaderExpr dist = ctx.temp(GlslType.FLOAT, "distance(" + maskColor + ", " + in + ")");
        ctx.output("out", new ShaderExpr("clamp(1.0 - (" + dist.code() + " - " + range
                + ") / max(" + fuzz + ", 1e-5), 0.0, 1.0)", GlslType.FLOAT));
    }
}
