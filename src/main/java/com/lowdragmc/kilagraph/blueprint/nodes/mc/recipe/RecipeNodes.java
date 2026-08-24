package com.lowdragmc.kilagraph.blueprint.nodes.mc.recipe;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * Asking the game what a recipe would produce.
 *
 * <h2>Why these are nodes when so much else is a command</h2>
 * {@code mc_run_command} covers most of what a graph wants from the game, because most of it has a command.
 * Recipes do not: there is no {@code /recipe query}, nothing that answers "what does this craft into" or
 * "what does this need". A blueprint that automates a workbench has no other way in, which is what makes
 * this the largest group added purely on the strength of the gap.
 *
 * <h2>Every node needs a world, and that is not an oversight</h2>
 * Recipes are datapack content — a pack can add, remove or redefine any of them — so they only exist
 * relative to a loaded world and have to be reached through {@code level.getRecipeManager()}. Same reason
 * {@link com.lowdragmc.kilagraph.blueprint.nodes.mc.gameplay.EnchantmentNodes} takes one and the potion
 * nodes do not.
 *
 * <h2>assemble, not getResultItem</h2>
 * The two lookup-by-input nodes report what the block would really hand you, which for a few recipes is not
 * the flat result: dyed leather, a cloned map, a copied book. {@code getResultItem} is the recipe's
 * declared output and is what the by-id nodes report, since they have no input to assemble from — the
 * difference is deliberate and is called out on each node.
 *
 * <p>An unknown recipe id, an input nothing matches, and a missing world are all {@code found = false}
 * rather than throws, matching every other lookup in this mod.
 */
public final class RecipeNodes {

    private static final String GROUP = "mc/recipe";

    /**
     * Largest grid {@code mc_crafting_result} will build.
     *
     * <p>Not a rule about crafting — vanilla recipes never exceed 3x3, but a mod's table can — just a
     * ceiling so that a width wired from a runaway counter cannot ask for a million-slot allocation.</p>
     */
    private static final int MAX_GRID = 64;

    private RecipeNodes() {
    }

    /**
     * What a crafting grid of items would produce.
     *
     * <p>{@code width} and {@code height} are the shape of the grid the items sit in, read row by row from
     * the top left. They matter: the same nine items in a 3x3 and in a 1x9 are different recipes, and a
     * shaped recipe that fits in 2x2 will not be found in a 1x4.
     *
     * <p>The result comes from {@code assemble}, so this is what the table would really give you rather
     * than the recipe's declared output — see the class docs.</p>
     */
    @NodeAttribute(name = "mc_crafting_result", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class CraftingResult extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_crafting_result.tooltip");
        }

        @InputPort public Level level;
        @InputPort public List<?> items;
        @InputPort public int width = 3;
        @InputPort public int height = 3;
        @OutputPort public ItemStack out = ItemStack.EMPTY;
        @OutputPort public boolean found;
        @OutputPort public Identifier recipeId;

        @Override
        public void evaluate(EvalContext ctx) {
            Level world = ctx.getInput("level", Level.class, null);
            int width = ctx.getInt("width", 3);
            int height = ctx.getInt("height", 3);
            // The multiplication is widened on purpose: as ints, 65536 by 65536 overflows to exactly zero
            // and slips past the ceiling, and the game's own grid builder then indexes off the end of the
            // padded list. A width wired from a runaway counter must be refused, not thrown at.
            if (world == null || width < 1 || height < 1 || (long) width * height > MAX_GRID) {
                miss(ctx);
                return;
            }

            CraftingInput input = CraftingInput.of(width, height, grid(ctx, width * height));
            var manager = recipes(world);
            var holder = manager == null ? null : manager
                    .getRecipeFor(RecipeType.CRAFTING, input, world)
                    .orElse(null);
            if (holder == null) {
                miss(ctx);
                return;
            }
            ctx.setOutput("out", holder.value().assemble(input));
            ctx.setOutput("recipeId", holder.id().identifier());
            ctx.setOutput("found", true);
        }

        /**
         * The {@code items} input as exactly {@code slots} stacks.
         *
         * <p>Padded and truncated rather than refused, because a graph builds this list with the list nodes
         * and a length that does not happen to equal width times height is an ordinary intermediate state,
         * not an error. Anything in the list that is not an item stack reads as an empty slot.</p>
         */
        private static List<ItemStack> grid(EvalContext ctx, int slots) {
            List<?> given = ctx.getInput("items", List.class, List.of());
            List<ItemStack> stacks = new ArrayList<>(slots);
            for (int i = 0; i < slots; i++) {
                Object at = given != null && i < given.size() ? given.get(i) : null;
                stacks.add(at instanceof ItemStack stack ? stack : ItemStack.EMPTY);
            }
            return stacks;
        }

