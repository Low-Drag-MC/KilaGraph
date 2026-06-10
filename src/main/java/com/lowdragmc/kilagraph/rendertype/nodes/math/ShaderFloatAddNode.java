package com.lowdragmc.kilagraph.rendertype.nodes.math;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

@NodeAttribute(name = "rt_float_add", group = "rendertype_math", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ShaderFloatAddNode extends ShaderBinaryFloatNode {
    @Override
    protected String emit(String a, String b) {
        return "(" + a + " + " + b + ")";
    }
}
