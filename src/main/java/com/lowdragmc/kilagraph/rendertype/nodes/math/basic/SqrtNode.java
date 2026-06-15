package com.lowdragmc.kilagraph.rendertype.nodes.math.basic;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicUnaryFuncNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

@NodeAttribute(name = "rt_sqrt", group = "rendertype_math/basic", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class SqrtNode extends DynamicUnaryFuncNode {
    @Override
    protected String glslFunc() {
        return "sqrt";
    }
}
