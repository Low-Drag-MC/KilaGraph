package com.lowdragmc.kilagraph.rendertype.nodes.channel;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.gui.ChoiceConfigurator;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.List;

/**
 * Unity's Swizzle: builds an output vector by remapping the input's channels. Each of the four output
 * slots picks a source channel ({@code x/y/z/w}) or {@code -} (none); the output width is the count of
 * leading non-{@code -} slots (so {@code x,y,-,-} -> vec2). Compiles to a GLSL swizzle on the input
 * padded to {@code vec4} (channels the input lacks read as 0).
 */
@NodeAttribute(name = "rt_swizzle", group = "rendertype_channel", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class SwizzleNode extends ShaderNode {

    private static final List<String> AXES = List.of("x", "y", "z", "w", "-");
    private static final String[] SLOTS = {"c0", "c1", "c2", "c3"};
    private static final String[] DEFAULTS = {"x", "y", "z", "w"};

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        for (int i = 0; i < SLOTS.length; i++) {
            context.addOption(SLOTS[i], TypeHandles.STRING).withDefaultValue(DEFAULTS[i])
                    .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, AXES)).build();
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
        ShaderExpr v = ctx.temp(GlslType.VEC4, ChannelGlsl.toVec4(in));
        String mask = mask();
        ctx.output("out", new ShaderExpr("(" + v.code() + ")." + mask, GlslType.floatVector(mask.length())));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    @Override
    public String glslExample() {
        return """
                // in padded to vec4, then swizzled
                vec4 v = vec4(in, 0.0, 0.0, 0.0);
                out = v.xy;   // slots x, y, -, -""";
    }

    /** The swizzle string from the slot options: leading non-{@code -} axes, at least one ({@code x}). */
    private String mask() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SLOTS.length; i++) {
            String a = axis(SLOTS[i], DEFAULTS[i]);
            if (a.equals("-")) break;
            sb.append(a);
        }
        return sb.isEmpty() ? "x" : sb.toString();
    }

    private String axis(String id, String def) {
        INodeOption opt = getNodeOptionById(id);
        Object raw = opt == null ? null : opt.tryGetValue(Object.class).result().orElse(null);
        return raw instanceof String s && AXES.contains(s) ? s : def;
    }
}
