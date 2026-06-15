package com.lowdragmc.kilagraph.rendertype.nodes.math.trigonometry;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicUnaryFuncNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** {@code degrees(a)}: converts radians to degrees, component-wise. */
@NodeAttribute(name = "rt_radians_to_degrees", group = "rendertype_math/trigonometry", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class RadiansToDegreesNode extends DynamicUnaryFuncNode {
    @Override
    protected String glslFunc() {
        return "degrees";
    }
}
