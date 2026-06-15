package com.lowdragmc.kilagraph.rendertype.nodes.math.round;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicUnaryFuncNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** {@code trunc(a)}: round toward zero per component. */
@NodeAttribute(name = "rt_truncate", group = "rendertype_math/round", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class TruncateNode extends DynamicUnaryFuncNode {
    @Override
    protected String glslFunc() {
        return "trunc";
    }
}
