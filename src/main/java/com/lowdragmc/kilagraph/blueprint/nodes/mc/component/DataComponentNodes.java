package com.lowdragmc.kilagraph.blueprint.nodes.mc.component;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Data components, the general form.
 *
 * <h2>Why one generic family instead of a node per component</h2>
 * 1.21 replaced item NBT with a typed component map: {@code minecraft:damage} is an {@code Integer},
 * {@code minecraft:custom_name} a {@code Component}, {@code minecraft:enchantments} a whole structure.
 * There are well over a hundred of them and mods add more, so a node per component is not a library
 * anyone would want to maintain or scroll through.
 *
 * <p>The named nodes that do exist — custom data, custom name, lore — are there because they are
 * reached for constantly and deserve typed ports. Everything else comes through here.
 *
 * <h2>NBT is the exchange currency, and why</h2>
 * A component's Java type is decided by which component it is, so a port cannot follow it — the same
 * problem block-state properties have, and solved the same way: go through the serialised form. Every
 * component type carries a {@link Codec}, so encoding to NBT is exact and lossless, and the text form
 * is the one that appears in {@code /give} and {@code /data}. That makes these nodes total (every
 * component is reachable, including modded ones) while being honest that they are not type-safe.
 *
 * <p>Values that do not encode to a compound — a bare int for {@code damage}, a string for a name —
 * are wrapped under a {@code value} key so the {@code nbt} output always has something to hand to the
 * NBT nodes. {@code text} carries the unwrapped form for the cases where reading it is enough.
 */
public final class DataComponentNodes {

    private static final String GROUP = "mc/component";
    /** The key a non-compound component value is wrapped under. @see #encode */
    public static final String VALUE_KEY = "value";

    private DataComponentNodes() {
    }

    // ---- listing -----------------------------------------------------------------------------

    /** Every component id present on a stack, so a graph can discover them rather than guess. */
    @NodeAttribute(name = "mc_item_components", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ItemComponents extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_components.tooltip");
        }

