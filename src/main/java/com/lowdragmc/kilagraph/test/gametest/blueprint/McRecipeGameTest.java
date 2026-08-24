package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.block.MiningNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.loot.LootNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.recipe.CookingType;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.recipe.RecipeNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.tag.TagContentsNodes;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector2f;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * Recipes, loot tables and mining queries — everything that asks the game "what would this produce".
 *
 * <p>All of it is datapack content, so the assertions are against vanilla's own numbers: four planks really
 * do make a crafting table, raw iron really does smelt in 200 ticks, and stone really does drop nothing to a
 * bare hand. A node that returned a constant, or read the wrong field, disagrees with the game rather than
 * with a number invented here.
 *
 * <p>Where an answer is random — the loot rolls — the assertion is on a table whose outcome is forced, so
 * that the test pins the wiring instead of the dice.
 */
public final class McRecipeGameTest {
    private static final String CRAFTING_RESULT_RESPECTS_THE_GRID = "mc_recipe_crafting_result_respects_the_grid";
    private static final String CRAFTING_RESULT_ASSEMBLES_COMPUTED_RECIPES = "mc_recipe_crafting_result_assembles_computed_recipes";
    private static final String SMELTING_REPORTS_RESULT_TIME_AND_EXPERIENCE = "mc_recipe_smelting_reports_result_time_and_experience";
    private static final String RECIPES_ARE_FOUND_BY_RESULT_AND_BY_ID = "mc_recipe_recipes_are_found_by_result_and_by_id";
    private static final String INGREDIENTS_NAME_ONE_ITEM_PER_SLOT = "mc_recipe_ingredients_name_one_item_per_slot";
    private static final String LOOT_TABLE_ROLLS_ARE_DRIVEN_BY_THE_TOOL = "mc_recipe_loot_table_rolls_are_driven_by_the_tool";
    private static final String BLOCK_DROPS_DEPEND_ON_THE_TOOL = "mc_recipe_block_drops_depend_on_the_tool";
    private static final String MINING_QUERIES_COMPARE_PICKAXES = "mc_recipe_mining_queries_compare_pickaxes";
    private static final String DESTROY_SPEED_USES_THE_PLAYER_WHEN_GIVEN_ONE = "mc_recipe_destroy_speed_uses_the_player_when_given_one";
    private static final String INSTANT_BLOCKS_AGREE_ON_BOTH_PATHS = "mc_recipe_instant_blocks_agree_on_both_paths";

    public static void registerFunctions() {
        KGGameTests.registerFunction(CRAFTING_RESULT_RESPECTS_THE_GRID, McRecipeGameTest::craftingResultRespectsTheGrid);
        KGGameTests.registerFunction(CRAFTING_RESULT_ASSEMBLES_COMPUTED_RECIPES, McRecipeGameTest::craftingResultAssemblesComputedRecipes);
        KGGameTests.registerFunction(SMELTING_REPORTS_RESULT_TIME_AND_EXPERIENCE, McRecipeGameTest::smeltingReportsResultTimeAndExperience);
        KGGameTests.registerFunction(RECIPES_ARE_FOUND_BY_RESULT_AND_BY_ID, McRecipeGameTest::recipesAreFoundByResultAndById);
        KGGameTests.registerFunction(INGREDIENTS_NAME_ONE_ITEM_PER_SLOT, McRecipeGameTest::ingredientsNameOneItemPerSlot);
        KGGameTests.registerFunction(LOOT_TABLE_ROLLS_ARE_DRIVEN_BY_THE_TOOL, McRecipeGameTest::lootTableRollsAreDrivenByTheTool);
        KGGameTests.registerFunction(BLOCK_DROPS_DEPEND_ON_THE_TOOL, McRecipeGameTest::blockDropsDependOnTheTool);
        KGGameTests.registerFunction(MINING_QUERIES_COMPARE_PICKAXES, McRecipeGameTest::miningQueriesComparePickaxes);
        KGGameTests.registerFunction(DESTROY_SPEED_USES_THE_PLAYER_WHEN_GIVEN_ONE, McRecipeGameTest::destroySpeedUsesThePlayerWhenGivenOne);
        KGGameTests.registerFunction(INSTANT_BLOCKS_AGREE_ON_BOTH_PATHS, McRecipeGameTest::instantBlocksAgreeOnBothPaths);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{
                CRAFTING_RESULT_RESPECTS_THE_GRID, CRAFTING_RESULT_ASSEMBLES_COMPUTED_RECIPES, SMELTING_REPORTS_RESULT_TIME_AND_EXPERIENCE,
                RECIPES_ARE_FOUND_BY_RESULT_AND_BY_ID, INGREDIENTS_NAME_ONE_ITEM_PER_SLOT, LOOT_TABLE_ROLLS_ARE_DRIVEN_BY_THE_TOOL,
                BLOCK_DROPS_DEPEND_ON_THE_TOOL, MINING_QUERIES_COMPARE_PICKAXES, DESTROY_SPEED_USES_THE_PLAYER_WHEN_GIVEN_ONE,
                INSTANT_BLOCKS_AGREE_ON_BOTH_PATHS
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    private static final Identifier CRAFTING_TABLE = Identifier.parse("minecraft:crafting_table");
    private static final Identifier NOT_A_THING = Identifier.parse("kilagraph:nope");

    private McRecipeGameTest() {
    }

    // ---- crafting ------------------------------------------------------------------------------

    /**
     * The 2x2 plank recipe, and the shape sensitivity that makes width and height real inputs.
     *
     * <p>The 1x4 case is the load-bearing half: the same four planks in a line are not a crafting table, so
     * a node that ignored the grid shape would pass the first assertion and fail this one.</p>
     */
    public static void craftingResultRespectsTheGrid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<ItemStack> fourPlanks = List.of(
                new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS),
                new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS));

