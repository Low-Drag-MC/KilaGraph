package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.id.McIdNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.tag.McTagNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.tag.TagContentsNodes;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;

/**
 * Identifier construction, registry lookup and tag membership.
 *
 * <p>The registry lookups are checked in <b>both</b> directions against the same object, because a
 * one-way test cannot tell a working lookup from one that always answers with the registry's default.
 * That is the specific hazard here: {@code ITEM}, {@code BLOCK} and {@code FLUID} are defaulted
 * registries, so an unknown id yields {@code air}/{@code air}/{@code empty} rather than nothing — which
 * is why every lookup node has a {@code found} output and why every test below asserts it.
 */
@GameTestHolder(Kilagraph.MODID)
public final class McIdTagGameTest {

    private McIdTagGameTest() {
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void idsAreBuiltAndParsed(GameTestHelper helper) {
        var create = node(McIdNodes.Create.class, "namespace", "minecraft", "path", "diamond");
        assertEq(helper, "create", ResourceLocation.fromNamespaceAndPath("minecraft", "diamond"),
                eval(create, "out", ResourceLocation.class));

        // A namespace with an illegal character is a null, not a crash — an id assembled from graph
        // data is user input.
        var bad = node(McIdNodes.Create.class, "namespace", "NOT VALID", "path", "x");
        assertEq(helper, "malformed namespace gives null", null, eval(bad, "out", ResourceLocation.class));

        var parse = node(McIdNodes.Parse.class, "in", "minecraft:diamond_sword");
        assertEq(helper, "parse", ResourceLocation.fromNamespaceAndPath("minecraft", "diamond_sword"),
                eval(parse, "out", ResourceLocation.class));
        assertTrue(helper, "parse valid", eval(parse, "valid", Boolean.class));

        // no colon: the minecraft namespace is implied, exactly as everywhere else in the game
        var bare = node(McIdNodes.Parse.class, "in", "stone");
        assertEq(helper, "bare path takes the minecraft namespace",
                ResourceLocation.fromNamespaceAndPath("minecraft", "stone"),
                eval(bare, "out", ResourceLocation.class));

        var junk = node(McIdNodes.Parse.class, "in", "not a valid id at all");
        assertFalse(helper, "junk is not valid", eval(junk, "valid", Boolean.class));
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void registryLookupsRoundTrip(GameTestHelper helper) {
        assertRoundTrip(helper, "item", McIdNodes.ItemFromId.class, McIdNodes.ItemId.class,
                "minecraft:diamond", Items.DIAMOND);
        assertRoundTrip(helper, "block", McIdNodes.BlockFromId.class, McIdNodes.BlockId.class,
                "minecraft:stone", Blocks.STONE);
        assertRoundTrip(helper, "fluid", McIdNodes.FluidFromId.class, McIdNodes.FluidId.class,
                "minecraft:water", Fluids.WATER);
        assertRoundTrip(helper, "entity type", McIdNodes.EntityTypeFromId.class,
                McIdNodes.EntityTypeId.class, "minecraft:pig", EntityType.PIG);
        helper.succeed();
    }

    /**
     * An unknown id reports {@code found = false} and yields null.
     *
     * <p>This is the assertion the defaulted registries make necessary: without it, a lookup node that
     * did nothing but return {@code registry.get(id)} would pass every test above while quietly turning
     * a typo into {@code minecraft:air}.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void unknownIdsAreNotSilentlyDefaulted(GameTestHelper helper) {
        for (var cls : List.of(McIdNodes.ItemFromId.class, McIdNodes.BlockFromId.class,
                McIdNodes.FluidFromId.class, McIdNodes.EntityTypeFromId.class)) {
            var n = node(cls, "id", ResourceLocation.fromNamespaceAndPath("kilagraph", "no_such_thing"));
            assertFalse(helper, cls.getSimpleName() + " reports not found",
                    eval(n, "found", Boolean.class));
            assertEq(helper, cls.getSimpleName() + " yields null rather than the registry default",
                    null, eval(n, "out", Object.class));
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void tagMembershipIsTestedPerRegistry(GameTestHelper helper) {
        // minecraft:planks holds every plank; oak is in it and stone is not.
        var oak = node(McTagNodes.BlockInTag.class, "block", Blocks.OAK_PLANKS,
                "tag", id("minecraft:planks"));
        assertTrue(helper, "oak planks are planks", eval(oak, "out", Boolean.class));
        var stone = node(McTagNodes.BlockInTag.class, "block", Blocks.STONE, "tag", id("minecraft:planks"));
        assertFalse(helper, "stone is not planks", eval(stone, "out", Boolean.class));

        var state = node(McTagNodes.BlockStateInTag.class, "state", Blocks.OAK_PLANKS.defaultBlockState(),
                "tag", id("minecraft:planks"));
        assertTrue(helper, "a plank state is planks", eval(state, "out", Boolean.class));

        var water = node(McTagNodes.FluidInTag.class, "fluid", Fluids.WATER, "tag", id("minecraft:water"));
        assertTrue(helper, "water is in the water tag", eval(water, "out", Boolean.class));

        var stack = node(McTagNodes.ItemStackInTag.class, "stack", new ItemStack(Items.OAK_PLANKS),
                "tag", id("minecraft:planks"));
        assertTrue(helper, "a plank stack is planks", eval(stack, "out", Boolean.class));

        var skeleton = node(McTagNodes.EntityTypeInTag.class, "type", EntityType.SKELETON,
                "tag", id("minecraft:skeletons"));
        assertTrue(helper, "a skeleton is a skeleton", eval(skeleton, "out", Boolean.class));

        // A tag that does not exist is false, not a throw.
        var missing = node(McTagNodes.BlockInTag.class, "block", Blocks.STONE,
                "tag", id("kilagraph:no_such_tag"));
        assertFalse(helper, "an unknown tag is false", eval(missing, "out", Boolean.class));
        helper.succeed();
    }

    /**
     * Tag contents, cross-checked against tag membership.
     *
     * <p>The load-bearing assertion is the loop: every item the contents node lists is fed back through
     * {@code mc_item_in_tag}. A hard-coded or stale list would pass a "contains oak planks" check and fail
     * this one, and the two nodes reach the tag by different routes — one through the registry's tag map,
     * one through the holder's own bindings — so agreement between them is worth something.
     *
     * <p>These tags are also datapack-loaded rather than hard-coded, so a non-empty result is what proves
     * the no-world lookup in {@code TagContentsNodes} actually sees a loaded server's tags.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void tagContentsAreListedPerRegistry(GameTestHelper helper) {
        var planks = node(TagContentsNodes.ItemsInTag.class, "tag", id("minecraft:planks"));
        assertTrue(helper, "the planks tag exists", eval(planks, "found", Boolean.class));
        List<?> items = eval(planks, "out", List.class);
        assertTrue(helper, "and holds several items, got " + items.size(), items.size() > 1);
        assertEq(helper, "count matches the list", items.size(), eval(planks, "count", Integer.class).intValue());
        assertTrue(helper, "oak planks are among them", items.contains(Items.OAK_PLANKS));
        assertFalse(helper, "stone is not", items.contains(Items.STONE));
        for (Object item : items) {
            var member = node(McTagNodes.ItemStackInTag.class, "stack", new ItemStack((Item) item),
                    "tag", id("minecraft:planks"));
            assertTrue(helper, item + " is listed but not a member", eval(member, "out", Boolean.class));
        }

        var blocks = node(TagContentsNodes.BlocksInTag.class, "tag", id("minecraft:planks"));
        assertTrue(helper, "the block planks tag exists", eval(blocks, "found", Boolean.class));
        assertTrue(helper, "and holds oak planks",
                eval(blocks, "out", List.class).contains(Blocks.OAK_PLANKS));

        var skeletons = node(TagContentsNodes.EntityTypesInTag.class, "tag", id("minecraft:skeletons"));
        assertTrue(helper, "the skeletons tag exists", eval(skeletons, "found", Boolean.class));
        assertTrue(helper, "and holds the skeleton",
                eval(skeletons, "out", List.class).contains(EntityType.SKELETON));

        var water = node(TagContentsNodes.FluidsInTag.class, "tag", id("minecraft:water"));
        assertTrue(helper, "the water tag exists", eval(water, "found", Boolean.class));
        assertTrue(helper, "and holds still water",
                eval(water, "out", List.class).contains(Fluids.WATER));

        // An unknown tag is an empty list and found = false, which is how a graph tells "nothing in it"
        // from "no such tag" — both iterate zero times.
        var unknown = node(TagContentsNodes.ItemsInTag.class, "tag", id("kilagraph:no_such_tag"));
        assertFalse(helper, "an unknown tag is not found", eval(unknown, "found", Boolean.class));
        assertEq(helper, "and lists nothing", 0, eval(unknown, "count", Integer.class).intValue());
        assertTrue(helper, "with an empty list", eval(unknown, "out", List.class).isEmpty());

        var noId = node(TagContentsNodes.ItemsInTag.class);
        assertFalse(helper, "no tag id at all is not found", eval(noId, "found", Boolean.class));
        helper.succeed();
    }

    // ---- helpers -----------------------------------------------------------------------------

    /** id → object → id, asserting the object and both {@code found}/key legs. */
    private static void assertRoundTrip(GameTestHelper helper, String label,
                                        Class<? extends Node> fromId, Class<? extends Node> toId,
                                        String idText, Object expected) {
        var from = node(fromId, "id", id(idText));
        assertTrue(helper, label + " found", eval(from, "found", Boolean.class));
        assertEq(helper, label + " from id", expected, eval(from, "out", Object.class));

        var to = node(toId, "in", expected);
        assertEq(helper, label + " back to id", id(idText), eval(to, "out", ResourceLocation.class));
    }

    private static ResourceLocation id(String s) {
        return ResourceLocation.parse(s);
    }

    /** One node in its own graph, carried with the graph so it can be evaluated. */
    private record Probe(BlueprintGraph graph, NodeModel model) {
    }

    /** A node in its own graph with the given input constants applied, as {@code id, value} pairs. */
    private static Probe node(Class<? extends Node> cls, Object... inputs) {
        var g = newGraph();
        NodeModel n = addNode(g, cls);
        for (int i = 0; i + 1 < inputs.length; i += 2) {
            setInputConstant(n, (String) inputs[i], inputs[i + 1]);
        }
        return new Probe(g, n);
    }

    private static <T> T eval(Probe probe, String output, Class<T> type) {
        return new GraphExecutor(probe.graph())
                .evaluate(probe.model().getOutputsById().get(output), type);
    }
}
