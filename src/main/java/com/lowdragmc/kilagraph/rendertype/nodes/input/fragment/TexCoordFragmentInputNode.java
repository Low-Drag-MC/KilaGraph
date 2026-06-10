package com.lowdragmc.kilagraph.rendertype.nodes.input.fragment;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;

/** Reads the interpolated {@code texCoord0} varying (vec2). vsh default: {@code UV0}. */
@NodeAttribute(name = "rt_frag_tex_coord", group = "rendertype_input/fragment", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class TexCoordFragmentInputNode extends FragmentInputNode {
    @Override
    protected String varyingName() {
        return "texCoord0";
    }

    @Override
    protected TypeHandle outputTypeHandle() {
        return RenderTypeGraphTypes.VEC2;
    }

    @Override
    protected ShaderExpr vshDefault(ShaderCompileContext ctx) {
        return new ShaderExpr("UV0", GlslType.VEC2);
    }

    @Override
    protected ShaderExpr previewDefault() {
        return new ShaderExpr("vUv", GlslType.VEC2);
    }
}
