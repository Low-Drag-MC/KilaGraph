package com.lowdragmc.kilagraph.rendertype.nodes.input.fragment;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;

/**
 * Reads the interpolated {@code vertexColor} varying (vec4). When no vertex Color block drives it, the
 * varying's vsh default is the raw vertex {@code Color} attribute (lighting is the explicit MixLight node).
 */
@NodeAttribute(name = "rt_frag_color", group = "rendertype_input/fragment", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class VertexColorFragmentInputNode extends FragmentInputNode {
    @Override
    protected String varyingName() {
        return "vertexColor";
    }

    @Override
    protected TypeHandle outputTypeHandle() {
        return RenderTypeGraphTypes.VEC4;
    }

    @Override
    protected ShaderExpr vshDefault(ShaderCompileContext ctx) {
        // The varying's vsh-side default is the raw vertex Color attribute. Per-vertex lighting is NOT
        // baked in here (that vsh concern doesn't belong on a fragment node) — use the MixLight node.
        return new ShaderExpr("Color", GlslType.VEC4);
    }

    @Override
    protected ShaderExpr previewDefault() {
        return new ShaderExpr("vec4(1.0)", GlslType.VEC4);
    }
}
