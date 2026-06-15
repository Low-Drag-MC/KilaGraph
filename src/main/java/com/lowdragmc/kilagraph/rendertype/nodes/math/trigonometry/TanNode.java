package com.lowdragmc.kilagraph.rendertype.nodes.math.trigonometry;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicUnaryFuncNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

@NodeAttribute(name = "rt_tan", group = "rendertype_math/trigonometry", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class TanNode extends DynamicUnaryFuncNode {
    @Override
    protected String glslFunc() {
        return "tan";
    }
}