        @InputPort
        public ItemStack stack = ItemStack.EMPTY;
        @OutputPort
        public List<?> out;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemStack s = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            List<ResourceLocation> ids = new ArrayList<>();
            if (s != null) {
                for (DataComponentType<?> type : s.getComponents().keySet()) {
                    ResourceLocation id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
                    if (id != null) ids.add(id);
                }
            }
            ctx.setOutput("out", ids);
        }
    }

    @NodeAttribute(name = "mc_fluid_components", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class FluidComponents extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_fluid_components.tooltip");
        }

        @InputPort
        public FluidStack stack = FluidStack.EMPTY;
        @OutputPort
        public List<?> out;

        @Override
        public void evaluate(EvalContext ctx) {
            FluidStack s = ctx.getInput("stack", FluidStack.class, FluidStack.EMPTY);
            List<ResourceLocation> ids = new ArrayList<>();
            if (s != null && !s.isEmpty()) {
                for (DataComponentType<?> type : s.getComponents().keySet()) {
                    ResourceLocation id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
                    if (id != null) ids.add(id);
                }
            }
            ctx.setOutput("out", ids);
        }
    }

    // ---- presence ----------------------------------------------------------------------------

    @NodeAttribute(name = "mc_item_has_component", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class HasComponent extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_has_component.tooltip");
        }

        @InputPort
        public ItemStack stack = ItemStack.EMPTY;
        @InputPort
        public ResourceLocation component;
        @OutputPort
        public boolean out;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemStack s = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            DataComponentType<?> type = type(ctx);
            ctx.setOutput("out", s != null && type != null && s.has(type));
        }
    }

    // ---- read --------------------------------------------------------------------------------

    /**
     * Reads one component off a stack, as NBT and as text.
     *
     * <p>Both outputs come from the same encode; neither is a re-parse of the other. Use {@code nbt}
     * to feed the NBT nodes and {@code text} to show or compare a value.</p>
     */
    @NodeAttribute(name = "mc_item_get_component", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class GetComponent extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_get_component.tooltip");
        }

        @InputPort
        public ItemStack stack = ItemStack.EMPTY;
        @InputPort
        public ResourceLocation component;
        @OutputPort
        public CompoundTag nbt;
        @OutputPort
        public String text;
        @OutputPort
        public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemStack s = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            DataComponentType<?> type = type(ctx);
            Tag encoded = s == null || type == null ? null : encode(s, type);
            ctx.setOutput("nbt", encoded == null ? new CompoundTag() : wrap(encoded));
            ctx.setOutput("text", encoded == null ? "" : encoded.toString());
            ctx.setOutput("found", encoded != null);
        }
    }

    // ---- write -------------------------------------------------------------------------------

    /**
     * Stores one component on a copy of the stack, decoding the value from NBT.
     *
     * <p>{@code ok} is false when the component id is unknown or the NBT does not fit that component's
     * shape, and the stack passes through unchanged — a chain of these does not lose everything to one
     * bad edit. Round-tripping the output of {@code Get Component} back through here always works,
     * which is the property that makes the pair usable.</p>
     */
    @NodeAttribute(name = "mc_item_set_component", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SetComponent extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_set_component.tooltip");
        }

        @InputPort
        public ItemStack stack = ItemStack.EMPTY;
        @InputPort
        public ResourceLocation component;
        @InputPort
        public CompoundTag nbt;
        @OutputPort
        public ItemStack out;
        @OutputPort
        public boolean ok;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemStack s = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            if (s == null) s = ItemStack.EMPTY;
            DataComponentType<?> type = type(ctx);
            CompoundTag tag = ctx.getInput("nbt", CompoundTag.class, null);
            ItemStack applied = type == null || tag == null ? null : decodeInto(s, type, unwrap(tag));
            ctx.setOutput("out", applied == null ? s : applied);
            ctx.setOutput("ok", applied != null);
        }
    }

    @NodeAttribute(name = "mc_item_remove_component", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class RemoveComponent extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_remove_component.tooltip");
        }

        @InputPort
        public ItemStack stack = ItemStack.EMPTY;
        @InputPort
        public ResourceLocation component;
        @OutputPort
        public ItemStack out;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemStack s = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            if (s == null) s = ItemStack.EMPTY;
            DataComponentType<?> type = type(ctx);
            if (type == null) {
                ctx.setOutput("out", s);
                return;
            }
            ItemStack copy = s.copy();
            copy.remove(type);
            ctx.setOutput("out", copy);
        }
    }

    // ---- shared ------------------------------------------------------------------------------

    /** The component type named by the {@code component} input, or null. */
    private static DataComponentType<?> type(EvalContext ctx) {
        ResourceLocation rl = ctx.getInput("component", ResourceLocation.class, null);
        return rl == null ? null : BuiltInRegistries.DATA_COMPONENT_TYPE.get(rl);
    }

    /**
     * The stack's value for {@code type}, encoded through the component's own codec.
     *
     * <p>Generic helper because {@code get} and {@code codecOrThrow} have to agree on the same
     * {@code T}, which a wildcard cannot express at the call site. Returns null when the stack does not
     * carry the component, or when the component has no persistent codec — some are transient by
     * design and {@code codecOrThrow} would throw rather than answer.</p>
     */
    private static <T> Tag encode(ItemStack stack, DataComponentType<T> type) {
        T value = stack.get(type);
        if (value == null) return null;
        Codec<T> codec = type.codec();
        if (codec == null) return null;
        return codec.encodeStart(NbtOps.INSTANCE, value).result().orElse(null);
    }

    /** {@code stack} with {@code type} decoded from {@code tag}, or null if the NBT does not fit. */
    private static <T> ItemStack decodeInto(ItemStack stack, DataComponentType<T> type, Tag tag) {
        Codec<T> codec = type.codec();
        if (codec == null) return null;
        T value = codec.parse(NbtOps.INSTANCE, tag).result().orElse(null);
        if (value == null) return null;
        ItemStack copy = stack.copy();
        copy.set(type, value);
        return copy;
    }

    /**
     * A tag as a compound, wrapping anything that is not one.
     *
     * <p>Most components encode to a compound and pass straight through. A few encode to a bare value
     * — {@code damage} is an int, {@code custom_name} a string — and those need somewhere to live,
     * because the graph's NBT type is a compound. {@link #unwrap} is the exact inverse, so a
     * get/set round trip is lossless either way.</p>
     */
    private static CompoundTag wrap(Tag tag) {
        if (tag instanceof CompoundTag compound) return compound;
        CompoundTag wrapper = new CompoundTag();
        wrapper.put(VALUE_KEY, tag);
        return wrapper;
    }

    /** The inverse of {@link #wrap}. */
    private static Tag unwrap(CompoundTag tag) {
        if (tag.size() == 1 && tag.contains(VALUE_KEY)) {
            Tag inner = tag.get(VALUE_KEY);
            if (inner != null) return inner;
        }
        return tag;
    }
}
