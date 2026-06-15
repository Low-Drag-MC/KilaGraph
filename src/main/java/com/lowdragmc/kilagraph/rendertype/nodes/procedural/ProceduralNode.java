package com.lowdragmc.kilagraph.rendertype.nodes.procedural;

import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;

/**
 * Shared base for Unity-style Procedural nodes (noise, shapes, checkerboard). They all read a
 * {@code uv} input that defaults to the mesh uv when unconnected, and expose a live preview of their
 * single {@code out} output. Concrete nodes add their own extra inputs and emit the GLSL.
 */
public abstract class ProceduralNode extends ShaderNode {

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    /** The {@code uv} input. It's a {@code UV}-typed port, so an unconnected one auto-resolves to the
     *  configured channel's interpolated mesh uv (the compiler handles it) — just read the port. */
    protected ShaderExpr uv(ShaderCompileContext ctx) {
        return ctx.input("uv");
    }
}
