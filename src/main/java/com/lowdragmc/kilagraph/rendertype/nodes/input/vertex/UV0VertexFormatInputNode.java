package com.lowdragmc.kilagraph.rendertype.nodes.input.vertex;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;

/**
 * Reads the raw {@code UV0} texture-coordinate vertex attribute (vec2). Vertex-only. To read the
 * interpolated uv in the fragment stage, use {@code FragmentTexCoordInputNode} instead.
 */
@NodeAttribute(name = "rt_in_uv0", group = "rendertype_input/vertex", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class UV0VertexFormatInputNode extends VertexFormatInputNode {
    @Override
    protected String attribute() {
        return "UV0";
    }

    @Override
    protected TypeHandle outputTypeHandle() {
        return RenderTypeGraphTypes.VEC2;
    }
}
