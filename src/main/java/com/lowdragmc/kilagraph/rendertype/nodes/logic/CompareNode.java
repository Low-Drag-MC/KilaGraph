package com.lowdragmc.kilagraph.rendertype.nodes.logic;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.gui.ChoiceConfigurator;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Unity's Comparison node: compares two scalars {@code a} and {@code b} with the chosen operator and outputs
 * a {@code bool}. Scalar-only (like Unity); wire the result into a {@link BranchNode}'s predicate. The
 * operator is a node option ({@code equal}/{@code notEqual}/{@code less}/{@code lessEqual}/{@code greater}/
 * {@code greaterEqual}) edited via a dropdown.
 */
@NodeAttribute(name = "rt_compare", group = "rendertype_logic", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class CompareNode extends ShaderNode {

    @Override
    protected @Nullable Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_compare.tooltip");
    }

    /** Option value -> GLSL operator. Insertion order drives the dropdown order. */
    private static final Map<String, String> OPS = new java.util.LinkedHashMap<>();
    static {
        OPS.put("equal", "==");
        OPS.put("notEqual", "!=");
        OPS.put("less", "<");
        OPS.put("lessEqual", "<=");
        OPS.put("greater", ">");
        OPS.put("greaterEqual", ">=");
    }
    private static final List<String> MODES = List.copyOf(OPS.keySet());

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("op", TypeHandles.STRING).withDefaultValue("equal")
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, MODES, CompareNode::label)).build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("a", TypeHandles.FLOAT);
        context.addInputPort("b", TypeHandles.FLOAT);
        context.addOutputPort("out", TypeHandles.BOOL);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String a = ctx.input("a").code();
        String b = ctx.input("b").code();
        String op = OPS.getOrDefault(ctx.option("op", String.class, "equal"), "==");
        ctx.output("out", new ShaderExpr("(" + a + " " + op + " " + b + ")", GlslType.BOOL));
    }

    private static String label(String mode) {
        return switch (mode) {
            case "equal" -> "A == B";
            case "notEqual" -> "A != B";
            case "less" -> "A < B";
            case "lessEqual" -> "A <= B";
            case "greater" -> "A > B";
            case "greaterEqual" -> "A >= B";
            default -> mode;
        };
    }
}