        private static void miss(EvalContext ctx) {
            ctx.setOutput("out", ItemStack.EMPTY);
            ctx.setOutput("recipeId", null);
            ctx.setOutput("found", false);
        }
    }

    /**
     * What a furnace, blast furnace, smoker or campfire would produce from one item.
     *
     * <p>All three numbers come out together because they come from one lookup: a graph deciding whether to
     * smelt something wants the result, the time it will take and the experience it will pay, and asking
     * for them separately would run the same search three times.
     *
     * <p>{@code experience} is per item and is a fraction — iron ore pays 0.7 — which is why the furnace
     * hands out whole levels only after several smelts.</p>
     */
    @NodeAttribute(name = "mc_smelting_result", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SmeltingResult extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_smelting_result.tooltip");
        }

        @Option public CookingType cookingType = CookingType.SMELTING;
        @InputPort public Level level;
        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @OutputPort public ItemStack out = ItemStack.EMPTY;
        @OutputPort public float experience;
        @OutputPort public int time;
        @OutputPort public boolean found;
        @OutputPort public Identifier recipeId;

        @Override
        public void evaluate(EvalContext ctx) {
            Level world = ctx.getInput("level", Level.class, null);
            ItemStack in = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            CookingType type = ctx.getOption("cookingType", CookingType.class, CookingType.SMELTING);
            if (world == null || in == null || in.isEmpty()) {
                miss(ctx);
                return;
            }

            SingleRecipeInput input = new SingleRecipeInput(in);
            var manager = recipes(world);
            RecipeHolder<AbstractCookingRecipe> holder = manager == null ? null : manager
                    .getRecipeFor(type.recipeType(), input, world)
                    .orElse(null);
            if (holder == null) {
                miss(ctx);
                return;
            }
            AbstractCookingRecipe recipe = holder.value();
            ctx.setOutput("out", recipe.assemble(input));
            ctx.setOutput("experience", recipe.experience());
            ctx.setOutput("time", recipe.cookingTime());
            ctx.setOutput("recipeId", holder.id().identifier());
            ctx.setOutput("found", true);
        }

        private static void miss(EvalContext ctx) {
            ctx.setOutput("out", ItemStack.EMPTY);
            ctx.setOutput("experience", 0f);
            ctx.setOutput("time", 0);
            ctx.setOutput("recipeId", null);
            ctx.setOutput("found", false);
        }

        @Override
        public List<String> optionChoices(String optionId) {
            return "cookingType".equals(optionId) ? CookingType.CHOICES : List.of();
        }
    }

    /**
     * Every recipe that produces a given item, by id.
     *
     * <p>The backwards question, and the expensive one: there is no index from result to recipe, so this
     * walks the whole recipe list — a few thousand entries on a vanilla server, more with mods. Fine once
     * in response to something; not fine every tick.
     *
     * <p>Sorted by id, because the recipe manager stores recipes in a hash map and its iteration order is
     * not stable across runs. A graph that picks {@code out[0]} would otherwise quietly get a different
     * recipe on a different launch.
     *
     * <p>Matched on the recipe's declared result rather than an assembled one, since there is no input to
     * assemble from. Special recipes that compute their output — map cloning, firework crafting — declare
     * nothing and so appear under no item.</p>
     */
    @NodeAttribute(name = "mc_recipes_for", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class RecipesFor extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_recipes_for.tooltip");
        }

        @InputPort public Level level;
        @InputPort public Item item;
        @OutputPort public List<?> out;
        @OutputPort public int count;

        @Override
        public void evaluate(EvalContext ctx) {
            Level world = ctx.getInput("level", Level.class, null);
            Item wanted = ctx.getInput("item", Item.class, null);
            List<Identifier> ids = new ArrayList<>();
            var manager = recipes(world);
            if (manager != null && wanted != null) {
                var displayContext = SlotDisplayContext.fromLevel(world);
                for (RecipeHolder<?> holder : manager.getRecipes()) {
                    if (resultOf(holder.value(), displayContext).is(wanted)) {
                        ids.add(holder.id().identifier());
                    }
                }
                ids.sort(Comparator.comparing(Identifier::toString));
            }
            ctx.setOutput("out", ids);
            ctx.setOutput("count", ids.size());
        }
    }

    /**
     * What one named recipe produces.
     *
     * <p>For a graph that already knows the recipe — from {@code mc_recipes_for}, or written down — and
     * wants its output and kind without reconstructing an input for it.
     *
     * <p>{@code out} is the recipe's declared result, not an assembled one; a recipe that computes its
     * output gives an empty stack here. {@code type} is the recipe kind, which is how a graph tells a
     * crafting recipe from a smelting one before deciding where to put the ingredients.</p>
     */
    @NodeAttribute(name = "mc_recipe_by_id", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class RecipeById extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_recipe_by_id.tooltip");
        }

        @InputPort public Level level;
        @InputPort public Identifier id;
        @OutputPort public ItemStack out = ItemStack.EMPTY;
        @OutputPort public Identifier type;
        @OutputPort public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            Level world = ctx.getInput("level", Level.class, null);
            // Guarded here rather than inside lookup so that the world is visibly non-null below, where
            // the registries are needed to resolve the result.
            Recipe<?> recipe = world == null ? null : lookup(ctx, world);
            if (recipe == null) {
                ctx.setOutput("out", ItemStack.EMPTY);
                ctx.setOutput("type", null);
                ctx.setOutput("found", false);
                return;
            }
            ctx.setOutput("out", resultOf(recipe, SlotDisplayContext.fromLevel(world)));
            ctx.setOutput("type", BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()));
            ctx.setOutput("found", true);
        }
    }

    /**
     * What one named recipe needs.
     *
     * <p>One entry per slot that requires something; empty slots in a shaped recipe are skipped, so the
     * list reads as a shopping list rather than as a grid. The shape itself is not reported — a graph that
     * needs it is really trying to fill a table, and {@code mc_crafting_result} answers that question from
     * the other end by simply trying the arrangement.
     *
     * <p>A slot can accept any of several items: {@code #minecraft:planks} is one slot, six answers.
     * {@code out} names the first of them and {@code choices} says how many there were, so a graph can see
     * a slot is a tag rather than silently committing to oak. The full set is one
     * {@code mc_items_in_tag} away when the recipe's tag is known.</p>
     */
    @NodeAttribute(name = "mc_recipe_ingredients", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class RecipeIngredients extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_recipe_ingredients.tooltip");
        }

        @InputPort public Level level;
        @InputPort public Identifier id;
        @OutputPort public List<?> out;
        @OutputPort public List<?> choices;
        @OutputPort public int count;
        @OutputPort public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            Recipe<?> recipe = lookup(ctx, ctx.getInput("level", Level.class, null));
            List<Item> items = new ArrayList<>();
            List<Integer> choices = new ArrayList<>();
            if (recipe != null) {
                for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
                    // Guard first: an empty ingredient would otherwise pay for a stream and a list
                    // it never reads.
                    if (ingredient.isEmpty()) continue;
                    var accepted = ingredient.items().toList();
                    if (accepted.isEmpty()) continue;
                    items.add(accepted.getFirst().value());
                    choices.add(accepted.size());
                }
            }
            ctx.setOutput("out", items);
            ctx.setOutput("choices", choices);
            ctx.setOutput("count", items.size());
            ctx.setOutput("found", recipe != null);
        }
    }

    /**
     * The recipe named by the {@code id} input, or null.
     *
     * <p>Shared by the two by-id nodes. {@link RecipeManager#byKey} is a plain map lookup, so unlike
     * {@code mc_recipes_for} this costs nothing.</p>
     */
    @Nullable
    private static Recipe<?> lookup(EvalContext ctx, @Nullable Level world) {
        Identifier id = ctx.getInput("id", Identifier.class, null);
        if (world == null || id == null) return null;
        var manager = recipes(world);
        return manager == null ? null : manager
                .byKey(ResourceKey.create(Registries.RECIPE, id))
                .map(RecipeHolder::value)
                .orElse(null);
    }

    /**
     * The server's recipe manager, or null.
     *
     * <p>26.1 took {@code getRecipeManager()} off {@code Level}: the full recipe set only ever exists
     * server-side, and {@code Level.recipeAccess()} exposes just the property sets the client is sent.
     * A graph evaluated on a client level therefore has no recipes to look at, which is what null
     * means here and why every caller treats it as "not found" rather than as an error.</p>
     */
    @Nullable
    private static RecipeManager recipes(@Nullable Level world) {
        return world == null || world.getServer() == null ? null : world.getServer().getRecipeManager();
    }

    /**
     * What {@code recipe} produces.
     *
     * <p>26.1 removed {@code getResultItem(RegistryAccess)} in favour of the recipe-display system: a
     * recipe publishes one or more {@code RecipeDisplay}s, and a display's result is a
     * {@code SlotDisplay} that has to be resolved against the level (some results depend on fuel
     * values or on datapack registries). The first display is the canonical one — that is the one the
     * recipe book shows — and a recipe with none has nothing to report, hence EMPTY.</p>
     */
    private static ItemStack resultOf(Recipe<?> recipe, ContextMap displayContext) {
        var displays = recipe.display();
        return displays.isEmpty() ? ItemStack.EMPTY
                : displays.getFirst().result().resolveForFirstStack(displayContext);
    }
}
