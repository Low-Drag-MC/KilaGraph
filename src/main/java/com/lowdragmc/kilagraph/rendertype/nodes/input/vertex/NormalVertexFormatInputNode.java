package com.lowdragmc.kilagraph.rendertype.nodes.input.vertex;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;

/** Reads the object-space vertex {@code Normal} attribute (vec3). Vertex-only. */
@NodeAttribute(name = "rt_in_normal", group = "rendertype_input/vertex", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class NormalVertexFormatInputNode extends VertexFormatInputNode {
    @Override
    protected String attribute() {
        return "Normal";
    }

    @Override
    protected TypeHandle outputTypeHandle() {
        return RenderTypeGraphTypes.VEC3;
    }
}
