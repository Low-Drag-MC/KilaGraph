package com.lowdragmc.kilagraph.rendertype.nodes.math.trigonometry;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicBinaryFuncNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** {@code atan(a, b)}: two-argument arctangent of {@code a/b} (radians), component-wise. */
@NodeAttribute(name = "rt_atan2", group = "rendertype_math/trigonometry", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class Atan2Node extends DynamicBinaryFuncNode {
    @Override
    protected String glslFunc() {
        return "atan";
    }
}
