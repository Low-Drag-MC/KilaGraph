package com.lowdragmc.kilagraph.rendertype.nodes.math.vector;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicBinaryNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * {@code 1 - saturate((distance(coords, center) - radius) / (1 - hardness))}: a soft spherical mask —
 * 1 inside {@code radius} of {@code center}, falling to 0 over a {@code hardness}-controlled edge.
 */
@NodeAttribute(name = "rt_sphere_mask", group = "rendertype_math/vector", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class SphereMaskNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("coords", RenderTypeGraphTypes.DYNAMIC).withoutConfigurator();
        context.addInputPort("center", RenderTypeGraphTypes.DYNAMIC);
        context.addInputPort("radius", TypeHandles.FLOAT);
        context.addInputPort("hardness", TypeHandles.FLOAT);
        context.addOutputPort("out", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        // Unconnected coords default to the mesh's model-space position (Unity-like).
        ShaderExpr coords = ctx.isConnected("coords") ? ctx.inputDynamic("coords") : ctx.meshPosition();
        ShaderExpr center = ctx.inputDynamic("center");
        GlslType r = GlslType.floatVector(Math.max(DynamicBinaryNode.components(coords), DynamicBinaryNode.components(center)));
        String c = ctx.convert(coords, r).code();
        String ce = ctx.convert(center, r).code();
        String radius = ctx.input("radius").code();
        String hardness = ctx.input("hardness").code();
        String code = "(1.0 - clamp((distance(" + c + ", " + ce + ") - " + radius
                + ") / (1.0 - " + hardness + "), 0.0, 1.0))";
        ctx.output("out", new ShaderExpr(code, GlslType.FLOAT));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    @Override
    public String glslExample() {
        return """
                out = 1.0 - clamp((distance(coords, center)
                    - radius) / (1.0 - hardness), 0.0, 1.0);""";
    }
}
