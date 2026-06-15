package com.lowdragmc.kilagraph.rendertype.nodes.math.interpolation;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicTernaryNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** {@code smoothstep(edge0, edge1, x)}: smooth Hermite interpolation between two edges, component-wise. */
@NodeAttribute(name = "rt_smoothstep", group = "rendertype_math/interpolation", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class SmoothstepNode extends DynamicTernaryNode {
    @Override
    protected String glslFunc() {
        return "smoothstep";
    }

    @Override
    protected String[] portIds() {
        return new String[]{"edge0", "edge1", "x"};
    }
}
