package com.lowdragmc.kilagraph.rendertype.nodes.math;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

@NodeAttribute(name = "rt_floor", group = "rendertype_math", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class FloorNode extends ShaderUnaryFloatNode {
    @Override
    protected String glslFunc() {
        return "floor";
    }
}
