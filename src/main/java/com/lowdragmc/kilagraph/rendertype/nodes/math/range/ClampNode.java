package com.lowdragmc.kilagraph.rendertype.nodes.math.range;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicTernaryNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** {@code clamp(value, min, max)}: constrains {@code value} into {@code [min, max]}, component-wise. */
@NodeAttribute(name = "rt_clamp", group = "rendertype_math/range", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ClampNode extends DynamicTernaryNode {
    @Override
    protected String glslFunc() {
        return "clamp";
    }

    @Override
    protected String[] portIds() {
        return new String[]{"value", "min", "max"};
    }
}
