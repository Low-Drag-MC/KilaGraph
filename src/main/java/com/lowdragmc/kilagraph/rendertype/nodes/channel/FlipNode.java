package com.lowdragmc.kilagraph.rendertype.nodes.channel;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicBinaryNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity's Flip: flips (mirrors {@code x -> 1 - x}) the channels of {@code in} selected by the per-channel
 * toggles, leaving the rest untouched. Operates on the {@linkplain RenderTypeGraphTypes#DYNAMIC dynamic}
 * float-vector width; only the channels the input actually has are considered. Implements Unity's
 * {@code Out = (Flip * -2 + 1) * In + Flip} with {@code Flip} a 0/1 mask from the toggles.
 */
@NodeAttribute(name = "rt_flip", group = "rendertype_channel", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class FlipNode extends ShaderNode {

    private static final String[] CHANNELS = {"red", "green", "blue", "alpha"};

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        for (String c : CHANNELS) {
            context.addOption(c, TypeHandles.BOOL).withDefaultValue(false).build();
        }
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", RenderTypeGraphTypes.DYNAMIC);
        context.addOutputPort("out", RenderTypeGraphTypes.DYNAMIC);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr in = ctx.inputDynamic("in");
        int n = DynamicBinaryNode.components(in);
        GlslType type = GlslType.floatVector(n);
        ShaderExpr inT = ctx.temp(type, in.code());
        String flip = mask(n);
        ctx.output("out", new ShaderExpr(
                "((" + flip + " * -2.0 + 1.0) * " + inT.code() + " + " + flip + ")", type));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    /** The 0/1 flip mask for the active toggles, at the input's width (a scalar for {@code n == 1}). */
    private String mask(int n) {
        if (n == 1) return flag(CHANNELS[0]) ? "1.0" : "0.0";
        StringBuilder sb = new StringBuilder("vec").append(n).append('(');
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(", ");
            sb.append(flag(CHANNELS[i]) ? "1.0" : "0.0");
        }
        return sb.append(')').toString();
    }

    private boolean flag(String id) {
        INodeOption opt = getNodeOptionById(id);
        Object raw = opt == null ? null : opt.tryGetValue(Object.class).result().orElse(null);
        return raw instanceof Boolean b && b;
    }

    @Override
    public String glslExample() {
        return """
                // flip is a 0/1 mask from the toggles
                out = (flip * -2.0 + 1.0) * in + flip;""";
    }
}
