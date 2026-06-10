package com.lowdragmc.kilagraph.rendertype.nodes.constant;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Outputs Minecraft's builtin {@code Globals.GameTime} (float): a normalised day fraction in
 * {@code [0,1)} that wraps every Minecraft day (24000 ticks), bound by vanilla's
 * {@code bindDefaultUniforms}. For real seconds use {@link TimeNode} instead.
 */
@NodeAttribute(name = "rt_game_time", group = "rendertype_constant", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class GameTimeNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("gameTime", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.output("gameTime", ctx.mcGameTime());
    }
}
