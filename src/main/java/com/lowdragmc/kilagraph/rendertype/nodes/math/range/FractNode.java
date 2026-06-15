package com.lowdragmc.kilagraph.rendertype.nodes.math.range;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicUnaryFuncNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

@NodeAttribute(name = "rt_fract", group = "rendertype_math/range", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class FractNode extends DynamicUnaryFuncNode {
    @Override
    protected String glslFunc() {
        return "fract";
    }
}
