package com.lowdragmc.kilagraph.rendertype.nodes.math.advanced;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicUnaryFuncNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** {@code inversesqrt(a)}: 1/sqrt(a), component-wise. */
@NodeAttribute(name = "rt_inversesqrt", group = "rendertype_math/advanced", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class InverseSqrtNode extends DynamicUnaryFuncNode {
    @Override
    protected String glslFunc() {
        return "inversesqrt";
    }
}
