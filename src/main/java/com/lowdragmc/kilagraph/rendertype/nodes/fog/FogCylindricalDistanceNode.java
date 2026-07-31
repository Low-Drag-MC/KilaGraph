package com.lowdragmc.kilagraph.rendertype.nodes.fog;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Vanilla's cylindrical fog distance of a position. {@link StageAffinity#VERTEX_ONLY}: fog distance is a
 * per-vertex quantity computed from the model position (vanilla interpolates it as a varying). An
 * unconnected {@code pos} falls back to the model-space vertex position {@code (Position + ModelOffset)}.
 */
@NodeAttribute(name = "rt_fog_cylindrical_distance", group = "rendertype_scene", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class FogCylindricalDistanceNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_fog_cylindrical_distance.tooltip");
    }

    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.VERTEX_ONLY;
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("pos", RenderTypeGraphTypes.VEC3).withoutConfigurator();
        context.addOutputPort("out", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.include("minecraft:fog.glsl");
        String pos = (ctx.isConnected("pos") ? ctx.input("pos") : ctx.modelPosition()).code();
        ctx.output("out", new ShaderExpr("fog_cylindrical_distance(" + pos + ")", GlslType.FLOAT));
    }

    @Override
    public String glslExample() {
        return "out = fog_cylindrical_distance(pos);";
    }
}
