package com.lowdragmc.kilagraph.rendertype.nodes.math.wave;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicUnaryNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** Triangle wave: {@code 2 * abs(2 * (a - floor(a + 0.5))) - 1}, in [-1, 1] with period 1, component-wise. */
@NodeAttribute(name = "rt_triangle_wave", group = "rendertype_math/wave", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class TriangleWaveNode extends DynamicUnaryNode {
    @Override
    protected String emit(String a) {
        return "(2.0 * abs(2.0 * ((" + a + ") - floor((" + a + ") + 0.5))) - 1.0)";
    }
}
