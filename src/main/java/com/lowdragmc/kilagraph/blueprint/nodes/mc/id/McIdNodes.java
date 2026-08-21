package com.lowdragmc.kilagraph.blueprint.nodes.mc.id;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/**
 * Namespaced ids, and the registry lookups that turn one into a real game object.
 *
 * <p>This group is the bridge between text and the game's content. A blueprint that wants to talk
 * about "minecraft:diamond" without a hard-coded picker — an id from a config, a variable, a string
 * built at runtime — comes through here.
 *
 * <h2>Why every lookup has a {@code found} output</h2>
 * Three of these four registries are <em>defaulted</em>: asking {@code BuiltInRegistries.ITEM} for an
 * unknown id hands back {@code minecraft:air} rather than nothing, and {@code FLUID} hands back
 * {@code EMPTY}. A node that only produced the value would therefore report a typo as a legitimate
 * air block. So the existence test is {@code containsKey} and a miss emits {@code null} — which is
 * both unambiguous and what {@code Is Null} is for.
 */
public final class McIdNodes {

    private static final String GROUP = "mc_id";

    private McIdNodes() {
    }

    // ---- construction and decomposition ------------------------------------------------------

    /**
     * An id's two halves.
     *
     * <p>The inverse of {@link Create}. Reading the whole id as text needs nothing — anything coerces to
     * String — but comparing just the namespace ("is this one of mine?") or just the path is a common
     * enough test to deserve not going through string splitting in the graph.</p>
     */
    @NodeAttribute(name = "mc_id_unpack", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Unpack extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_id_unpack.tooltip");
        }

        @InputPort public ResourceLocation in;
        @OutputPort public String namespace;
        @OutputPort public String path;

        @Override
        public void evaluate(EvalContext ctx) {
            ResourceLocation rl = ctx.getInput("in", ResourceLocation.class, null);
            ctx.setOutput("namespace", rl == null ? "" : rl.getNamespace());
            ctx.setOutput("path", rl == null ? "" : rl.getPath());
        }
    }

    @NodeAttribute(name = "mc_id_create", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Create extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_id_create.tooltip");
        }

        @InputPort public String namespace = "minecraft";
        @InputPort public String path = "";
        @OutputPort public ResourceLocation out;

        @Override
        public void evaluate(EvalContext ctx) {
            String ns = ctx.getInput("namespace", String.class, "minecraft");
            String p = ctx.getInput("path", String.class, "");
            // Both halves are validated, and either being malformed is a null rather than a throw:
            // an id assembled from graph data is user input, and a blueprint should be able to test
            // the result instead of dying on it.
            ResourceLocation rl = null;
            try {
                rl = ResourceLocation.fromNamespaceAndPath(ns, p);
            } catch (RuntimeException ignored) {
                // malformed namespace or path
            }
            ctx.setOutput("out", rl);
        }
    }

    /**
     * Parses a whole {@code namespace:path} string. An id with no colon takes the {@code minecraft}
     * namespace, matching how the game reads ids everywhere else.
     */
    @NodeAttribute(name = "mc_id_parse", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Parse extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_id_parse.tooltip");
        }

        @InputPort public String in = "";
        @OutputPort public ResourceLocation out;
        @OutputPort public boolean valid;

        @Override
        public void evaluate(EvalContext ctx) {
            ResourceLocation rl = ResourceLocation.tryParse(ctx.getInput("in", String.class, ""));
            ctx.setOutput("out", rl);
            ctx.setOutput("valid", rl != null);
        }
    }

    // ---- id → object -------------------------------------------------------------------------

    @NodeAttribute(name = "mc_item_from_id", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ItemFromId extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_from_id.tooltip");
        }

        @InputPort public ResourceLocation id;
        @OutputPort public Item out;
        @OutputPort public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            lookup(ctx, BuiltInRegistries.ITEM);
        }
    }

    @NodeAttribute(name = "mc_block_from_id", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class BlockFromId extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_from_id.tooltip");
        }

        @InputPort public ResourceLocation id;
        @OutputPort public Block out;
        @OutputPort public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            lookup(ctx, BuiltInRegistries.BLOCK);
        }
    }

    @NodeAttribute(name = "mc_fluid_from_id", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class FluidFromId extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_fluid_from_id.tooltip");
        }

        @InputPort public ResourceLocation id;
        @OutputPort public Fluid out;
        @OutputPort public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            lookup(ctx, BuiltInRegistries.FLUID);
        }
    }

    @NodeAttribute(name = "mc_entity_type_from_id", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class EntityTypeFromId extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_entity_type_from_id.tooltip");
        }

        @InputPort public ResourceLocation id;
        @OutputPort public EntityType<?> out;
        @OutputPort public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            lookup(ctx, BuiltInRegistries.ENTITY_TYPE);
        }
    }

    // ---- object → id -------------------------------------------------------------------------

    @NodeAttribute(name = "mc_item_id", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ItemId extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_id.tooltip");
        }

        @InputPort public Item in;
        @OutputPort public ResourceLocation out;

        @Override
        public void evaluate(EvalContext ctx) {
            keyOf(ctx, BuiltInRegistries.ITEM, ctx.getInput("in", Item.class, null));
        }
    }

    @NodeAttribute(name = "mc_block_id", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class BlockId extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_id.tooltip");
        }

        @InputPort public Block in;
        @OutputPort public ResourceLocation out;

        @Override
        public void evaluate(EvalContext ctx) {
            keyOf(ctx, BuiltInRegistries.BLOCK, ctx.getInput("in", Block.class, null));
        }
    }

    @NodeAttribute(name = "mc_fluid_id", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class FluidId extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_fluid_id.tooltip");
        }

        @InputPort public Fluid in;
        @OutputPort public ResourceLocation out;

        @Override
        public void evaluate(EvalContext ctx) {
            keyOf(ctx, BuiltInRegistries.FLUID, ctx.getInput("in", Fluid.class, null));
        }
    }

    @NodeAttribute(name = "mc_entity_type_id", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class EntityTypeId extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_entity_type_id.tooltip");
        }

        @InputPort public EntityType<?> in;
        @OutputPort public ResourceLocation out;

        @Override
        public void evaluate(EvalContext ctx) {
            keyOf(ctx, BuiltInRegistries.ENTITY_TYPE, ctx.getInput("in", EntityType.class, null));
        }
    }

    // ---- shared ------------------------------------------------------------------------------

    /**
     * {@code id → out/found} against {@code registry}.
     *
     * <p>{@code containsKey} rather than a null check on the result: these registries are defaulted,
     * so a miss would otherwise be indistinguishable from a legitimate {@code air}/{@code empty}.
     */
    private static <T> void lookup(EvalContext ctx, Registry<T> registry) {
        ResourceLocation rl = ctx.getInput("id", ResourceLocation.class, null);
        boolean found = rl != null && registry.containsKey(rl);
        ctx.setOutput("out", found ? registry.get(rl) : null);
        ctx.setOutput("found", found);
    }

    /** {@code in → out} as the registry key, or null when the value is not registered. */
    private static <T> void keyOf(EvalContext ctx, Registry<T> registry, Object value) {
        if (value == null) {
            ctx.setOutput("out", null);
            return;
        }
        @SuppressWarnings("unchecked")
        T typed = (T) value;
        ctx.setOutput("out", registry.getKey(typed));
    }
}
