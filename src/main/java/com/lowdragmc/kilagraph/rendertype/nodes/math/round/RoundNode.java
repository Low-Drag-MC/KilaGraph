package com.lowdragmc.kilagraph.rendertype.nodes.math.round;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicUnaryFuncNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** {@code round(a)}: nearest integer per component. */
@NodeAttribute(name = "rt_round", group = "rendertype_math/round", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class RoundNode extends DynamicUnaryFuncNode {
    @Override
    protected String glslFunc() {
        return "round";
    }
}
