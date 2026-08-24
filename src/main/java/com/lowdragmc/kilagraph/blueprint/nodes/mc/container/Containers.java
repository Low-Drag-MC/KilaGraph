package com.lowdragmc.kilagraph.blueprint.nodes.mc.container;

import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import org.jetbrains.annotations.Nullable;

/**
 * Where a graph's container pin meets NeoForge's transfer API.
 *
 * <h2>One unchecked cast, not four</h2>
 * A pin carries a {@code ResourceHandler<ItemResource>} or a {@code ResourceHandler<FluidResource>}, but
 * the executor's {@code getInput} is told a {@code Class}, which cannot carry the type argument. So
 * reading one is unavoidably an unchecked conversion — it just does not need to be written out once per
 * node file. The graph's own type system is what makes it safe: {@code KGTypeHandles} gives the two
 * handlers different pin types, so a fluid tank cannot reach an item node's port in the first place.
 *
 * <p>Two methods rather than one because {@code EvalContext} and {@code ExecContext} are both final and
 * share no supertype — that is the engine's shape, not a choice here.</p>
 *
 * <h2>Resources are not stacks</h2>
 * 26.1 splits what a stack used to carry: a resource is the item or fluid plus its components, and the
 * amount lives on the slot. The graph's own vocabulary is still {@code ItemStack}/{@code FluidStack}, so
 * the two are recombined here — and only where a stack is really wanted, since a reader that just needs
 * identity or a count should ask the handler directly rather than allocate one per slot.
 */
public final class Containers {

    private Containers() {
    }

    /** The handler wired to the {@code container} input, or null. */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T extends Resource> ResourceHandler<T> handler(EvalContext ctx) {
        return ctx.getInput("container", ResourceHandler.class, null);
    }

    /** @see #handler(EvalContext) */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T extends Resource> ResourceHandler<T> handler(ExecContext ctx) {
        return ctx.getInput("container", ResourceHandler.class, null);
    }

    /** Slot {@code i} as an {@code ItemStack}. */
    public static ItemStack stackIn(ResourceHandler<ItemResource> h, int i) {
        return h.getResource(i).toStack(h.getAmountAsInt(i));
    }

    /**
     * Tank {@code i} as a {@code FluidStack}.
     *
     * <p>Not an overload of {@link #stackIn}: both erase to {@code ResourceHandler}, so they cannot
     * share a name.</p>
     */
    public static FluidStack fluidIn(ResourceHandler<FluidResource> h, int i) {
        return h.getResource(i).toStack(h.getAmountAsInt(i));
    }
}