        var table = craft(level, fourPlanks, 2, 2);
        assertTrue(helper, "four planks in a square is a recipe", table.eval("found", Boolean.class));
        assertEq(helper, "and it is a crafting table", Items.CRAFTING_TABLE,
                table.eval("out", ItemStack.class).getItem());
        assertEq(helper, "named as such", CRAFTING_TABLE, table.eval("recipeId", Identifier.class));

        var line = craft(level, fourPlanks, 1, 4);
        assertFalse(helper, "the same planks in a line are not", line.eval("found", Boolean.class));
        assertTrue(helper, "and produce nothing", line.eval("out", ItemStack.class).isEmpty());

        // A shorter list than the grid is padded rather than refused: one log in a 3x3 is still a log.
        var log = craft(level, List.of(new ItemStack(Items.OAK_LOG)), 3, 3);
        assertTrue(helper, "one log in a big grid still matches", log.eval("found", Boolean.class));
        assertEq(helper, "giving planks", Items.OAK_PLANKS, log.eval("out", ItemStack.class).getItem());
        assertEq(helper, "four of them", 4, log.eval("out", ItemStack.class).getCount());

        assertFalse(helper, "a diamond crafts into nothing",
                craft(level, List.of(new ItemStack(Items.DIAMOND)), 1, 1).eval("found", Boolean.class));
        assertFalse(helper, "a zero-sized grid is refused",
                craft(level, fourPlanks, 0, 2).eval("found", Boolean.class));

