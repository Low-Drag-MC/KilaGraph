package com.lowdragmc.kilagraph.rendertype.nodes.constant;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Outputs the world time in seconds (float). Backed by KilaGraph's engine-globals block
 * ({@code KG_Globals.Time}) which we update each frame — unlike Minecraft's {@code GameTime} (a 0..1
 * day fraction), this is a real seconds value suited to driving animations.
 */
@NodeAttribute(name = "rt_time", group = "rendertype_constant", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class TimeNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("time", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.output("time", ctx.engineTime());
    }

    @Override
    public String glslExample() {
        return """
                uniform float kg_Time;
                time = kg_Time;""";
    }
}
