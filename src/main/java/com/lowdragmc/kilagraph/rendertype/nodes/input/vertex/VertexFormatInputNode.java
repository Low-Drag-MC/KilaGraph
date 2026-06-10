package com.lowdragmc.kilagraph.rendertype.nodes.input.vertex;

import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Base for a node that reads a raw vertex-format attribute (a vsh {@code in} variable such as
 * {@code Position}/{@code Color}/{@code Normal}/{@code UV0}). These attributes exist only in the vertex
 * shader, so the node is {@link StageAffinity#VERTEX_ONLY}: pulling it into the fragment stage (directly
 * or transitively) is flagged as a stage error — route the value through a vertex varying block (or the
 * matching {@code FragmentInputNode}) to read it in fragment.
 *
 * <p>Subclasses supply the GLSL attribute name ({@link #attribute()}) and the output {@link TypeHandle}
 * ({@link #outputTypeHandle()}); the base wires the single {@code out} port and emits the attribute.</p>
 */
public abstract class VertexFormatInputNode extends ShaderNode {

    /** The GLSL vertex attribute name, e.g. {@code "Position"}. */
    protected abstract String attribute();

    /** The output port's graph type (must be a GLSL-representable handle). */
    protected abstract TypeHandle outputTypeHandle();

    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.VERTEX_ONLY;
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("out", outputTypeHandle());
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        GlslType type = GlslType.of(outputTypeHandle());
        ctx.output("out", new ShaderExpr(attribute(), type != null ? type : GlslType.VEC4));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }
}
