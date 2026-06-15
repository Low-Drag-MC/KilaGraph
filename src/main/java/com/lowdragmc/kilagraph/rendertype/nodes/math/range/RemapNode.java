package com.lowdragmc.kilagraph.rendertype.nodes.math.range;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicBinaryNode;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * {@code outMin + (in - inMin) * (outMax - outMin) / (inMax - inMin)}: linearly remaps {@code in} from the
 * {@code [inMin, inMax]} range into {@code [outMin, outMax]}, component-wise over the dynamic float-vector
 * type.
 */
@NodeAttribute(name = "rt_remap", group = "rendertype_math/range", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class RemapNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", RenderTypeGraphTypes.DYNAMIC);
        context.addInputPort("inMin", RenderTypeGraphTypes.DYNAMIC);
        context.addInputPort("inMax", RenderTypeGraphTypes.DYNAMIC);
        context.addInputPort("outMin", RenderTypeGraphTypes.DYNAMIC);
        context.addInputPort("outMax", RenderTypeGraphTypes.DYNAMIC);
        context.addOutputPort("out", RenderTypeGraphTypes.DYNAMIC);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr in = ctx.inputDynamic("in");
        ShaderExpr inMin = ctx.inputDynamic("inMin");
        ShaderExpr inMax = ctx.inputDynamic("inMax");
        ShaderExpr outMin = ctx.inputDynamic("outMin");
        ShaderExpr outMax = ctx.inputDynamic("outMax");
        int comps = DynamicBinaryNode.components(in);
        for (ShaderExpr e : new ShaderExpr[]{inMin, inMax, outMin, outMax}) {
            comps = Math.max(comps, DynamicBinaryNode.components(e));
        }
        GlslType r = GlslType.floatVector(comps);
        String inc = ctx.convert(in, r).code();
        String inMinc = ctx.convert(inMin, r).code();
        String inMaxc = ctx.convert(inMax, r).code();
        String outMinc = ctx.convert(outMin, r).code();
        String outMaxc = ctx.convert(outMax, r).code();
        String code = "(" + outMinc + " + (" + inc + " - " + inMinc + ") * ("
                + outMaxc + " - " + outMinc + ") / (" + inMaxc + " - " + inMinc + "))";
        ctx.output("out", new ShaderExpr(code, r));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }
}
