package com.lowdragmc.kilagraph.rendertype.nodes.math.round;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicUnaryFuncNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** {@code sign(a)}: -1/0/+1 per component. */
@NodeAttribute(name = "rt_sign", group = "rendertype_math/round", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class SignNode extends DynamicUnaryFuncNode {
    @Override
    protected String glslFunc() {
        return "sign";
    }
}
