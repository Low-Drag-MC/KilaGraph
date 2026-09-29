package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.BranchNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ForEachNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ForNode;
import com.lowdragmc.kilagraph.blueprint.nodes.convert.ParseNumberNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListContainsNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListGetNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.action.BlockActionNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.action.EntityActionNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.action.RunCommandNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.action.WorldEffectNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.block.BlockStateNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.block.MiningNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.EntityDataNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.gameplay.PotionNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.geometry.BlockPosCreateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.geometry.BlockPosNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.id.McIdNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.item.ItemStackCreateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.item.ItemStackNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.loot.LootNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtCreateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtPathGetNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtPathSetNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtValueType;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.recipe.RecipeNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.tag.McTagNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.tag.TagContentsNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.text.TextNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.world.GetBlockStateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.world.WorldQueryNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.string.FindNode;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector2f;

import java.util.Map;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * Whole graphs running against a live {@link ServerLevel}.
 *
 * <p>Every other MC test in this suite drives one node at a time with constant inputs. That finds a node
 * that computes the wrong value; it cannot find the things that only go wrong when the pieces are
 * assembled — a loop whose body reads a stale cached value, an action that never re-resolves its position,
 * a list of entities that goes flat after the first iteration. These tests build the graph a user would
 * build and then assert on <b>the world</b>, not on a port.
 *
 * <h2>The one worth reading first</h2>
 * {@link #buildsAColumn} exists because of a specific failure mode. The executor memoises data values, so
 * a loop body whose position depends on the loop index is only correct if the cache is invalidated per
 * iteration. If it were not, all five blocks would land on the same spot and every per-node test would
 * still pass. That is the class of bug integration coverage is for.
 */
@GameTestHolder(Kilagraph.MODID)
public final class McIntegrationGameTest {

    private McIntegrationGameTest() {
    }

    /**
     * A For loop building a column, with the height computed from the loop index.
     *
     * <p>Entry → For(5) → body → Set Block, where the position comes from
     * {@code index + baseY} through Add and Block Pos Create. Five distinct blocks, five distinct
     * positions, one graph.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void buildsAColumn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(0, 2, 0));
        for (int i = 0; i < 5; i++) {
            level.setBlock(base.above(i), Blocks.AIR.defaultBlockState(), 3);
        }

        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var loop = addNode(g, ForNode.class);
        setInputConstant(loop, "count", 5);

        // y = index + base.getY()
        var add = addNode(g, AddNode.class);
        wire(g, add.getInputsById().get("in1"), loop.getOutputsById().get("index"));
        setInputConstant(add, "in2", base.getY());

        var pos = addNode(g, BlockPosCreateNode.class);
        setInputConstant(pos, "x", base.getX());
        setInputConstant(pos, "z", base.getZ());
        wire(g, pos.getInputsById().get("y"), add.getOutputsById().get("out"));

        var set = addNode(g, BlockActionNodes.SetBlock.class);
        wire(g, set.getInputsById().get("pos"), pos.getOutputsById().get("out"));
        setInputConstant(set, "state", Blocks.GOLD_BLOCK.defaultBlockState());

        var levelVar = declareLevel(g);
        wire(g, set.getInputsById().get("level"), levelVar);
        wire(g, loop.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, set.getInputsById().get("trigger"), loop.getOutputsById().get("body"));

        run(g, level, entry);

        // Every one of the five, at its own height — this is the assertion that a stale cache breaks.
        for (int i = 0; i < 5; i++) {
            assertEq(helper, "column block at +" + i, Blocks.GOLD_BLOCK,
                    level.getBlockState(base.above(i)).getBlock());
        }
        assertTrue(helper, "and nothing above the column",
                level.getBlockState(base.above(5)).isAir());
        helper.succeed();
    }

    /**
     * A world query feeding a For Each that acts on some items and not others.
     *
     * <p>Entities In Box → For Each → Is Type(pig) → Branch → Damage. Two entities go in, one comes out
     * hurt. This is the shape almost every real blueprint has, and it exercises the loop item flowing into
     * two separate nodes in the body.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void damagesOnlyTheMatchingEntities(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LivingEntity pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        LivingEntity cow = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2));
        float pigFull = pig.getHealth();
        float cowFull = cow.getHealth();

        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var levelVar = declareLevel(g);

        var inBox = addNode(g, WorldQueryNodes.EntitiesInBox.class);
        wire(g, inBox.getInputsById().get("level"), levelVar);
        setInputConstant(inBox, "box", new AABB(pig.blockPosition()).inflate(16));

        var each = addNode(g, ForEachNode.class);
        wire(g, each.getInputsById().get("list"), inBox.getOutputsById().get("out"));

        var isPig = addNode(g, EntityDataNodes.IsType.class);
        wire(g, isPig.getInputsById().get("entity"), each.getOutputsById().get("item"));
        setInputConstant(isPig, "type", EntityType.PIG);

        var branch = addNode(g, BranchNode.class);
        wire(g, branch.getInputsById().get("cond"), isPig.getOutputsById().get("out"));

        var damage = addNode(g, EntityActionNodes.DamageEntity.class);
        wire(g, damage.getInputsById().get("entity"), each.getOutputsById().get("item"));
        setInputConstant(damage, "amount", 4f);

        wire(g, each.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, branch.getInputsById().get("in"), each.getOutputsById().get("body"));
        wire(g, damage.getInputsById().get("trigger"), branch.getOutputsById().get("trueExec"));

        run(g, level, entry);

        assertTrue(helper, "the pig was hurt, was " + pigFull + " now " + pig.getHealth(),
                pig.getHealth() < pigFull);
        assertEq(helper, "the cow was not touched", cowFull, cow.getHealth(), 0.01f);
        helper.succeed();
    }

    /**
     * Reading the world, deciding, and writing back — in one pass over a region.
     *
     * <p>Block Pos Between → For Each → Get Block State → Flags → Branch on {@code air} → Set Block. The
     * region is seeded half stone and half air, and only the air is filled. This is the read-modify-write
     * loop, and it is the one where a cached block state would produce a visibly wrong result: the first
     * position's answer applied to all four.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void fillsOnlyTheAirInARegion(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos min = helper.absolutePos(new BlockPos(0, 2, 0));
        BlockPos max = min.offset(3, 0, 0);
        // stone, air, stone, air
        level.setBlock(min, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(min.offset(1, 0, 0), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(min.offset(2, 0, 0), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(min.offset(3, 0, 0), Blocks.AIR.defaultBlockState(), 3);

        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var levelVar = declareLevel(g);

        var between = addNode(g, BlockPosNodes.Between.class);
        setInputConstant(between, "min", min);
        setInputConstant(between, "max", max);

        var each = addNode(g, ForEachNode.class);
        wire(g, each.getInputsById().get("list"), between.getOutputsById().get("out"));

        var getState = addNode(g, GetBlockStateNode.class);
        wire(g, getState.getInputsById().get("level"), levelVar);
        wire(g, getState.getInputsById().get("pos"), each.getOutputsById().get("item"));

        var flags = addNode(g, BlockStateNodes.Flags.class);
        wire(g, flags.getInputsById().get("in"), getState.getOutputsById().get("out"));

        var branch = addNode(g, BranchNode.class);
        wire(g, branch.getInputsById().get("cond"), flags.getOutputsById().get("air"));

        var set = addNode(g, BlockActionNodes.SetBlock.class);
        wire(g, set.getInputsById().get("level"), levelVar);
        wire(g, set.getInputsById().get("pos"), each.getOutputsById().get("item"));
        setInputConstant(set, "state", Blocks.GOLD_BLOCK.defaultBlockState());

        wire(g, each.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, branch.getInputsById().get("in"), each.getOutputsById().get("body"));
        wire(g, set.getInputsById().get("trigger"), branch.getOutputsById().get("trueExec"));

        run(g, level, entry);

        assertEq(helper, "stone at 0 untouched", Blocks.STONE, level.getBlockState(min).getBlock());
        assertEq(helper, "air at 1 filled", Blocks.GOLD_BLOCK,
                level.getBlockState(min.offset(1, 0, 0)).getBlock());
        assertEq(helper, "stone at 2 untouched", Blocks.STONE,
                level.getBlockState(min.offset(2, 0, 0)).getBlock());
        assertEq(helper, "air at 3 filled", Blocks.GOLD_BLOCK,
                level.getBlockState(min.offset(3, 0, 0)).getBlock());
        helper.succeed();
    }

    /**
     * A data component written by the graph, surviving into a world entity.
     *
     * <p>Item Stack Create → Set Custom Name → Drop Item, then the dropped entity is read back out of the
     * world and asked what it is holding. The component has to survive the copy the action makes and the
     * {@code ItemEntity}'s own handling of the stack, which no per-node test covers.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void itemComponentsSurviveIntoTheWorld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));

        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var levelVar = declareLevel(g);

        var stack = addNode(g, ItemStackCreateNode.class);
        setInputConstant(stack, "item", Items.DIAMOND);
        setInputConstant(stack, "count", 3);

        var text = addNode(g, TextNodes.Literal.class);
        setInputConstant(text, "text", "Graph Diamond");

        var named = addNode(g, ItemStackNodes.SetCustomName.class);
        wire(g, named.getInputsById().get("stack"), stack.getOutputsById().get("out"));
        wire(g, named.getInputsById().get("name"), text.getOutputsById().get("out"));

        var drop = addNode(g, WorldEffectNodes.DropItem.class);
        wire(g, drop.getInputsById().get("level"), levelVar);
        setInputConstant(drop, "pos", at);
        wire(g, drop.getInputsById().get("stack"), named.getOutputsById().get("out"));

        wire(g, drop.getInputsById().get("trigger"), entry.getOutputsById().get("next"));

        var exec = run(g, level, entry);

        assertTrue(helper, "the drop succeeded",
                exec.evaluate(drop.getOutputsById().get("ok"), Boolean.class));
        Entity dropped = exec.evaluate(drop.getOutputsById().get("entity"), Entity.class);
        assertTrue(helper, "and produced an item entity", dropped instanceof ItemEntity);

        var inWorld = ((ItemEntity) dropped).getItem();
        assertEq(helper, "holding diamonds", Items.DIAMOND, inWorld.getItem());
        assertEq(helper, "three of them", 3, inWorld.getCount());
        assertEq(helper, "with the custom name the graph gave it", "Graph Diamond",
                inWorld.getHoverName().getString());

        // The entity really is in the world, not just constructed.
        assertFalse(helper, "and it is not removed", dropped.isRemoved());
        assertTrue(helper, "and the world can find it",
                level.getEntities(null, new AABB(at).inflate(2)).contains(dropped));
        helper.succeed();
    }

    // ---- the nodes added in the tag/regex/potion/nbt/recipe/loot batches -----------------------
    //
    // Those thirty nodes each arrived with their own test, and every one of those tests drives a single
    // node with constant inputs. That is the shape this file's header warns about: it proves each node
    // computes the right answer in isolation and proves nothing about the answers travelling. What
    // follows wires them to each other, so a value that survives evaluation but not a wire — a list that
    // arrives as the wrong element type, an id that loses its namespace, a tag mutated in place and read
    // back stale — has somewhere to show up.

    /**
     * Command text turned back into a number, over wires.
     *
     * <p>This is the chain the regex nodes were added for, and until now it existed only in their docs.
     * {@code mc_run_command} produces a sentence, {@code string_find} pulls the digits out of it as a
     * capture group, {@code list_get} takes the group and {@code convert_parse_number} makes it a number.
     *
     * <p>The assertion ties the far end of that chain to the near end: the number parsed out of the
     * <b>text</b> must equal the integer the command returned through its own {@code result} port. Nothing
     * short of the whole chain working produces that agreement, and no per-node test can check it, because
     * each half is a different node.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void commandOutputIsParsedBackIntoANumber(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlueprintGraph g = newGraph();
        NodeModel entry = addNode(g, EntryNode.class);

        NodeModel command = addNode(g, RunCommandNode.class);
        setInputConstant(command, "command", "time query daytime");
        wire(g, command.getInputsById().get("level"), declareLevel(g));
        wire(g, command.getInputsById().get("trigger"), entry.getOutputsById().get("next"));

        // text ──> find ──> groups ──> get ──> parse
        //
        // The pattern deliberately matches more than the capture, so the whole match ("is 42") and group 1
        // ("42") are different strings. A bare "(\\d+)" would make them identical and the chain would pass
        // even if groups[0] were the whole match rather than the first capture — which is exactly the
        // contract this is here to hold up. Parsing "is 42" as a number does not work.
        NodeModel find = addNode(g, FindNode.class);
        setInputConstant(find, "pattern", "is (\\d+)");
        wire(g, find.getInputsById().get("in"), command.getOutputsById().get("output"));

        NodeModel first = addNode(g, ListGetNode.class);
        setInputConstant(first, "index", 0);
        wire(g, first.getInputsById().get("list"), find.getOutputsById().get("groups"));

        NodeModel parse = addNode(g, ParseNumberNode.class);
        wire(g, parse.getInputsById().get("in"), first.getOutputsById().get("value"));

        var exec = run(g, level, entry);
        assertTrue(helper, "the command ran", exec.evaluate(command.getOutputsById().get("ok"), Boolean.class));
        assertTrue(helper, "the pattern matched its output",
                exec.evaluate(find.getOutputsById().get("found"), Boolean.class));

        int fromResult = exec.evaluate(command.getOutputsById().get("result"), Integer.class);
        float fromText = exec.evaluate(parse.getOutputsById().get("out"), Float.class);
        assertEq(helper, "the number parsed out of the text is the command's own result",
                (float) fromResult, fromText, 1e-3f);
        assertEq(helper, "and that is really the world's daytime",
                (int) (level.getDayTime() % 24000L), fromResult);
        helper.succeed();
    }

    /**
     * One id, fanned out to two nodes that must agree about it.
     *
     * <p>{@code mc_items_in_tag} lists a tag and {@code mc_item_stack_in_tag} tests membership of one. Their
     * own tests each cross-check the other, but in Java: the list came out of an executor and went back in
     * as a constant. Here the id is built once by {@code mc_id_create} and <b>wired to both</b>, an item
     * from the list is turned into a stack by a third node, and that stack is fed to the membership test.
     *
     * <p>So the round trip is now a graph: id → contents → element → stack → membership → true. A namespace
     * dropped on the way, or a list whose elements arrive as something other than items, breaks it.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aTagsContentsAreMembersOfThatSameTag(GameTestHelper helper) {
        BlueprintGraph g = newGraph();

        NodeModel id = addNode(g, McIdNodes.Create.class);
        setInputConstant(id, "namespace", "minecraft");
        setInputConstant(id, "path", "planks");

        NodeModel contents = addNode(g, TagContentsNodes.ItemsInTag.class);
        wire(g, contents.getInputsById().get("tag"), id.getOutputsById().get("out"));

        NodeModel first = addNode(g, ListGetNode.class);
        setInputConstant(first, "index", 0);
        wire(g, first.getInputsById().get("list"), contents.getOutputsById().get("out"));

        NodeModel stack = addNode(g, ItemStackCreateNode.class);
        setInputConstant(stack, "count", 1);
        wire(g, stack.getInputsById().get("item"), first.getOutputsById().get("value"));

        // The same id node feeds the membership test — a fan-out, not a second constant.
        NodeModel member = addNode(g, McTagNodes.ItemStackInTag.class);
        wire(g, member.getInputsById().get("stack"), stack.getOutputsById().get("out"));
        wire(g, member.getInputsById().get("tag"), id.getOutputsById().get("out"));

        var exec = new GraphExecutor(g);
        assertTrue(helper, "the tag was found", exec.evaluate(contents.getOutputsById().get("found"), Boolean.class));
        assertTrue(helper, "and holds something",
                exec.evaluate(contents.getOutputsById().get("count"), Integer.class) > 0);
        assertFalse(helper, "the stack built from its first element is real",
                exec.evaluate(stack.getOutputsById().get("out"), ItemStack.class).isEmpty());
        assertTrue(helper, "and the membership node agrees it is in the tag",
                exec.evaluate(member.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }

    /**
     * A recipe id travelling from a list into two different lookups.
     *
     * <p>{@code mc_recipes_for} answers with ids, and the only useful thing to do with an id is hand it to
     * {@code mc_recipe_by_id} or {@code mc_recipe_ingredients}. That hand-off is the join their single-node
     * tests could not exercise, because there the id was typed in.
     *
     * <p>The closing assertion is a loop back to the start: whatever recipe the search found must, when
     * looked up by id, produce the item that was searched for.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aFoundRecipeIdLooksUpItsOwnResult(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlueprintGraph g = newGraph();
        PortModel levelPort = declareLevel(g);

        NodeModel search = addNode(g, RecipeNodes.RecipesFor.class);
        setInputConstant(search, "item", Items.CRAFTING_TABLE);
        wire(g, search.getInputsById().get("level"), levelPort);

        NodeModel first = addNode(g, ListGetNode.class);
        setInputConstant(first, "index", 0);
        wire(g, first.getInputsById().get("list"), search.getOutputsById().get("out"));

        NodeModel byId = addNode(g, RecipeNodes.RecipeById.class);
        wire(g, byId.getInputsById().get("id"), first.getOutputsById().get("value"));
        wire(g, byId.getInputsById().get("level"), levelPort);

        NodeModel ingredients = addNode(g, RecipeNodes.RecipeIngredients.class);
        wire(g, ingredients.getInputsById().get("id"), first.getOutputsById().get("value"));
        wire(g, ingredients.getInputsById().get("level"), levelPort);

        NodeModel unpack = addNode(g, ItemStackNodes.Unpack.class);
        wire(g, unpack.getInputsById().get("stack"), byId.getOutputsById().get("out"));

        var exec = new GraphExecutor(g, EvaluationEnvironment.with(Map.of("level", level)));
        assertTrue(helper, "something makes a crafting table",
                exec.evaluate(search.getOutputsById().get("count"), Integer.class) > 0);
        assertTrue(helper, "the id resolved to a recipe",
                exec.evaluate(byId.getOutputsById().get("found"), Boolean.class));
        assertEq(helper, "which produces the item we searched for", Items.CRAFTING_TABLE,
                exec.evaluate(unpack.getOutputsById().get("item"), Item.class));
        assertEq(helper, "and it takes four things", 4,
                exec.evaluate(ingredients.getOutputsById().get("count"), Integer.class).intValue());
        helper.succeed();
    }

    /**
     * A compound tag written and read back through three chained nodes.
     *
     * <p>The NBT nodes mutate in place and hand the same object on, which their own docs promise. That
     * promise only means anything across a wire, and this is where it is checked: Create makes a tag, Path
     * Set writes into it and passes it along, Path Get reads it out of what arrived.
     *
     * <p>The second reader is the sharper half. It hangs off the <b>same</b> Set output and asks for a
     * parent of the path that was written, so it can only answer if the intermediate compounds really were
     * created on the way down rather than faked on the leaf.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void nbtFlowsThroughAPathWriteAndBack(GameTestHelper helper) {
        BlueprintGraph g = newGraph();

        NodeModel create = addNode(g, NbtCreateNode.class);

        NodeModel set = addNode(g, NbtPathSetNode.class);
        setOption(set, "valueType", NbtValueType.STRING);
        setInputConstant(set, "path", "display.Name");
        setInputConstant(set, "value", "Excalibur");
        wire(g, set.getInputsById().get("tag"), create.getOutputsById().get("out"));

        NodeModel get = addNode(g, NbtPathGetNode.class);
        setOption(get, "valueType", NbtValueType.STRING);
        setInputConstant(get, "path", "display.Name");
        wire(g, get.getInputsById().get("tag"), set.getOutputsById().get("out"));

        NodeModel parent = addNode(g, NbtPathGetNode.class);
        setOption(parent, "valueType", NbtValueType.COMPOUND);
        setInputConstant(parent, "path", "display");
        wire(g, parent.getInputsById().get("tag"), set.getOutputsById().get("out"));

        var exec = new GraphExecutor(g);
        assertEq(helper, "one value was written", 1,
                exec.evaluate(set.getOutputsById().get("count"), Integer.class).intValue());
        assertTrue(helper, "and the reader downstream found it",
                exec.evaluate(get.getOutputsById().get("found"), Boolean.class));
        assertEq(helper, "with the value that went in", "Excalibur",
                exec.evaluate(get.getOutputsById().get("out"), String.class));
        assertTrue(helper, "the parent compound was created on the way down",
                exec.evaluate(parent.getOutputsById().get("found"), Boolean.class));
        assertEq(helper, "and holds the leaf", "Excalibur",
                exec.evaluate(parent.getOutputsById().get("out"), CompoundTag.class).getString("Name"));
        helper.succeed();
    }

    /**
     * A potion built by one node, extended by a second, and read by a third.
     *
     * <p>Stacks are values here, so each node returns a copy and the next one has to receive it over a
     * wire. Their own tests moved those copies in Java. This asserts the same thing the hard way, and adds
     * a fan-out the single-node tests had no way to express: the effect id is built once and wired both to
     * the node that adds it and to the {@code list_contains} that looks for it in the result.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aPotionIsBuiltAndReadBackThroughTheGraph(GameTestHelper helper) {
        BlueprintGraph g = newGraph();

        NodeModel strengthId = addNode(g, McIdNodes.Create.class);
        setInputConstant(strengthId, "namespace", "minecraft");
        setInputConstant(strengthId, "path", "strength");

        NodeModel potionId = addNode(g, McIdNodes.Create.class);
        setInputConstant(potionId, "namespace", "minecraft");
        setInputConstant(potionId, "path", "swiftness");

        NodeModel make = addNode(g, PotionNodes.Make.class);
        setInputConstant(make, "item", Items.POTION);
        wire(g, make.getInputsById().get("potion"), potionId.getOutputsById().get("out"));

        NodeModel add = addNode(g, PotionNodes.AddCustomEffect.class);
        setInputConstant(add, "duration", 100);
        setInputConstant(add, "amplifier", 1);
        wire(g, add.getInputsById().get("stack"), make.getOutputsById().get("out"));
        wire(g, add.getInputsById().get("effect"), strengthId.getOutputsById().get("out"));

        NodeModel effects = addNode(g, PotionNodes.Effects.class);
        wire(g, effects.getInputsById().get("stack"), add.getOutputsById().get("out"));

        // Same id node, second consumer: does the list coming out really contain what went in?
        NodeModel contains = addNode(g, ListContainsNode.class);
        wire(g, contains.getInputsById().get("list"), effects.getOutputsById().get("ids"));
        wire(g, contains.getInputsById().get("value"), strengthId.getOutputsById().get("out"));

        var exec = new GraphExecutor(g);
        assertTrue(helper, "the potion was made", exec.evaluate(make.getOutputsById().get("ok"), Boolean.class));
        assertTrue(helper, "the effect was added", exec.evaluate(add.getOutputsById().get("ok"), Boolean.class));
        assertEq(helper, "the result carries both effects", 2,
                exec.evaluate(effects.getOutputsById().get("count"), Integer.class).intValue());
        assertTrue(helper, "including the one wired in",
                exec.evaluate(contains.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }

    /**
     * One position, three consumers, and a state handed on from one of them.
     *
     * <p>The mining nodes are meant to be used together — is it loaded, is it worth the time, what do I get
     * — and each takes the same position. Wiring one {@code mc_block_pos_create} to all three is how a user
     * would build it and is the case where a node that cached a position, or read a stale one, shows up.
     *
     * <p>{@code mc_block_drops} also hands its {@code state} to {@code mc_can_harvest}, which is the join
     * that node's docs recommend and which nothing tested.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void oneBlockPositionDrivesTheWholeMiningDecision(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(at, Blocks.STONE.defaultBlockState(), 3);

        BlueprintGraph g = newGraph();
        PortModel levelPort = declareLevel(g);

        NodeModel pos = addNode(g, BlockPosCreateNode.class);
        setInputConstant(pos, "x", at.getX());
        setInputConstant(pos, "y", at.getY());
        setInputConstant(pos, "z", at.getZ());

        NodeModel loaded = addNode(g, WorldQueryNodes.IsChunkLoaded.class);
        wire(g, loaded.getInputsById().get("level"), levelPort);
        wire(g, loaded.getInputsById().get("pos"), pos.getOutputsById().get("out"));

        NodeModel speed = addNode(g, MiningNodes.DestroySpeed.class);
        setInputConstant(speed, "tool", new ItemStack(Items.IRON_PICKAXE));
        wire(g, speed.getInputsById().get("level"), levelPort);
        wire(g, speed.getInputsById().get("pos"), pos.getOutputsById().get("out"));

        NodeModel drops = addNode(g, LootNodes.BlockDrops.class);
        setInputConstant(drops, "tool", new ItemStack(Items.IRON_PICKAXE));
        wire(g, drops.getInputsById().get("level"), levelPort);
        wire(g, drops.getInputsById().get("pos"), pos.getOutputsById().get("out"));

        // The state the drops node read flows on to the harvest test, rather than being restated.
        NodeModel harvest = addNode(g, MiningNodes.CanHarvest.class);
        setInputConstant(harvest, "tool", new ItemStack(Items.IRON_PICKAXE));
        wire(g, harvest.getInputsById().get("state"), drops.getOutputsById().get("state"));

        NodeModel firstDrop = addNode(g, ListGetNode.class);
        setInputConstant(firstDrop, "index", 0);
        wire(g, firstDrop.getInputsById().get("list"), drops.getOutputsById().get("out"));

        NodeModel unpack = addNode(g, ItemStackNodes.Unpack.class);
        wire(g, unpack.getInputsById().get("stack"), firstDrop.getOutputsById().get("value"));

        var exec = new GraphExecutor(g, EvaluationEnvironment.with(Map.of("level", level)));
        assertTrue(helper, "the position is loaded",
                exec.evaluate(loaded.getOutputsById().get("out"), Boolean.class));
        assertEq(helper, "and all three nodes read the same stone", 1.5f,
                exec.evaluate(speed.getOutputsById().get("hardness"), Float.class), 1e-4f);
        assertTrue(helper, "a pickaxe harvests it",
                exec.evaluate(harvest.getOutputsById().get("out"), Boolean.class));
        assertEq(helper, "and what it drops is cobblestone", Items.COBBLESTONE,
                exec.evaluate(unpack.getOutputsById().get("item"), Item.class));
        helper.succeed();
    }

    /**
     * Three effect actions in one flow, all pointed at the same entity by one wire.
     *
     * <p>The only exec-flow chain among the new nodes. Their own tests each ran a two-node graph — Entry
     * then the action — so nothing checked that a second action sees what the first one did, which is the
     * whole point of a flow. Here two adds run before a clear, and the clear has to count both.
     *
     * <p>The entity arrives through one variable node wired to all three. A node that resolved its entity
     * once and cached it would still pass here, but one that mis-ordered the flow would not: the count
     * comes out wrong the moment the clear runs before either add.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void anEffectFlowRunsInOrderOverOneEntity(GameTestHelper helper) {
        // No level variable: none of the effect actions take a world port, they reach it through the entity.
        LivingEntity pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));

        BlueprintGraph g = newGraph();
        var entityVar = (VariableDeclarationModelBase)
                g.graphModel.createVariable("who", KGTypeHandles.ENTITY, null, VariableKind.INPUT);
        PortModel who = g.graphModel.createVariableNode(entityVar, new Vector2f(0, 0), null, null)
                .getOutputPort();

        NodeModel entry = addNode(g, EntryNode.class);
        NodeModel speed = addNode(g, EntityActionNodes.AddEffect.class);
        NodeModel strength = addNode(g, EntityActionNodes.AddEffect.class);
        NodeModel clear = addNode(g, EntityActionNodes.ClearEffects.class);

        setInputConstant(speed, "effect", ResourceLocation.parse("minecraft:speed"));
        setInputConstant(speed, "duration", 200);
        setInputConstant(strength, "effect", ResourceLocation.parse("minecraft:strength"));
        setInputConstant(strength, "duration", 200);
        for (NodeModel n : new NodeModel[]{speed, strength, clear}) {
            wire(g, n.getInputsById().get("entity"), who);
        }

        wire(g, speed.getInputsById().get("trigger"), entry.getOutputsById().get("next"));
        wire(g, strength.getInputsById().get("trigger"), speed.getOutputsById().get("next"));
        wire(g, clear.getInputsById().get("trigger"), strength.getOutputsById().get("next"));

        var exec = new GraphExecutor(g, EvaluationEnvironment.with(Map.of("who", pig)));
        exec.executeFrom(entry);

        assertTrue(helper, "the first effect went on", exec.evaluate(speed.getOutputsById().get("ok"), Boolean.class));
        assertTrue(helper, "so did the second", exec.evaluate(strength.getOutputsById().get("ok"), Boolean.class));
        assertTrue(helper, "and the clear had something to do",
                exec.evaluate(clear.getOutputsById().get("ok"), Boolean.class));
        assertEq(helper, "it removed both, so it ran after both adds", 2,
                exec.evaluate(clear.getOutputsById().get("removed"), Integer.class).intValue());
        assertTrue(helper, "and the pig really has none left", pig.getActiveEffects().isEmpty());
        helper.succeed();
    }

    // ---- helpers -----------------------------------------------------------------------------

    /** Declares the {@code level} INPUT variable and returns its node's output port, ready to wire. */
    private static PortModel declareLevel(BlueprintGraph g) {
        var v = (VariableDeclarationModelBase)
                g.graphModel.createVariable("level", KGTypeHandles.LEVEL, null, VariableKind.INPUT);
        return g.graphModel.createVariableNode(v, new Vector2f(0, 0), null, null).getOutputPort();
    }

    /** Runs the flow from {@code entry} with the level seeded on the environment. */
    private static GraphExecutor run(BlueprintGraph g, ServerLevel level, NodeModel entry) {
        var exec = new GraphExecutor(g, EvaluationEnvironment.with(Map.of("level", level)));
        exec.executeFrom(entry);
        return exec;
    }
}
