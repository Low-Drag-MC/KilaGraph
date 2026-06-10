package com.lowdragmc.kilagraph.rendertype.nodes.input.fragment;

import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Base for a node that reads a fixed interpolated varying in the fragment stage — the fsh-side
 * counterpart to a vertex varying block. Reading a varying only makes sense in fragment, so the node is
 * {@link StageAffinity#FRAGMENT_ONLY}. The compiler ensures the vsh declares and writes the varying with
 * {@link #vshDefault} (unless the matching vertex block already produced it — first writer wins).
 *
 * <p>Subclasses supply the varying name + output {@link TypeHandle}, the vsh default expression, and a
 * per-node-preview default (no vertex stage exists in a preview).</p>
 */
public abstract class FragmentInputNode extends ShaderNode {

    /** The varying name shared with the matching vertex block, e.g. {@code "texCoord0"}. */
    protected abstract String varyingName();

    /** The output port's graph type (must be a GLSL-representable handle). */
    protected abstract TypeHandle outputTypeHandle();

    /** The vsh expression that writes the varying when no vertex block drives it. Runs in vertex scope. */
    protected abstract ShaderExpr vshDefault(ShaderCompileContext ctx);

    /** The value substituted in a per-node preview (which has no vertex stage). */
    protected abstract ShaderExpr previewDefault();

    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.FRAGMENT_ONLY;
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("out", outputTypeHandle());
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        GlslType type = GlslType.of(outputTypeHandle());
        GlslType t = type != null ? type : GlslType.VEC4;
        ctx.output("out", ctx.varyingInput(varyingName(), t, () -> vshDefault(ctx), previewDefault()));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }
}
