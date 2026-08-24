package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.action.EntityActionNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.action.WorldEffectNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.component.DataComponentNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.container.ContainerNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.EntityCastNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.world.WorldQueryNodes;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Vector2f;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * Nodes that had no test at all.
 *
 * <p>Found by a coverage sweep rather than by reasoning: for every {@code @NodeAttribute} class, is its
 * name referenced anywhere under {@code test/}? Eleven were not, and a node nothing exercises is a node
 * nobody has ever run — several of these turned out to be fine, which is exactly what a coverage gap
 * looks like from the inside until you close it.
 *
 * <p>They have nothing in common but that, so they live together here rather than being scattered into
 * files whose subject they do not share.
 */
public final class McCoverageGameTest {
    private static final String ENTITY_CASTS = "mc_coverage_entity_casts";
    private static final String ENTITY_CONTAINER = "mc_coverage_entity_container";
    private static final String NEAREST_PLAYER = "mc_coverage_nearest_player";
    private static final String FLUID_COMPONENTS = "mc_coverage_fluid_components";
    private static final String GIVE_ITEM_AND_SEND_MESSAGE = "mc_coverage_give_item_and_send_message";

    public static void registerFunctions() {
        KGGameTests.registerFunction(ENTITY_CASTS, McCoverageGameTest::entityCasts);
        KGGameTests.registerFunction(ENTITY_CONTAINER, McCoverageGameTest::entityContainer);
        KGGameTests.registerFunction(NEAREST_PLAYER, McCoverageGameTest::nearestPlayer);
        KGGameTests.registerFunction(FLUID_COMPONENTS, McCoverageGameTest::fluidComponents);
        KGGameTests.registerFunction(GIVE_ITEM_AND_SEND_MESSAGE, McCoverageGameTest::giveItemAndSendMessage);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{
                ENTITY_CASTS, ENTITY_CONTAINER, NEAREST_PLAYER,
                FLUID_COMPONENTS, GIVE_ITEM_AND_SEND_MESSAGE
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    private McCoverageGameTest() {
    }

    /**
     * Entity to player, and the living-entity test.
     *
     * <p>Both exist because {@code Player} and {@code Entity} are separate pin types and a graph holding
     * an {@code Entity} needs a way down to the narrower one. The interesting case is the failing one: a
     * pig is not a player, and the node has to say so rather than producing a broken value.</p>
     */
    public static void entityCasts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        Entity arrow = helper.spawn(EntityType.ARROW, new BlockPos(2, 2, 2));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        var asPlayer = probe(level, EntityCastNodes.AsPlayer.class, "entity", player);
        assertTrue(helper, "a player casts to Player", asPlayer.eval("ok", Boolean.class));
        assertEq(helper, "and is the same object", player, asPlayer.eval("out", Player.class));

        var notPlayer = probe(level, EntityCastNodes.AsPlayer.class, "entity", pig);
        assertFalse(helper, "a pig does not cast to Player", notPlayer.eval("ok", Boolean.class));
        assertEq(helper, "and yields nothing", null, notPlayer.eval("out", Object.class));

        assertTrue(helper, "a pig is living",
                probe(level, EntityCastNodes.IsLiving.class, "entity", pig).eval("out", Boolean.class));
        assertTrue(helper, "a player is living",
                probe(level, EntityCastNodes.IsLiving.class, "entity", player).eval("out", Boolean.class));
        assertFalse(helper, "an arrow is not",
                probe(level, EntityCastNodes.IsLiving.class, "entity", arrow).eval("out", Boolean.class));
        helper.succeed();
    }

    /**
     * An entity's own inventory — and the surprise that every living entity has one.
     *
     * <p>A chest minecart's is storage, as expected. A pig's is its <b>equipment</b>: NeoForge registers
     * a handler over hands and armour for every living entity, so "does this mob have a container" is
     * always yes and a graph that treats it as storage will be writing to the mob's hands. The assertion
     * here is behavioural rather than a slot count — an item put in the mainhand is visible through the
     * container — because the exact composition is NeoForge's and asserting it would test NeoForge.
     */
    public static void entityContainer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity cart = helper.spawn(EntityType.CHEST_MINECART, new BlockPos(1, 2, 1));
        var pig = helper.spawn(EntityType.PIG, new BlockPos(3, 2, 3));

        var g = newGraph();
        var resolve = addNode(g, ContainerNodes.EntityContainer.class);
        setInputConstant(resolve, "entity", cart);
        var size = addNode(g, ContainerNodes.Size.class);
        wire(g, size.getInputsById().get("container"), resolve.getOutputsById().get("out"));
        var exec = new GraphExecutor(g, EvaluationEnvironment.with(Map.of("level", level)));

        assertTrue(helper, "a chest minecart has an inventory",
                exec.evaluate(resolve.getOutputsById().get("found"), Boolean.class));
        assertEq(helper, "with 27 slots of storage", 27,
                exec.evaluate(size.getOutputsById().get("slots"), Integer.class).intValue());

        // A pig also resolves — to its equipment, which is the part worth knowing.
        pig.setItemSlot(EquipmentSlot.MAINHAND,
                new ItemStack(Items.GOLDEN_APPLE));
        var pigG = newGraph();
        var pigResolve = addNode(pigG, ContainerNodes.EntityContainer.class);
        setInputConstant(pigResolve, "entity", pig);
        var pigGet = addNode(pigG, ContainerNodes.Get.class);
        wire(pigG, pigGet.getInputsById().get("container"), pigResolve.getOutputsById().get("out"));
        setInputConstant(pigGet, "slot", 0);
        var pigExec = new GraphExecutor(pigG, EvaluationEnvironment.with(Map.of("level", level)));

        assertTrue(helper, "a living entity resolves to a container too",
                pigExec.evaluate(pigResolve.getOutputsById().get("found"), Boolean.class));
        assertEq(helper, "and its first slot is the main hand", Items.GOLDEN_APPLE,
                pigExec.evaluate(pigGet.getOutputsById().get("out"), ItemStack.class).getItem());
        helper.succeed();
    }

