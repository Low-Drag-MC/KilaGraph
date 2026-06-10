package com.lowdragmc.kilagraph.rendertype.nodes.input.fragment;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;

/** Reads the interpolated {@code sphericalVertexDistance} varying (float). vsh default: fog distance. */
@NodeAttribute(name = "rt_frag_spherical_distance", group = "rendertype_input/fragment", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class SphericalDistanceFragmentInputNode extends FragmentInputNode {
    @Override
    protected String varyingName() {
        return "sphericalVertexDistance";
    }

    @Override
    protected TypeHandle outputTypeHandle() {
        return TypeHandles.FLOAT;
    }

    @Override
    protected ShaderExpr vshDefault(ShaderCompileContext ctx) {
        ctx.include("minecraft:fog.glsl");
        return new ShaderExpr("fog_spherical_distance(Position)", GlslType.FLOAT);
    }

    @Override
    protected ShaderExpr previewDefault() {
        return new ShaderExpr("0.0", GlslType.FLOAT);
    }
}
