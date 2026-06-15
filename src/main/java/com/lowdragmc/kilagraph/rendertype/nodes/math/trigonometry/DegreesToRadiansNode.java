package com.lowdragmc.kilagraph.rendertype.nodes.math.trigonometry;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicUnaryFuncNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** {@code radians(a)}: converts degrees to radians, component-wise. */
@NodeAttribute(name = "rt_degrees_to_radians", group = "rendertype_math/trigonometry", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class DegreesToRadiansNode extends DynamicUnaryFuncNode {
    @Override
    protected String glslFunc() {
        return "radians";
    }
}
