package com.lowdragmc.kilagraph.rendertype.nodes.math.range;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicUnaryNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** {@code 1 - a}: complement, component-wise. */
@NodeAttribute(name = "rt_one_minus", group = "rendertype_math/range", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class OneMinusNode extends DynamicUnaryNode {
    @Override
    protected String emit(String a) {
        return "(1.0 - (" + a + "))";
    }
}
