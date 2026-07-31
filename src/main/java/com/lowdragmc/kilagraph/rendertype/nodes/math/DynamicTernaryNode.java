package com.lowdragmc.kilagraph.rendertype.nodes.math;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Base for a three-argument component-wise builtin over the {@linkplain RenderTypeGraphTypes#DYNAMIC
 * dynamic} float-vector type ({@code out = func(a, b, c)}) — e.g. {@code clamp}/{@code mix}/{@code
 * smoothstep}. All three operands are broadcast to the widest input width, which the result carries.
 * Subclasses supply {@link #glslFunc()} and may rename the ports via {@link #portIds()}.
 */
public abstract class DynamicTernaryNode extends ShaderNode {

    /** The three-argument GLSL builtin, e.g. {@code "clamp"} (must be overloaded for vecN). */
    protected abstract String glslFunc();

    /** Input port ids in argument order; default {@code a,b,c}. Override for friendlier names. */
    protected String[] portIds() {
        return new String[]{"a", "b", "c"};
    }

    /** Derived from the builtin and the real port ids, so the documented GLSL matches the emitted code. */
    @Override
    public String glslExample() {
        String[] ids = portIds();
        return "out = " + glslFunc() + "(" + ids[0] + ", " + ids[1] + ", " + ids[2] + ");";
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        for (String id : portIds()) context.addInputPort(id, RenderTypeGraphTypes.DYNAMIC);
        context.addOutputPort("out", RenderTypeGraphTypes.DYNAMIC);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String[] ids = portIds();
        ShaderExpr a = ctx.inputDynamic(ids[0]);
        ShaderExpr b = ctx.inputDynamic(ids[1]);
        ShaderExpr c = ctx.inputDynamic(ids[2]);
        int comps = Math.max(DynamicBinaryNode.components(a),
                Math.max(DynamicBinaryNode.components(b), DynamicBinaryNode.components(c)));
        GlslType result = GlslType.floatVector(comps);
        String code = glslFunc() + "(" + ctx.convert(a, result).code() + ", "
                + ctx.convert(b, result).code() + ", " + ctx.convert(c, result).code() + ")";
        ctx.output("out", new ShaderExpr(code, result));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }
}
