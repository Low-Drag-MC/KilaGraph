package com.lowdragmc.kilagraph.blueprint.nodes.mc.tag;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

/**
 * Tag membership, one node per registry.
 *
 * <h2>Tags are ids here, not a type</h2>
 * A tag is modelled as a {@link ResourceLocation} rather than a {@code TagKey<T>} pin type. Typed tag
 * handles would be stronger — they would stop an item tag reaching a block-tag pin — but they are
 * expensive in a way that buys little: {@code TagKey<Item>}'s identification string is not
 * {@code Class.forName}-able, its codec needs the registry up front, and its picker is per-registry.
 * Since the node already names the registry, the type would be restating what the node says.
 *
 * <p>Which registry each node targets is therefore baked in rather than an option. That keeps the
 * lookup total (no "unknown registry" failure mode) and lets each node's docs name the tag folder a
 * user should be looking in.
 *
 * <p>A malformed or absent id is {@code false}, never a throw — a tag id is user data.
 */
public final class McTagNodes {

    private static final String GROUP = "mc_tag";

    private McTagNodes() {
    }

    @NodeAttribute(name = "mc_block_in_tag", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class BlockInTag extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_in_tag.tooltip");
        }

        @InputPort public Block block;
        @InputPort public ResourceLocation tag;
        @OutputPort public boolean out;

        @Override
        public void evaluate(EvalContext ctx) {
            Block b = ctx.getInput("block", Block.class, null);
            TagKey<Block> key = tagKey(ctx, Registries.BLOCK);
            ctx.setOutput("out", b != null && key != null && b.builtInRegistryHolder().is(key));
        }
    }

    @NodeAttribute(name = "mc_fluid_in_tag", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class FluidInTag extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_fluid_in_tag.tooltip");
        }

        @InputPort public Fluid fluid;
        @InputPort public ResourceLocation tag;
        @OutputPort public boolean out;

        @Override
        public void evaluate(EvalContext ctx) {
            Fluid f = ctx.getInput("fluid", Fluid.class, null);
            TagKey<Fluid> key = tagKey(ctx, Registries.FLUID);
            ctx.setOutput("out", f != null && key != null && f.builtInRegistryHolder().is(key));
        }
    }

    @NodeAttribute(name = "mc_entity_type_in_tag", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class EntityTypeInTag extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_entity_type_in_tag.tooltip");
        }

        @InputPort public EntityType<?> type;
        @InputPort public ResourceLocation tag;
        @OutputPort public boolean out;

        @Override
        public void evaluate(EvalContext ctx) {
            EntityType<?> t = ctx.getInput("type", EntityType.class, null);
            TagKey<EntityType<?>> key = tagKey(ctx, Registries.ENTITY_TYPE);
            ctx.setOutput("out", t != null && key != null && t.builtInRegistryHolder().is(key));
        }
    }

    /**
     * Item-tag test that takes a stack rather than an {@code Item}.
     *
     * <p>Not redundant with {@code mc_item_in_tag}: a stack is what most graphs actually hold, and
     * {@code ItemStack.is(TagKey)} is the game's own path — going through {@code getItem()} would work
     * today but is the wrong thing to write down.</p>
     */
    @NodeAttribute(name = "mc_item_stack_in_tag", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ItemStackInTag extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_stack_in_tag.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @InputPort public ResourceLocation tag;
        @OutputPort public boolean out;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemStack s = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            TagKey<net.minecraft.world.item.Item> key = tagKey(ctx, Registries.ITEM);
            ctx.setOutput("out", s != null && key != null && s.is(key));
        }
    }

    /**
     * Block-tag test that takes a state.
     *
     * <p>{@code BlockState.is(TagKey)} rather than {@code getBlock().builtInRegistryHolder().is(...)}
     * — the same answer today, but state-level is where the game asks the question.</p>
     */
    @NodeAttribute(name = "mc_block_state_in_tag", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class BlockStateInTag extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_state_in_tag.tooltip");
        }

        @InputPort public BlockState state;
        @InputPort public ResourceLocation tag;
        @OutputPort public boolean out;

        @Override
        public void evaluate(EvalContext ctx) {
            BlockState s = ctx.getInput("state", BlockState.class, null);
            TagKey<Block> key = tagKey(ctx, Registries.BLOCK);
            ctx.setOutput("out", s != null && key != null && s.is(key));
        }
    }

    /** The {@code tag} input as a {@link TagKey} in {@code registry}, or null if it is absent. */
    private static <T> TagKey<T> tagKey(EvalContext ctx, ResourceKey<? extends net.minecraft.core.Registry<T>> registry) {
        ResourceLocation rl = ctx.getInput("tag", ResourceLocation.class, null);
        return rl == null ? null : TagKey.create(registry, rl);
    }
}
