package com.lowdragmc.kilagraph.blueprint.nodes.mc.item;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * Item stacks: counts, equality, and the data components a blueprint is likely to read or write.
 *
 * <h2>Stacks are values, and every node here returns a new one</h2>
 * {@code ItemStack} is mutable in the game's code, and a graph is pull-evaluated with memoised
 * outputs — the same stack object can be read by several downstream nodes in one run. Mutating an
 * input would therefore change what an already-evaluated branch saw. So every node that "changes"
 * something copies first, exactly as the list nodes do.
 *
 * <h2>Why only some components</h2>
 * 1.21 replaced stack NBT with a typed component map, which is large. The ones here are the ones a
 * blueprint actually reaches for: custom data (the general-purpose escape hatch that replaced the old
 * tag), the custom name, and lore. The rest of the map is reachable generically through
 * {@code mc_item_get_component} / {@code mc_item_components}, which is what a component key nobody
 * anticipated should go through.
 *
 * <h2>Reading plain facts</h2>
 * {@link Unpack}, {@link Damage} and {@link Limits} cover them, grouped by what a graph asks together
 * rather than one node per getter. {@code ItemStack} has 49 reflectively-readable members and perhaps a
 * dozen a blueprint ever wants; the rest ({@code frame}, {@code popTime}, {@code barWidth},
 * {@code useAnimation}, {@code entityRepresentation}) are renderer and internals detail that a property
 * searcher should not have been offering as though they were data.
 */
public final class ItemStackNodes {

    private static final String GROUP = "mc_item";

    private ItemStackNodes() {
    }

    // ---- reading -----------------------------------------------------------------------------

