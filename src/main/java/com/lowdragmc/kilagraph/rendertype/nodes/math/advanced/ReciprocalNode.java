package com.lowdragmc.kilagraph.rendertype.nodes.math.advanced;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicUnaryNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** {@code 1 / a}: reciprocal, component-wise. */
@NodeAttribute(name = "rt_reciprocal", group = "rendertype_math/advanced", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ReciprocalNode extends DynamicUnaryNode {
    @Override
    protected String emit(String a) {
        return "(1.0 / (" + a + "))";
    }
}
