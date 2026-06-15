package com.lowdragmc.kilagraph.rendertype.nodes.math.derivative;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** {@code dFdx(a)}: partial derivative of {@code a} with respect to the screen x axis (fragment only). */
@NodeAttribute(name = "rt_ddx", group = "rendertype_math/derivative", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class DDXNode extends DerivativeNode {
    @Override
    protected String glslFunc() {
        return "dFdx";
    }
}
