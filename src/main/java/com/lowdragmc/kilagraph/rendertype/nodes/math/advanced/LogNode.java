package com.lowdragmc.kilagraph.rendertype.nodes.math.advanced;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
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
import java.util.Map;

/**
 * Logarithm, component-wise over the dynamic float-vector type, with a {@code base} dropdown (Unity's
 * Log node): {@code e → log(a)}, {@code 2 → log2(a)}, {@code 10 → log(a) / log(10)} (GLSL has no
 * {@code log10}). Output width follows the input.
 */
@NodeAttribute(name = "rt_log", group = "rendertype_math/advanced", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class LogNode extends ShaderNode {

    private static final List<String> BASES = List.of("e", "2", "10");
    private static final Map<String, String> LABELS = Map.of("e", "Base E", "2", "Base 2", "10", "Base 10");

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("base", TypeHandles.STRING).withDefaultValue("e")
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, BASES, LABELS::get)).build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("a", RenderTypeGraphTypes.DYNAMIC);
        context.addOutputPort("out", RenderTypeGraphTypes.DYNAMIC);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr a = ctx.inputDynamic("a");
        String code = switch (base()) {
            case "2" -> "log2(" + a.code() + ")";
            case "10" -> "(log(" + a.code() + ") / log(10.0))";
            default -> "log(" + a.code() + ")";
        };
        ctx.output("out", new ShaderExpr(code, a.type()));
    }

    private String base() {
        INodeOption opt = getNodeOptionById("base");
        Object raw = opt == null ? null : opt.tryGetValue(Object.class).result().orElse(null);
        return BASES.contains(raw) ? (String) raw : "e";
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }
}
