package com.lowdragmc.kilagraph.blueprint.nodes.mc;

import com.lowdragmc.kilagraph.graph.core.AnnotatedBlockNode;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import org.jetbrains.annotations.Nullable;

/**
 * Base for a block that reads one property of its parent {@link InfoContextNode}'s target.
 *
 * <p>A subclass declares its outputs as annotated fields and implements {@link #read}; everything else —
 * finding the parent, pulling its {@code target} through the executor, and the null/wrong-type case —
 * happens here. That is what keeps a property down to about ten lines, which is what makes one class per
 * property affordable.
 *
 * <pre>{@code
 * @NodeAttribute(name = "mc_level_rain_level", group = "mc_world", graphTypes = BlueprintGraph.class)
 * @UseWithContext(LevelInfoNode.class)
 * public static class RainLevel extends InfoPropertyBlock<Level> {
 *     @OutputPort public float value;
 *
 *     @Override protected Class<Level> targetClass() { return Level.class; }
 *
 *     @Override protected void read(Level level, EvalContext ctx) {
 *         ctx.setOutput("value", level.getRainLevel(1.0f));
 *     }
 * }
 * }</pre>
 *
 * <h2>Missing target</h2>
 * When the parent's target is absent — nothing wired in, or a subgraph that has not been given one yet —
 * {@link #read} is not called and every output goes unstaged, which the executor publishes as null. A
 * consumer then sees its own declared default. The alternative, throwing, would make an unwired context
 * break the whole evaluation rather than the branch that depended on it; a property read is exactly the
 * kind of thing a half-built graph should tolerate.
 *
 * <p>The type check on top of that is defence in depth rather than the mechanism. LDLib2 enforces
 * {@code @UseWithContext} when a block is inserted — attaching a {@code Player} block to an
 * {@code Entity} context throws there, so a mismatched pair cannot be built. What the check does cover is
 * a target whose runtime type is narrower than the port's: a {@code Player}-scoped block is safe because
 * its context's {@code target} port is typed {@code Player}, and this makes that safety explicit instead
 * of relying on it.
 *
 * @param <T> the target type this block knows how to read
 */
public abstract class InfoPropertyBlock<T> extends AnnotatedBlockNode {

    /** The type this block reads. Checked against the parent's target before {@link #read}. */
    protected abstract Class<T> targetClass();

    /** Write this block's outputs from {@code target}. Only called with a non-null, correctly typed one. */
    protected abstract void read(T target, EvalContext ctx);

    @Override
    public final void evaluate(EvalContext ctx) {
        Object target = parentTarget(ctx);
        if (targetClass().isInstance(target)) {
            read(targetClass().cast(target), ctx);
        }
    }

    /** The parent context's {@code target} value, or null if there is no parent or nothing feeding it. */
    @Nullable
    private Object parentTarget(EvalContext ctx) {
        var block = getBlockNodeModel();
        if (block == null) return null;
        var context = block.getContextNodeModel();
        if (context == null) return null;
        PortModel target = context.getInputsById().get("target");
        return target == null ? null : ctx.getExecutor().pullInputValue(target);
    }
}