    /**
     * What a stack is: its item, how many, and whether it is nothing at all.
     *
     * <p>The three fundamentals, and the inverse of {@code mc_item_stack_create}. {@code empty} is a
     * separate output rather than something to infer from {@code count == 0} because
     * {@code ItemStack.isEmpty()} is the game's own test and also covers an air item.</p>
     */
    @NodeAttribute(name = "mc_item_stack_unpack", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Unpack extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_stack_unpack.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @OutputPort public net.minecraft.world.item.Item item;
        @OutputPort public int count;
        @OutputPort public boolean empty;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemStack s = stack(ctx, "stack");
            ctx.setOutput("item", s.getItem());
            ctx.setOutput("count", s.getCount());
            ctx.setOutput("empty", s.isEmpty());
        }
    }

    /**
     * A stack's durability.
     *
     * <p>{@code damage} counts up from zero, the way the game stores it — a fresh tool is 0 and a
     * nearly-broken one approaches {@code maxDamage}. {@code damageable} distinguishes "undamaged" from
     * "cannot be damaged", which a graph showing a durability bar has to know: a stone block reports
     * damage 0 and maxDamage 0, and dividing those is how you get a NaN into a tooltip.</p>
     */
    @NodeAttribute(name = "mc_item_stack_damage", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Damage extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_stack_damage.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @OutputPort public int damage;
        @OutputPort public int maxDamage;
        @OutputPort public boolean damaged;
        @OutputPort public boolean damageable;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemStack s = stack(ctx, "stack");
            ctx.setOutput("damage", s.getDamageValue());
            ctx.setOutput("maxDamage", s.getMaxDamage());
            ctx.setOutput("damaged", s.isDamaged());
            ctx.setOutput("damageable", s.isDamageableItem());
        }
    }

    /** How large a stack may get, and whether it carries an enchantment. */
    @NodeAttribute(name = "mc_item_stack_limits", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Limits extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_stack_limits.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @OutputPort public int maxStackSize;
        @OutputPort public boolean stackable;
        @OutputPort public boolean enchanted;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemStack s = stack(ctx, "stack");
            ctx.setOutput("maxStackSize", s.getMaxStackSize());
            ctx.setOutput("stackable", s.isStackable());
            ctx.setOutput("enchanted", s.isEnchanted());
        }
    }

    /**
     * An item type's own constants, independent of any stack.
     *
     * <p>{@code maxStackSize} here is the item's default; a stack's own limit can differ, because a
     * component can override it — read that from {@link Limits} instead. This node is for asking about
     * an {@code Item} you have without inventing a stack to hold it.</p>
     */
    @NodeAttribute(name = "mc_item_props", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ItemProps extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_props.tooltip");
        }

        @InputPort public net.minecraft.world.item.Item in;
        @OutputPort public Component name;
        @OutputPort public int maxStackSize;
        @OutputPort public int enchantmentValue;

        @Override
        public void evaluate(EvalContext ctx) {
            var item = ctx.getInput("in", net.minecraft.world.item.Item.class, null);
            if (item == null) item = net.minecraft.world.item.Items.AIR;
            ctx.setOutput("name", (Object) item.getDescription());
            ctx.setOutput("maxStackSize", item.getDefaultMaxStackSize());
            ctx.setOutput("enchantmentValue", item.getEnchantmentValue());
        }
    }

    /** The same item with a different count. Count is clamped at zero; a zero count is an empty stack. */
    @NodeAttribute(name = "mc_item_stack_with_count", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class WithCount extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_stack_with_count.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @InputPort public int count = 1;
        @OutputPort public ItemStack out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", stack(ctx, "stack").copyWithCount(Math.max(0, ctx.getInt("count", 1))));
        }
    }

    /**
     * Whether two stacks hold the same item, ignoring count and components.
     *
     * <p>The question "is this a diamond" — as opposed to "is this the <em>same</em> diamond sword with
     * the same enchantments", which is the next node.</p>
     */
    @NodeAttribute(name = "mc_item_stack_same_item", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SameItem extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_stack_same_item.tooltip");
        }

        @InputPort public ItemStack a = ItemStack.EMPTY;
        @InputPort public ItemStack b = ItemStack.EMPTY;
        @OutputPort public boolean out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", ItemStack.isSameItem(stack(ctx, "a"), stack(ctx, "b")));
        }
    }

    /**
     * Whether two stacks are interchangeable: same item <em>and</em> same components.
     *
     * <p>This is the test that decides whether two stacks would merge in an inventory. Count is still
     * ignored — two stacks of different sizes are the same kind of thing.</p>
     */
    @NodeAttribute(name = "mc_item_stack_same_components", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SameComponents extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_stack_same_components.tooltip");
        }

        @InputPort public ItemStack a = ItemStack.EMPTY;
        @InputPort public ItemStack b = ItemStack.EMPTY;
        @OutputPort public boolean out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", ItemStack.isSameItemSameComponents(stack(ctx, "a"), stack(ctx, "b")));
        }
    }

    /** The name shown in a tooltip — the custom name if it has one, otherwise the item's own. */
    @NodeAttribute(name = "mc_item_stack_display_name", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class DisplayName extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_stack_display_name.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @OutputPort public Component out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", stack(ctx, "stack").getHoverName());
        }
    }

    /**
     * The stack's {@code custom_data} component as NBT.
     *
     * <p>This is where arbitrary mod data lives in 1.21 — the typed replacement for the old whole-stack
     * tag. A stack with no custom data reads as an empty compound rather than null, so a graph can
     * always ask a question of the result.</p>
     */
    @NodeAttribute(name = "mc_item_stack_custom_data", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class GetCustomData extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_stack_custom_data.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @OutputPort public CompoundTag out;

        @Override
        public void evaluate(EvalContext ctx) {
            CustomData data = stack(ctx, "stack").get(DataComponents.CUSTOM_DATA);
            // copyTag, not getUnsafe: the component is shared and immutable by contract, and handing a
            // graph the live tag would let a downstream NBT-set node edit every stack that shares it.
            ctx.setOutput("out", data == null ? new CompoundTag() : data.copyTag());
        }
    }

    @NodeAttribute(name = "mc_item_stack_set_custom_data", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SetCustomData extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_stack_set_custom_data.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @InputPort public CompoundTag tag;
        @OutputPort public ItemStack out;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemStack copy = stack(ctx, "stack").copy();
            CompoundTag tag = ctx.getInput("tag", CompoundTag.class, null);
            copy.set(DataComponents.CUSTOM_DATA, CustomData.of(tag == null ? new CompoundTag() : tag));
            ctx.setOutput("out", copy);
        }
    }

    /** The custom name, if one was set. {@code has} tells a rename apart from a default name. */
    @NodeAttribute(name = "mc_item_stack_custom_name", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class GetCustomName extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_stack_custom_name.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @OutputPort public Component out;
        @OutputPort public boolean has;

        @Override
        public void evaluate(EvalContext ctx) {
            Component name = stack(ctx, "stack").get(DataComponents.CUSTOM_NAME);
            ctx.setOutput("out", name == null ? Component.empty() : name);
            ctx.setOutput("has", name != null);
        }
    }

    @NodeAttribute(name = "mc_item_stack_set_custom_name", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SetCustomName extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_stack_set_custom_name.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @InputPort public Component name;
        @OutputPort public ItemStack out;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemStack copy = stack(ctx, "stack").copy();
            Component name = ctx.getInput("name", Component.class, null);
            // A null name REMOVES the component rather than setting an empty one, so this node can
            // undo a rename instead of leaving behind a blank line where the name was.
            if (name == null) {
                copy.remove(DataComponents.CUSTOM_NAME);
            } else {
                copy.set(DataComponents.CUSTOM_NAME, name);
            }
            ctx.setOutput("out", copy);
        }
    }

    /** The stack's lore lines. Empty list when it has none. */
    @NodeAttribute(name = "mc_item_stack_lore", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Lore extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_stack_lore.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @OutputPort public List<?> out;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemLore lore = stack(ctx, "stack").get(DataComponents.LORE);
            ctx.setOutput("out", lore == null ? List.of() : new ArrayList<>(lore.lines()));
        }
    }

    private static ItemStack stack(EvalContext ctx, String id) {
        ItemStack s = ctx.getInput(id, ItemStack.class, ItemStack.EMPTY);
        return s == null ? ItemStack.EMPTY : s;
    }
}
