package com.lowdragmc.kilagraph.blueprint.nodes.mc.gameplay;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Reading and changing the enchantments on an item stack.
 *
 * <h2>Every node here needs a Level, and that is not an oversight</h2>
 * Enchantments stopped being a hard-coded registry in 1.21 and became a <b>datapack</b> registry, which
 * means they only exist relative to a loaded world — a pack can add, remove or redefine them. So an
 * enchantment id cannot be resolved from a static table the way an item or a block can; it has to go
 * through {@code level.registryAccess()}. That is why these live here rather than beside the other item
 * nodes, and why they take a world input that {@code mc_item_stack_damage} does not.
 *
 * <p>An unknown id reports {@code found = false} rather than throwing, matching every other registry
 * lookup in this mod: a datapack that does not define an enchantment is a normal situation.
 */
public final class EnchantmentNodes {

    private static final String GROUP = "mc/gameplay";

    private EnchantmentNodes() {
    }

    /**
     * The level of one enchantment on a stack.
     *
     * <p>Zero means absent, which is also what {@code has} says — the pair exists because a graph that
     * wants to branch reads {@code has} and one that wants to compute reads {@code level}, and neither
     * should have to convert.</p>
     */
    @NodeAttribute(name = "mc_enchantment_level", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class LevelOf extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_enchantment_level.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @InputPort public Identifier enchantment;
        @InputPort public Level level;
        @OutputPort public int enchantmentLevel;
        @OutputPort public boolean has;
        @OutputPort public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemStack s = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            Holder<Enchantment> holder = resolve(ctx);
            if (s == null || s.isEmpty() || holder == null) {
                ctx.setOutput("enchantmentLevel", 0);
                ctx.setOutput("has", false);
                ctx.setOutput("found", holder != null);
                return;
            }
            int lvl = EnchantmentHelper.getItemEnchantmentLevel(holder, s);
            ctx.setOutput("enchantmentLevel", lvl);
            ctx.setOutput("has", lvl > 0);
            ctx.setOutput("found", true);
        }
    }

    /**
     * Every enchantment on a stack, as ids, with their levels alongside.
     *
     * <p>Two parallel lists rather than a map, because the graph's Map keys by value and a list pair
     * survives a For Each with an index — which is how a graph walks them. The lists are the same length
     * and the same order.</p>
     */
    @NodeAttribute(name = "mc_enchantments", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class All extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_enchantments.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @OutputPort public List<?> ids;
        @OutputPort public List<?> levels;
        @OutputPort public int count;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemStack s = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            List<Identifier> ids = new ArrayList<>();
            List<Integer> levels = new ArrayList<>();
            if (s != null && !s.isEmpty()) {
                for (var entry : s.getEnchantments().entrySet()) {
                    // The holder knows its own key, so no registry access is needed to name it — which
                    // is why this node, alone in the file, does not take a Level.
                    entry.getKey().unwrapKey().ifPresent(key -> {
                        ids.add(key.identifier());
                        levels.add(entry.getIntValue());
                    });
                }
            }
            ctx.setOutput("ids", ids);
            ctx.setOutput("levels", levels);
            ctx.setOutput("count", ids.size());
        }
    }

    /**
     * A copy of the stack with an enchantment added or raised.
     *
     * <p>A data node rather than an action, like every other stack operation here: it returns a new stack
     * and changes nothing, so it composes in an expression. Nothing is written to the world until that
     * stack is put somewhere.
     *
     * <p>No validity checking, deliberately: the game itself allows commands to put any enchantment on
     * any item, and a blueprint is closer to a command than to an enchanting table. A level above the
     * enchantment's normal maximum works, exactly as {@code /enchant} allows.</p>
     */
    @NodeAttribute(name = "mc_add_enchantment", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Add extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_add_enchantment.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @InputPort public Identifier enchantment;
        @InputPort public int enchantmentLevel = 1;
        @InputPort public Level level;
        @OutputPort public ItemStack out = ItemStack.EMPTY;
        @OutputPort public boolean ok;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemStack s = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            Holder<Enchantment> holder = resolve(ctx);
            int lvl = ctx.getInt("enchantmentLevel", 1);
            if (s == null || s.isEmpty() || holder == null || lvl <= 0) {
                ctx.setOutput("out", s == null ? ItemStack.EMPTY : s);
                ctx.setOutput("ok", false);
                return;
            }
            // Copy first: the input stack may already have been read by another branch of this run.
            ItemStack copy = s.copy();
            copy.enchant(holder, lvl);
            ctx.setOutput("out", copy);
            ctx.setOutput("ok", true);
        }
    }

    /**
     * A copy of the stack with one enchantment taken off.
     *
     * <p>The other half of {@code mc_add_enchantment}, which had no counterpart: a graph could enchant a
     * tool and never un-enchant it. A data node like its opposite — it returns a new stack and changes
     * nothing.
     *
     * <p>{@code ok} reports what changed, not what was attempted: removing an enchantment the item did not
     * have is false, and so is an unknown id, because neither altered the stack. That distinction is what a
     * graph checking "was it enchanted" reads.
     *
     * <p>Removing the last enchantment leaves a plain item, not an item with an empty enchantment list —
     * the game drops the component when nothing is left, so the result compares equal to a never-enchanted
     * stack.</p>
     */
    @NodeAttribute(name = "mc_remove_enchantment", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Remove extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_remove_enchantment.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @InputPort public Identifier enchantment;
        @InputPort public Level level;
        @OutputPort public ItemStack out = ItemStack.EMPTY;
        @OutputPort public boolean ok;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemStack s = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            Holder<Enchantment> holder = resolve(ctx);
            if (s == null || s.isEmpty() || holder == null
                    || EnchantmentHelper.getItemEnchantmentLevel(holder, s) <= 0) {
                ctx.setOutput("out", s == null ? ItemStack.EMPTY : s);
                ctx.setOutput("ok", false);
                return;
            }
            // Copy first: updateEnchantments writes to the stack it is given, and the input may already
            // have been read by another branch of this run.
            ItemStack copy = s.copy();
            EnchantmentHelper.updateEnchantments(copy, mutable -> mutable.removeIf(holder::is));
            ctx.setOutput("out", copy);
            ctx.setOutput("ok", true);
        }
    }

    /**
     * The registry holder for the enchantment id on the {@code enchantment} port, or null.
     *
     * <p>Goes through the world's registry access because enchantments are a datapack registry — see the
     * class javadoc. A missing world is as much a "cannot resolve" as a missing id.</p>
     */
    @Nullable
    private static Holder<Enchantment> resolve(EvalContext ctx) {
        Level world = ctx.getInput("level", Level.class, null);
        Identifier id = ctx.getInput("enchantment", Identifier.class, null);
        if (world == null || id == null) return null;
        var registry = world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        return registry.get(id).orElse(null);
    }
}
