package com.lowdragmc.kilagraph.rendertype.nodes.scene;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity's Screen node: the viewport dimensions in pixels ({@code Width} / {@code Height}), from
 * KilaGraph's own {@code KG_Globals.ScreenSize} (same value as Minecraft's {@code Globals.ScreenSize},
 * but no {@code #moj_import} — a Minecraft include would make any graph using this node
 * non-injectable under an Iris shaderpack; see {@code ShaderGraphCompiler#buildInjectionSnippet}).
 */
@NodeAttribute(name = "rt_screen", group = "rendertype_scene", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ScreenNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_screen.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("Width", TypeHandles.FLOAT);
        context.addOutputPort("Height", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr size = ctx.screenSize();
        ctx.output("Width", new ShaderExpr(size.code() + ".x", GlslType.FLOAT));
        ctx.output("Height", new ShaderExpr(size.code() + ".y", GlslType.FLOAT));
    }
}
