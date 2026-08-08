package com.lowdragmc.kilagraph.rendertype;

import com.lowdragmc.kilagraph.graph.type.KGGraphModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;

/**
 * Shared graph-model base for the shader graphs ({@link RenderTypeGraph} and
 * {@link ShaderFunctionGraph}). Relaxes {@code canAssignTo} so any shader scalar/vector wires into any
 * other (float ↔ vec2/3/4) — the compiler's {@code convert} handles broadcast/swizzle/pad.
 */
public class ShaderGraphModelBase extends KGGraphModel {

    public ShaderGraphModelBase(Graph graph) {
        super(graph);
    }

    @Override
    public boolean canAssignTo(PortModel destination, PortModel source) {
        TypeHandle dst = destination.getDataTypeHandle();
        TypeHandle src = source.getDataTypeHandle();
        if (isShaderVector(dst) && isShaderVector(src)) {
            return true;
        }
        return super.canAssignTo(destination, source);
    }

    private static boolean isShaderVector(TypeHandle type) {
        return type.equals(TypeHandles.FLOAT)
                || type.equals(RenderTypeGraphTypes.VEC2)
                || type.equals(RenderTypeGraphTypes.VEC3)
                || type.equals(RenderTypeGraphTypes.VEC4)
                || type.equals(RenderTypeGraphTypes.DYNAMIC) // Dynamic math type: any float/vec
                || type.equals(RenderTypeGraphTypes.UV) // uv coordinate, compiles to vec2
                || type.equals(TypeHandles.COLOR) // ARGB color, compiles to vec4
                || type.equals(TypeHandles.HDR_COLOR); // HDR color, compiles to a premultiplied vec4
    }
}
