package com.lowdragmc.kilagraph.rendertype.nodes.math.trigonometry;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicUnaryFuncNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** {@code sinh(a)}: hyperbolic sine, component-wise. */
@NodeAttribute(name = "rt_sinh", group = "rendertype_math/trigonometry", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class SinhNode extends DynamicUnaryFuncNode {
    @Override
    protected String glslFunc() {
        return "sinh";
    }
}
