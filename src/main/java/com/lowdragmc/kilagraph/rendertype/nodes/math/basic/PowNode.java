package com.lowdragmc.kilagraph.rendertype.nodes.math.basic;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicBinaryNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

@NodeAttribute(name = "rt_pow", group = "rendertype_math/basic", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class PowNode extends DynamicBinaryNode {
    @Override
    protected String emit(String a, String b) {
        return "pow(" + a + ", " + b + ")";
    }
}