        // 65536 by 65536 is exactly the pair whose product overflows an int to zero. Checked as ints it
        // slips under the ceiling and the game's grid builder then indexes off the end of the padded list,
        // so this asks for the one grid size that used to throw instead of being refused.
        assertFalse(helper, "a grid whose area overflows an int is refused",
                craft(level, fourPlanks, 65536, 65536).eval("found", Boolean.class));
        helper.succeed();
    }

    /**
     * The recipe that proves {@code assemble} is being called and not {@code getResultItem}.
     *
     * <p>Armour dyeing declares no output at all — {@code getResultItem} on it is an empty stack, because
     * the colour depends on which dyes went in. So a node reading the declared result would report nothing
     * here, and the only way to get a dyed helmet out is to assemble the recipe against the actual grid.
     * That choice is written down in {@code RecipeNodes}, and this is what holds it up.
     *
     * <p>The id is taken from the node's own output rather than hard-coded, which also lets the second half
     * assert the other side of the same coin: {@code mc_recipe_by_id} on that very recipe reports it exists
     * and produces nothing, because by then there is no grid left to assemble from.</p>
     */
    public static void craftingResultAssemblesComputedRecipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        var dyed = craft(level, List.of(new ItemStack(Items.LEATHER_HELMET), new ItemStack(Items.RED_DYE)), 2, 1);
        assertTrue(helper, "dyeing is a recipe", dyed.eval("found", Boolean.class));
        ItemStack helmet = dyed.eval("out", ItemStack.class);
        assertEq(helper, "and gives back the helmet", Items.LEATHER_HELMET, helmet.getItem());
        assertTrue(helper, "with a colour on it, which only assemble produces",
                helmet.has(DataComponents.DYED_COLOR));

        Identifier id = dyed.eval("recipeId", Identifier.class);
        assertTrue(helper, "the recipe was named", id != null);

        var declared = probe(level, RecipeNodes.RecipeById.class, "id", id);
        assertTrue(helper, "the same recipe is found by id", declared.eval("found", Boolean.class));
        // 26.1 answers this where 1.21.1 did not: getResultItem() had nothing to say about a special
        // crafting recipe, so the by-id node reported EMPTY, while the recipe-display system that
        // replaced it publishes a result. So the assertion is that the two agree on the item rather
        // than that one of them is empty.
        ItemStack byId = declared.eval("out", ItemStack.class);
        assertEq(helper, "and its declared result is the same item assemble produced",
                helmet.getItem(), byId.getItem());
        helper.succeed();
    }

    // ---- cooking -------------------------------------------------------------------------------

    /** Each of the four cooking blocks, including one that has no recipe for the item. */
    public static void smeltingReportsResultTimeAndExperience(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack rawIron = new ItemStack(Items.RAW_IRON);

        var furnace = cook(level, CookingType.SMELTING, rawIron);
        assertTrue(helper, "raw iron smelts", furnace.eval("found", Boolean.class));
        assertEq(helper, "into an ingot", Items.IRON_INGOT, furnace.eval("out", ItemStack.class).getItem());
        assertEq(helper, "in ten seconds", 200, furnace.eval("time", Integer.class).intValue());
        assertEq(helper, "paying 0.7 experience", 0.7f, furnace.eval("experience", Float.class), 1e-4f);

        var blast = cook(level, CookingType.BLASTING, rawIron);
        assertTrue(helper, "and blasts too", blast.eval("found", Boolean.class));
        assertEq(helper, "in half the time", 100, blast.eval("time", Integer.class).intValue());
        assertEq(helper, "for the same result", Items.IRON_INGOT, blast.eval("out", ItemStack.class).getItem());

        // The smoker is food only, which is what makes the option a real input rather than decoration.
        var smoker = cook(level, CookingType.SMOKING, rawIron);
        assertFalse(helper, "but a smoker will not take ore", smoker.eval("found", Boolean.class));
        assertEq(helper, "and reports no time", 0, smoker.eval("time", Integer.class).intValue());

        var campfire = cook(level, CookingType.CAMPFIRE, new ItemStack(Items.BEEF));
        assertTrue(helper, "a campfire cooks beef", campfire.eval("found", Boolean.class));
        assertEq(helper, "into steak", Items.COOKED_BEEF, campfire.eval("out", ItemStack.class).getItem());
        assertEq(helper, "slowly", 600, campfire.eval("time", Integer.class).intValue());

        assertFalse(helper, "an empty input is refused",
                cook(level, CookingType.SMELTING, ItemStack.EMPTY).eval("found", Boolean.class));
        helper.succeed();
    }

    // ---- recipe lookup -------------------------------------------------------------------------

    public static void recipesAreFoundByResultAndById(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        var producing = probe(level, RecipeNodes.RecipesFor.class, "item", Items.CRAFTING_TABLE);
        List<?> ids = producing.eval("out", List.class);
        assertTrue(helper, "something makes a crafting table", ids.contains(CRAFTING_TABLE));
        assertEq(helper, "count matches the list", ids.size(),
                producing.eval("count", Integer.class).intValue());

        // Sorted, because the recipe manager's own iteration order is a hash order and would vary per run.
        List<String> asText = new ArrayList<>();
        for (Object id : ids) asText.add(id.toString());
        List<String> sorted = new ArrayList<>(asText);
        sorted.sort(Comparator.naturalOrder());
        assertEq(helper, "the ids come out sorted", sorted, asText);

        assertEq(helper, "nothing crafts bedrock", 0,
                probe(level, RecipeNodes.RecipesFor.class, "item", Items.BEDROCK)
                        .eval("count", Integer.class).intValue());

        var byId = probe(level, RecipeNodes.RecipeById.class, "id", CRAFTING_TABLE);
        assertTrue(helper, "the recipe is there", byId.eval("found", Boolean.class));
        assertEq(helper, "producing a crafting table", Items.CRAFTING_TABLE,
                byId.eval("out", ItemStack.class).getItem());
        assertEq(helper, "and it is a crafting recipe", Identifier.parse("minecraft:crafting"),
                byId.eval("type", Identifier.class));

        var unknown = probe(level, RecipeNodes.RecipeById.class, "id", NOT_A_THING);
        assertFalse(helper, "an unknown recipe id is not found", unknown.eval("found", Boolean.class));
        assertTrue(helper, "and yields nothing", unknown.eval("out", ItemStack.class).isEmpty());
        helper.succeed();
    }

    /**
     * Ingredients, cross-checked against the tag they come from.
     *
     * <p>The crafting table takes {@code #minecraft:planks} four times, so every slot must report more than
     * one acceptable item and every representative must be a member of that tag. A node that flattened the
     * ingredient list, or that reported one plank as the only option, fails one of those two.</p>
     */
    public static void ingredientsNameOneItemPerSlot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        var ingredients = probe(level, RecipeNodes.RecipeIngredients.class, "id", CRAFTING_TABLE);
        assertTrue(helper, "the recipe is there", ingredients.eval("found", Boolean.class));
        List<?> items = ingredients.eval("out", List.class);
        List<?> choices = ingredients.eval("choices", List.class);
        assertEq(helper, "four slots", 4, items.size());
        assertEq(helper, "count matches", 4, ingredients.eval("count", Integer.class).intValue());
        assertEq(helper, "one choice count per slot", items.size(), choices.size());

        List<?> planks = probe(level, TagContentsNodes.ItemsInTag.class,
                "tag", Identifier.parse("minecraft:planks")).eval("out", List.class);
        for (int i = 0; i < items.size(); i++) {
            assertTrue(helper, "slot " + i + " names a plank", planks.contains(items.get(i)));
            assertTrue(helper, "slot " + i + " is a tag, so it accepts several, got " + choices.get(i),
                    ((Integer) choices.get(i)) > 1);
        }

        var unknown = probe(level, RecipeNodes.RecipeIngredients.class, "id", NOT_A_THING);
        assertFalse(helper, "an unknown recipe id is not found", unknown.eval("found", Boolean.class));
        assertEq(helper, "and lists nothing", 0, unknown.eval("count", Integer.class).intValue());
        helper.succeed();
    }

    // ---- loot ----------------------------------------------------------------------------------

    /**
     * A loot table whose outcome is forced, so the test pins the wiring rather than the dice.
     *
     * <p>{@code blocks/stone} is exactly two outcomes with a condition between them: silk touch gives stone,
     * anything else gives cobblestone. That makes it the one vanilla table a test can assert an exact result
     * from, and it also exercises the {@code tool} input, since the condition reads it.</p>
     */
    public static void lootTableRollsAreDrivenByTheTool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        Identifier stoneTable = Identifier.parse("minecraft:blocks/stone");

        var plain = probe(level, LootNodes.Roll.class,
                "table", stoneTable, "pos", at, "tool", new ItemStack(Items.IRON_PICKAXE));
        assertTrue(helper, "the table exists", plain.eval("found", Boolean.class));
        assertTrue(helper, "and rolled", plain.eval("ok", Boolean.class));
        List<?> dropped = plain.eval("out", List.class);
        assertEq(helper, "giving one item", 1, dropped.size());
        assertEq(helper, "count matches the list", 1, plain.eval("count", Integer.class).intValue());
        assertEq(helper, "which is cobblestone", Items.COBBLESTONE, ((ItemStack) dropped.getFirst()).getItem());

        var silk = probe(level, LootNodes.Roll.class,
                "table", stoneTable, "pos", at, "tool", silkTouch(level));
        assertEq(helper, "silk touch gives the stone itself", Items.STONE,
                ((ItemStack) silk.eval("out", List.class).getFirst()).getItem());

        var unknown = probe(level, LootNodes.Roll.class, "table", NOT_A_THING, "pos", at);
        assertFalse(helper, "an unknown table is not found", unknown.eval("found", Boolean.class));
        assertFalse(helper, "and did not roll", unknown.eval("ok", Boolean.class));
        assertEq(helper, "producing nothing", 0, unknown.eval("count", Integer.class).intValue());
        helper.succeed();
    }

    /**
     * Block drops, including the bare-handed case the loot table alone gets wrong.
     *
     * <p>{@code blocks/stone} has no pickaxe condition in it — the harvest rule lives outside the table — so
     * a node that only rolled the table would report cobblestone here. That is what the empty-handed
     * assertion pins.</p>
     */
    public static void blockDropsDependOnTheTool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(at, Blocks.STONE.defaultBlockState(), 3);

        var mined = probe(level, LootNodes.BlockDrops.class,
                "pos", at, "tool", new ItemStack(Items.IRON_PICKAXE));
        assertTrue(helper, "the query worked", mined.eval("ok", Boolean.class));
        assertTrue(helper, "a pickaxe harvests stone", mined.eval("harvestable", Boolean.class));
        assertEq(helper, "and it reports the block it read", Blocks.STONE,
                mined.eval("state", BlockState.class).getBlock());
        List<?> drops = mined.eval("out", List.class);
        assertEq(helper, "one drop", 1, drops.size());
        assertEq(helper, "which is cobblestone", Items.COBBLESTONE, ((ItemStack) drops.getFirst()).getItem());

        var barehanded = probe(level, LootNodes.BlockDrops.class, "pos", at, "tool", ItemStack.EMPTY);
        assertFalse(helper, "a hand does not harvest stone", barehanded.eval("harvestable", Boolean.class));
        assertEq(helper, "so nothing drops", 0, barehanded.eval("count", Integer.class).intValue());

        // Dirt needs no tool at all, which is the other side of the harvest rule.
        BlockPos dirt = helper.absolutePos(new BlockPos(3, 2, 2));
        level.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 3);
        var dug = probe(level, LootNodes.BlockDrops.class, "pos", dirt, "tool", ItemStack.EMPTY);
        assertTrue(helper, "dirt needs no tool", dug.eval("harvestable", Boolean.class));
        assertEq(helper, "and drops itself", Items.DIRT,
                ((ItemStack) dug.eval("out", List.class).getFirst()).getItem());

        // Air is harvestable and drops nothing, which is a real answer rather than a failure.
        BlockPos air = helper.absolutePos(new BlockPos(4, 2, 2));
        level.setBlock(air, Blocks.AIR.defaultBlockState(), 3);
        var nothing = probe(level, LootNodes.BlockDrops.class, "pos", air, "tool", ItemStack.EMPTY);
        assertTrue(helper, "reading air still works", nothing.eval("ok", Boolean.class));
        assertEq(helper, "and drops nothing", 0, nothing.eval("count", Integer.class).intValue());
        helper.succeed();
    }

    // ---- mining --------------------------------------------------------------------------------

    public static void miningQueriesComparePickaxes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos stone = helper.absolutePos(new BlockPos(1, 2, 3));
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);

        var wood = probe(level, MiningNodes.DestroySpeed.class,
                "pos", stone, "tool", new ItemStack(Items.WOODEN_PICKAXE));
        var diamond = probe(level, MiningNodes.DestroySpeed.class,
                "pos", stone, "tool", new ItemStack(Items.DIAMOND_PICKAXE));
        assertEq(helper, "stone's hardness", 1.5f, wood.eval("hardness", Float.class), 1e-4f);
        assertFalse(helper, "and it is breakable", wood.eval("unbreakable", Boolean.class));
        assertFalse(helper, "no player, so this is the estimate", wood.eval("exact", Boolean.class));
        int woodTicks = wood.eval("ticks", Integer.class);
        int diamondTicks = diamond.eval("ticks", Integer.class);
        assertTrue(helper, "both take some time, got " + woodTicks + " and " + diamondTicks,
                woodTicks > 0 && diamondTicks > 0);
        assertTrue(helper, "and diamond is faster than wood, got " + diamondTicks + " vs " + woodTicks,
                diamondTicks < woodTicks);
        assertTicksMatchProgress(helper, "wood", wood);
        assertTicksMatchProgress(helper, "diamond", diamond);

        // Bedrock is the -1 case: not slow, impossible.
        BlockPos bedrock = helper.absolutePos(new BlockPos(2, 2, 3));
        level.setBlock(bedrock, Blocks.BEDROCK.defaultBlockState(), 3);
        var never = probe(level, MiningNodes.DestroySpeed.class,
                "pos", bedrock, "tool", new ItemStack(Items.DIAMOND_PICKAXE));
        assertTrue(helper, "bedrock is unbreakable", never.eval("unbreakable", Boolean.class));
        assertEq(helper, "and reports -1 rather than a huge number", -1,
                never.eval("ticks", Integer.class).intValue());

        // Harvestability is a separate question with separate inputs.
        var withPick = probe(level, MiningNodes.CanHarvest.class,
                "state", Blocks.STONE.defaultBlockState(), "tool", new ItemStack(Items.WOODEN_PICKAXE));
        assertTrue(helper, "wood harvests stone", withPick.eval("out", Boolean.class));
        assertTrue(helper, "which required a tool", withPick.eval("requiresTool", Boolean.class));

        assertFalse(helper, "a shovel does not",
                probe(level, MiningNodes.CanHarvest.class, "state", Blocks.STONE.defaultBlockState(),
                        "tool", new ItemStack(Items.DIAMOND_SHOVEL)).eval("out", Boolean.class));

        var soft = probe(level, MiningNodes.CanHarvest.class,
                "state", Blocks.DIRT.defaultBlockState(), "tool", ItemStack.EMPTY);
        assertTrue(helper, "dirt harvests with a hand", soft.eval("out", Boolean.class));
        assertFalse(helper, "because it needs no tool", soft.eval("requiresTool", Boolean.class));

        assertFalse(helper, "iron does not harvest diamond ore",
                probe(level, MiningNodes.CanHarvest.class, "state", Blocks.DIAMOND_ORE.defaultBlockState(),
                        "tool", new ItemStack(Items.STONE_PICKAXE)).eval("out", Boolean.class));
        helper.succeed();
    }

    /**
     * The accurate path: with a player wired, the answer comes from the game rather than from the estimate.
     *
     * <p>The load-bearing case is the last one. The node documents that {@code tool} is ignored once a
     * player is given, because the game uses the player's own held item — so an empty-handed player holding
     * nothing, with a diamond pickaxe on the {@code tool} port, must come back slow. A node that fell back
     * to the tool, or that mixed the two, gets that one wrong while passing everything above it.
     *
     * <p>No exact tick counts here: the game's formula runs in floats and pinning 150 rather than 151 would
     * be testing rounding. The relationships are what the node promises.</p>
     */
    public static void destroySpeedUsesThePlayerWhenGivenOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos stone = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);

        Player barehanded = helper.makeMockPlayer(GameType.SURVIVAL);
        var byHand = probe(level, MiningNodes.DestroySpeed.class, "pos", stone, "player", barehanded);
        assertTrue(helper, "a player makes the answer exact", byHand.eval("exact", Boolean.class));
        assertTrue(helper, "and it still takes time", byHand.eval("ticks", Integer.class) > 0);
        assertTicksMatchProgress(helper, "bare-handed player", byHand);

        Player armed = helper.makeMockPlayer(GameType.SURVIVAL);
        armed.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        var byPick = probe(level, MiningNodes.DestroySpeed.class, "pos", stone, "player", armed);
        assertTrue(helper, "holding a pickaxe is faster, got " + byPick.eval("ticks", Integer.class)
                        + " vs " + byHand.eval("ticks", Integer.class),
                byPick.eval("ticks", Integer.class) < byHand.eval("ticks", Integer.class));

        // The tool port is ignored once a player is given: what the player holds is what counts.
        var confused = probe(level, MiningNodes.DestroySpeed.class, "pos", stone,
                "player", barehanded, "tool", new ItemStack(Items.DIAMOND_PICKAXE));
        assertEq(helper, "a pickaxe on the tool port does not speed up an empty-handed player",
                byHand.eval("ticks", Integer.class).intValue(),
                confused.eval("ticks", Integer.class).intValue());
        helper.succeed();
    }

    /**
     * A block of zero hardness, asked both ways, must give the same answer.
     *
     * <p>The game's formula divides by hardness, so air and torches make it {@code Infinity}. The two paths
     * used to disagree about that — the estimate said one tick, the player path said zero and put an
     * infinity on the {@code progress} port for whatever arithmetic came next. Both now short-circuit to one
     * tick, which is true (nothing breaks in less) and finite.</p>
     */
    public static void instantBlocksAgreeOnBothPaths(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos air = helper.absolutePos(new BlockPos(5, 2, 3));
        level.setBlock(air, Blocks.AIR.defaultBlockState(), 3);

        var estimated = probe(level, MiningNodes.DestroySpeed.class, "pos", air);
        var asked = probe(level, MiningNodes.DestroySpeed.class,
                "pos", air, "player", helper.makeMockPlayer(GameType.SURVIVAL));

        assertEq(helper, "air has no hardness", 0f, estimated.eval("hardness", Float.class), 1e-6f);
        for (var probe : List.of(estimated, asked)) {
            float progress = probe.eval("progress", Float.class);
            assertTrue(helper, "progress stays finite, got " + progress,
                    !Float.isInfinite(progress) && !Float.isNaN(progress));
            assertEq(helper, "and it breaks in one tick", 1, probe.eval("ticks", Integer.class).intValue());
            assertFalse(helper, "which is not the same as unbreakable",
                    probe.eval("unbreakable", Boolean.class));
        }
        assertFalse(helper, "the estimate knows it is an estimate", estimated.eval("exact", Boolean.class));
        assertTrue(helper, "and the asked one knows it is not", asked.eval("exact", Boolean.class));
        helper.succeed();
    }

    /** {@code ticks} must be the whole number of ticks {@code progress} implies, on both paths. */
    private static void assertTicksMatchProgress(GameTestHelper helper, String label, Probe probe) {
        float progress = probe.eval("progress", Float.class);
        assertTrue(helper, label + " makes progress, got " + progress, progress > 0f);
        assertEq(helper, label + " ticks agree with progress",
                (int) Math.ceil(1f / progress), probe.eval("ticks", Integer.class).intValue());
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static ItemStack silkTouch(ServerLevel level) {
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        Holder<Enchantment> silk = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH);
        pick.enchant(silk, 1);
        return pick;
    }

    private static Probe cook(ServerLevel level, CookingType type, ItemStack input) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, RecipeNodes.SmeltingResult.class);
        setOption(n, "cookingType", type);
        setInputConstant(n, "stack", input);
        wireLevel(g, n);
        return new Probe(new GraphExecutor(g, EvaluationEnvironment.with(Map.of("level", level))), n);
    }

    /**
     * {@code mc_crafting_result} over a grid.
     *
     * <p>The item list arrives through a graph variable rather than an input constant: a {@code LIST} port
     * carries no embedded constant — there is no accessor for {@code List}, so it takes the
     * no-configurator path — and {@code setInputConstant} on it fails outright.</p>
     */
    private static Probe craft(ServerLevel level, List<ItemStack> items, int width, int height) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, RecipeNodes.CraftingResult.class);
        setInputConstant(n, "width", width);
        setInputConstant(n, "height", height);
        wireLevel(g, n);

        var v = (VariableDeclarationModelBase)
                g.graphModel.createVariable("items", KGTypeHandles.LIST, null, VariableKind.INPUT);
        wire(g, n.getInputsById().get("items"),
                g.graphModel.createVariableNode(v, new Vector2f(0, 0), null, null).getOutputPort());

        return new Probe(new GraphExecutor(g,
                EvaluationEnvironment.with(Map.of("level", level, "items", items))), n);
    }

    private record Probe(GraphExecutor exec, NodeModel node) {
        <T> T eval(String output, Class<T> type) {
            return exec.evaluate(node.getOutputsById().get(output), type);
        }
    }

    /** A node in its own graph with the level wired from the environment and the given input constants. */
    private static Probe probe(ServerLevel level, Class<? extends Node> cls, Object... inputs) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, cls);
        for (int i = 0; i + 1 < inputs.length; i += 2) {
            setInputConstant(n, (String) inputs[i], inputs[i + 1]);
        }
        wireLevel(g, n);
        return new Probe(new GraphExecutor(g, EvaluationEnvironment.with(Map.of("level", level))), n);
    }

    private static void wireLevel(BlueprintGraph g, NodeModel n) {
        PortModel levelPort = n.getInputsById().get("level");
        if (levelPort == null) return;
        var v = (VariableDeclarationModelBase)
                g.graphModel.createVariable("level", KGTypeHandles.LEVEL, null, VariableKind.INPUT);
        wire(g, levelPort, g.graphModel.createVariableNode(v, new Vector2f(0, 0), null, null).getOutputPort());
    }
}
