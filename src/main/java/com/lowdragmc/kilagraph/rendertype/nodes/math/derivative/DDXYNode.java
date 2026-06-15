package com.lowdragmc.kilagraph.rendertype.nodes.math.derivative;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** {@code fwidth(a)}: {@code abs(dFdx(a)) + abs(dFdy(a))}, the screen-space rate of change (fragment only). */
@NodeAttribute(name = "rt_ddxy", group = "rendertype_math/derivative", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class DDXYNode extends DerivativeNode {
    @Override
    protected String glslFunc() {
        return "fwidth";
    }
}
