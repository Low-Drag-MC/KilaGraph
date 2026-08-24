package com.lowdragmc.kilagraph.blueprint.nodes.mc.tag;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything a tag contains, one node per registry.
 *
 * <h2>Why this exists next to the membership nodes</h2>
 * {@code mc_item_in_tag} and friends answer "is this one thing in that tag". They cannot answer "what is
 * in that tag", and neither can a command — {@code /execute if items} still needs a candidate to test. A
 * graph that wants to iterate every plank, or pick a random ore, has no other way in.
 *
 * <h2>No world input</h2>
 * Items, blocks, entity types and fluids live in {@link BuiltInRegistries}, and a datapack reload binds
 * its tags onto exactly those registries, so the lookup is total from the static table. This is the same
 * reason the membership nodes in {@link McTagNodes} take no world while
 * {@code mc_enchantments} does: enchantments are a datapack registry and only exist relative to a loaded
 * world, tags on hard-coded registries are not.
 *
 * <p>The consequence is that these read as empty before a server has loaded its packs. That is a real
 * state on a dedicated server during startup, and it is reported as {@code found = false} rather than as
 * an error, because a graph asking for a tag no pack defines is the same situation.
 *
 * <h2>Objects, not ids</h2>
 * The list holds {@code Item}/{@code Block}/{@code EntityType}/{@code Fluid} values, not
 * {@link Identifier}s, because that is what the rest of the graph consumes — {@code mc_item_stack_create}
 * wants an item, {@code mc_set_block} wants a block. Going the other way is one {@code mc_item_id} away;
 * going back from an id costs a registry lookup per element.
 *
 * <p>Order is the order the tag files produced, nested tags flattened in place. It is stable for a fixed
 * set of packs but it is neither alphabetical nor guaranteed across pack changes, so a graph that needs a
 * deterministic pick should sort by id rather than index blindly.
 */
public final class TagContentsNodes {

    private static final String GROUP = "mc_tag";

    private TagContentsNodes() {
    }

    @NodeAttribute(name = "mc_items_in_tag", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ItemsInTag extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_items_in_tag.tooltip");
        }

        @InputPort public Identifier tag;
        @OutputPort public List<?> out;
        @OutputPort public int count;
        @OutputPort public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            contents(ctx, BuiltInRegistries.ITEM, Registries.ITEM);
        }
    }

    @NodeAttribute(name = "mc_blocks_in_tag", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class BlocksInTag extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_blocks_in_tag.tooltip");
        }

        @InputPort public Identifier tag;
        @OutputPort public List<?> out;
        @OutputPort public int count;
        @OutputPort public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            contents(ctx, BuiltInRegistries.BLOCK, Registries.BLOCK);
        }
    }

    @NodeAttribute(name = "mc_entity_types_in_tag", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class EntityTypesInTag extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_entity_types_in_tag.tooltip");
        }

        @InputPort public Identifier tag;
        @OutputPort public List<?> out;
        @OutputPort public int count;
        @OutputPort public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            contents(ctx, BuiltInRegistries.ENTITY_TYPE, Registries.ENTITY_TYPE);
        }
    }

    @NodeAttribute(name = "mc_fluids_in_tag", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class FluidsInTag extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_fluids_in_tag.tooltip");
        }

        @InputPort public Identifier tag;
        @OutputPort public List<?> out;
        @OutputPort public int count;
        @OutputPort public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            contents(ctx, BuiltInRegistries.FLUID, Registries.FLUID);
        }
    }

    /**
     * Writes the contents of the {@code tag} input to {@code out}/{@code count}/{@code found}.
     *
     * <p>{@code found} separates "no such tag" from "a tag that is empty" — both give an empty list, but
     * only one of them means the graph has the id wrong, and a graph that silently iterates zero items is
     * the hardest kind of mistake to see.</p>
     */
    private static <T> void contents(EvalContext ctx, Registry<T> registry,
                                     ResourceKey<? extends Registry<T>> registryKey) {
        Identifier id = ctx.getInput("tag", Identifier.class, null);
        List<T> out = new ArrayList<>();
        boolean found = false;
        if (id != null) {
            var named = registry.get(TagKey.create(registryKey, id));
            if (named.isPresent()) {
                found = true;
                for (Holder<T> holder : named.get()) {
                    out.add(holder.value());
                }
            }
        }
        ctx.setOutput("out", out);
        ctx.setOutput("count", out.size());
        ctx.setOutput("found", found);
    }
}