    /** The nearest player within a radius, and the miss when there is none in range. */
    public static void nearestPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos where = helper.absolutePos(new BlockPos(1, 2, 1));

        // A mock player is not added to the level, so this asserts the honest answer for an empty world:
        // nothing found, and no crash. The node's positive path needs a real connected player, which a
        // headless GameTest has none of — saying so is better than a test that pretends.
        var none = probe(level, WorldQueryNodes.NearestPlayer.class, "pos", where, "radius", 8.0);
        assertFalse(helper, "no player is nearby in a headless test", none.eval("found", Boolean.class));
        assertEq(helper, "and none is returned", null, none.eval("out", Object.class));

        // A zero radius must also not throw.
        assertFalse(helper, "a zero radius finds nothing",
                probe(level, WorldQueryNodes.NearestPlayer.class, "pos", where, "radius", 0.0)
                        .eval("found", Boolean.class));
        helper.succeed();
    }

    /** A fluid stack's data components, listed. */
    public static void fluidComponents(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var empty = probe(level, DataComponentNodes.FluidComponents.class, "stack", FluidStack.EMPTY);
        assertEq(helper, "an empty stack lists nothing", List.of(), empty.eval("out", List.class));

        var water = probe(level, DataComponentNodes.FluidComponents.class,
                "stack", new FluidStack(Fluids.WATER, 1000));
        // Plain water carries no components; the assertion is that the node answers with a list rather
        // than null, which is what a For Each downstream depends on.
        assertTrue(helper, "and water answers with a list", water.eval("out", List.class) != null);
        helper.succeed();
    }

    /** Giving an item to a player, and the chat message action. */
    public static void giveItemAndSendMessage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        var give = action(level, EntityActionNodes.GiveItem.class,
                "player", player, "stack", new ItemStack(Items.IRON_INGOT, 5));
        assertTrue(helper, "giving reported success", give.eval("ok", Boolean.class));
        assertTrue(helper, "with nothing left over", give.eval("remainder", ItemStack.class).isEmpty());
        assertTrue(helper, "and the player really has it",
                player.getInventory().contains(new ItemStack(Items.IRON_INGOT)));

        assertFalse(helper, "giving nothing is refused",
                action(level, EntityActionNodes.GiveItem.class,
                        "player", player, "stack", ItemStack.EMPTY).eval("ok", Boolean.class));

        // A real ServerPlayer, not the mock above: 26.1 delivers a system message through the player's
        // connection, so a player without one genuinely cannot be sent to and the node says so.
        assertTrue(helper, "sending a message reported success",
                action(level, WorldEffectNodes.SendMessage.class,
                        "player", helper.makeMockServerPlayerInLevel(),
                        "message", Component.literal("hello")).eval("ok", Boolean.class));
        assertFalse(helper, "with no player it is refused",
                action(level, WorldEffectNodes.SendMessage.class,
                        "message", Component.literal("hello")).eval("ok", Boolean.class));
        helper.succeed();
    }

    // ---- helpers -----------------------------------------------------------------------------

    private record Probe(GraphExecutor exec, NodeModel node) {
        <T> T eval(String output, Class<T> type) {
            return exec.evaluate(node.getOutputsById().get(output), type);
        }
    }

    /** A data node alone in a graph, with the level wired in if it wants one. */
    private static Probe probe(ServerLevel level, Class<? extends Node> cls, Object... inputs) {
        BlueprintGraph g = newGraph();
        NodeModel n = build(g, cls, inputs);
        return new Probe(new GraphExecutor(g, EvaluationEnvironment.with(Map.of("level", level))), n);
    }

    /** Entry → an exec node, run to completion. */
    private static Probe action(ServerLevel level, Class<? extends Node> cls, Object... inputs) {
        BlueprintGraph g = newGraph();
        NodeModel entry = addNode(g, EntryNode.class);
        NodeModel n = build(g, cls, inputs);
        wire(g, n.getInputsById().get("trigger"), entry.getOutputsById().get("next"));
        var exec = new GraphExecutor(g, EvaluationEnvironment.with(Map.of("level", level)));
        exec.executeFrom(entry);
        return new Probe(exec, n);
    }

    private static NodeModel build(BlueprintGraph g, Class<? extends Node> cls, Object... inputs) {
        NodeModel n = addNode(g, cls);
        for (int i = 0; i + 1 < inputs.length; i += 2) {
            setInputConstant(n, (String) inputs[i], inputs[i + 1]);
        }
        PortModel levelPort = n.getInputsById().get("level");
        if (levelPort != null) {
            var v = (VariableDeclarationModelBase)
                    g.graphModel.createVariable("level", KGTypeHandles.LEVEL, null, VariableKind.INPUT);
            wire(g, levelPort,
                    g.graphModel.createVariableNode(v, new Vector2f(0, 0), null, null).getOutputPort());
        }
        return n;
    }
}
