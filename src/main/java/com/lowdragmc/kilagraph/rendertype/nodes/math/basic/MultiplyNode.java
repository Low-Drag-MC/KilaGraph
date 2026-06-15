package com.lowdragmc.kilagraph.rendertype.nodes.math.basic;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicBinaryNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** {@code a * b}, component-wise over the dynamic float-vector type (float/vec2/vec3/vec4). */
@NodeAttribute(name = "rt_multiply", group = "rendertype_math/basic", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class MultiplyNode extends DynamicBinaryNode {
    @Override
    protected String emit(String a, String b) {
        return "(" + a + " * " + b + ")";
    }
}
