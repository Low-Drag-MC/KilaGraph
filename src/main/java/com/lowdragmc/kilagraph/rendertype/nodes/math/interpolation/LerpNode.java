package com.lowdragmc.kilagraph.rendertype.nodes.math.interpolation;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicTernaryNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** {@code mix(a, b, t)}: linearly interpolates from {@code a} to {@code b} by {@code t}, component-wise. */
@NodeAttribute(name = "rt_lerp", group = "rendertype_math/interpolation", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class LerpNode extends DynamicTernaryNode {
    @Override
    protected String glslFunc() {
        return "mix";
    }

    @Override
    protected String[] portIds() {
        return new String[]{"a", "b", "t"};
    }
}
