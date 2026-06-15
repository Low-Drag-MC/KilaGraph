package com.lowdragmc.kilagraph.rendertype.nodes.math.derivative;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** {@code dFdy(a)}: partial derivative of {@code a} with respect to the screen y axis (fragment only). */
@NodeAttribute(name = "rt_ddy", group = "rendertype_math/derivative", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class DDYNode extends DerivativeNode {
    @Override
    protected String glslFunc() {
        return "dFdy";
    }
}
