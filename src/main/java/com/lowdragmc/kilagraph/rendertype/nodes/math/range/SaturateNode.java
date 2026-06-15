package com.lowdragmc.kilagraph.rendertype.nodes.math.range;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicUnaryNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** {@code clamp(a, 0, 1)}: saturate into the unit range, component-wise. */
@NodeAttribute(name = "rt_saturate", group = "rendertype_math/range", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class SaturateNode extends DynamicUnaryNode {
    @Override
    protected String emit(String a) {
        return "clamp(" + a + ", 0.0, 1.0)";
    }
}
