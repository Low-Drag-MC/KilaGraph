package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.action.EntityInteractionNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.action.WorldInteractionNodes;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
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
 * Entity and world interaction actions, against a real world.
 *
 * <p>As in {@code McActionGameTest}, the assertions are on the world after the action — the entity really
 * is on fire, the fire block really is there — and every case also covers the refusal path, because these
 * actions are written to report failure rather than throw.
 */
public final class McInteractionGameTest {
    private static final String SETS_AND_CLEARS_FIRE = "mc_interaction_sets_and_clears_fire";
    private static final String NAMES_AN_ENTITY = "mc_interaction_names_an_entity";
    private static final String MOUNTS_AND_DISMOUNTS = "mc_interaction_mounts_and_dismounts";
    private static final String SETS_EQUIPMENT = "mc_interaction_sets_equipment";
    private static final String IGNITES_AND_EXTINGUISHES = "mc_interaction_ignites_and_extinguishes";
    private static final String BONE_MEALS_WHAT_CAN_GROW = "mc_interaction_bone_meals_what_can_grow";
    private static final String STRIKES_LIGHTNING = "mc_interaction_strikes_lightning";
    private static final String EXPLODES = "mc_interaction_explodes";

    public static void registerFunctions() {
        KGGameTests.registerFunction(SETS_AND_CLEARS_FIRE, McInteractionGameTest::setsAndClearsFire);
        KGGameTests.registerFunction(NAMES_AN_ENTITY, McInteractionGameTest::namesAnEntity);
        KGGameTests.registerFunction(MOUNTS_AND_DISMOUNTS, McInteractionGameTest::mountsAndDismounts);
        KGGameTests.registerFunction(SETS_EQUIPMENT, McInteractionGameTest::setsEquipment);
        KGGameTests.registerFunction(IGNITES_AND_EXTINGUISHES, McInteractionGameTest::ignitesAndExtinguishes);
        KGGameTests.registerFunction(BONE_MEALS_WHAT_CAN_GROW, McInteractionGameTest::boneMealsWhatCanGrow);
        KGGameTests.registerFunction(STRIKES_LIGHTNING, McInteractionGameTest::strikesLightning);
        KGGameTests.registerFunction(EXPLODES, McInteractionGameTest::explodes);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{
                SETS_AND_CLEARS_FIRE, NAMES_AN_ENTITY, MOUNTS_AND_DISMOUNTS,
                SETS_EQUIPMENT, IGNITES_AND_EXTINGUISHES, BONE_MEALS_WHAT_CAN_GROW,
                STRIKES_LIGHTNING, EXPLODES
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    private McInteractionGameTest() {
    }

    // ---- entity ------------------------------------------------------------------------------

    public static void setsAndClearsFire(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));

        assertTrue(helper, "setting fire reported success",
                run(level, EntityInteractionNodes.SetFire.class, "entity", pig, "seconds", 5).ok());
        assertTrue(helper, "and the pig is burning, ticks=" + pig.getRemainingFireTicks(),
                pig.getRemainingFireTicks() > 0);
        assertEq(helper, "for the seconds given", 100, pig.getRemainingFireTicks());

        assertTrue(helper, "zero seconds reported success",
                run(level, EntityInteractionNodes.SetFire.class, "entity", pig, "seconds", 0).ok());
        assertTrue(helper, "and put the fire out", pig.getRemainingFireTicks() <= 0);

        // A fire-immune entity refuses rather than silently doing nothing.
        Entity blaze = helper.spawn(EntityType.BLAZE, new BlockPos(2, 2, 2));
        assertFalse(helper, "a blaze cannot be set alight",
                run(level, EntityInteractionNodes.SetFire.class, "entity", blaze, "seconds", 5).ok());
        helper.succeed();
    }

    public static void namesAnEntity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));

        assertTrue(helper, "naming reported success",
                run(level, EntityInteractionNodes.SetName.class,
                        "entity", pig, "name", Component.literal("Kevin"), "alwaysVisible", true).ok());
        assertTrue(helper, "the pig has a custom name", pig.hasCustomName());
        assertEq(helper, "which is the one given", "Kevin", pig.getCustomName().getString());
        assertTrue(helper, "and it is visible", pig.isCustomNameVisible());

        // No name clears it.
        assertTrue(helper, "clearing reported success",
                run(level, EntityInteractionNodes.SetName.class, "entity", pig).ok());
        assertFalse(helper, "and the custom name is gone", pig.hasCustomName());
        helper.succeed();
    }

    public static void mountsAndDismounts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        Entity cart = helper.spawn(EntityType.MINECART, new BlockPos(1, 2, 1));

        assertTrue(helper, "mounting reported success",
                run(level, EntityInteractionNodes.Mount.class, "entity", pig, "vehicle", cart).ok());
        assertTrue(helper, "the pig is riding", pig.isPassenger());
        assertEq(helper, "the minecart", cart, pig.getVehicle());

        assertTrue(helper, "dismounting reported success",
                run(level, EntityInteractionNodes.Dismount.class, "entity", pig).ok());
        assertFalse(helper, "and the pig is off", pig.isPassenger());

        assertFalse(helper, "dismounting again reports false",
                run(level, EntityInteractionNodes.Dismount.class, "entity", pig).ok());
        assertFalse(helper, "and nothing may ride itself",
                run(level, EntityInteractionNodes.Mount.class, "entity", pig, "vehicle", pig).ok());
        helper.succeed();
    }

    public static void setsEquipment(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LivingEntity zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));

        assertTrue(helper, "equipping reported success",
                run(level, EntityInteractionNodes.SetEquipment.class, "entity", zombie,
                        "slot", EquipmentSlot.MAINHAND, "stack", new ItemStack(Items.DIAMOND_SWORD)).ok());
        assertEq(helper, "and the zombie holds it", Items.DIAMOND_SWORD,
                zombie.getItemBySlot(EquipmentSlot.MAINHAND).getItem());

        assertTrue(helper, "armour works too",
                run(level, EntityInteractionNodes.SetEquipment.class, "entity", zombie,
                        "slot", EquipmentSlot.HEAD, "stack", new ItemStack(Items.IRON_HELMET)).ok());
        assertEq(helper, "on the head", Items.IRON_HELMET,
                zombie.getItemBySlot(EquipmentSlot.HEAD).getItem());

        // Not a living entity: refused.
        Entity arrow = helper.spawn(EntityType.ARROW, new BlockPos(2, 2, 2));
        assertFalse(helper, "an arrow has no hands",
                run(level, EntityInteractionNodes.SetEquipment.class, "entity", arrow,
                        "stack", new ItemStack(Items.DIAMOND_SWORD)).ok());
        helper.succeed();
    }

    // ---- world -------------------------------------------------------------------------------

    public static void ignitesAndExtinguishes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos above = ground.above();
        level.setBlock(ground, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(above, Blocks.AIR.defaultBlockState(), 3);

        assertTrue(helper, "ignite reported success",
                run(level, WorldInteractionNodes.Ignite.class, "pos", above).ok());
        assertTrue(helper, "and there is fire there",
                level.getBlockState(above).getBlock() instanceof BaseFireBlock);

        assertTrue(helper, "extinguish reported success",
                run(level, WorldInteractionNodes.Extinguish.class, "pos", above).ok());
        assertTrue(helper, "and the fire is gone", level.getBlockState(above).isAir());

        assertFalse(helper, "extinguishing where there is no fire reports false",
                run(level, WorldInteractionNodes.Extinguish.class, "pos", above).ok());
        assertFalse(helper, "and fire needs an empty block",
                run(level, WorldInteractionNodes.Ignite.class, "pos", ground).ok());
        helper.succeed();
    }

    public static void boneMealsWhatCanGrow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos grass = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(grass, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        level.setBlock(grass.above(), Blocks.AIR.defaultBlockState(), 3);

        assertTrue(helper, "bone meal works on grass",
                run(level, WorldInteractionNodes.BoneMeal.class, "pos", grass).ok());

        // Stone is not bonemealable, and saying so is the point.
        BlockPos stone = helper.absolutePos(new BlockPos(3, 2, 1));
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
        assertFalse(helper, "but not on stone",
                run(level, WorldInteractionNodes.BoneMeal.class, "pos", stone).ok());
        helper.succeed();
    }

    public static void strikesLightning(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));

        var bolt = run(level, WorldInteractionNodes.StrikeLightning.class, "pos", at, "visualOnly", true);
        assertTrue(helper, "the strike reported success", bolt.ok());
        Entity entity = bolt.get("entity", Entity.class);
        assertTrue(helper, "and produced a lightning bolt",
                entity instanceof LightningBolt);
        assertEq(helper, "at the position asked for", at.getX() + 0.5f, (float) entity.getX(), 0.01f);
        helper.succeed();
    }

    /**
     * The explosion node runs and refuses correctly.
     *
     * <h2>Why block destruction is deliberately not asserted here</h2>
     * It cannot be, in this environment. Calling vanilla's own {@code Level.explode} directly, with the
     * identical arguments the node passes and TNT-strength radius 4, <b>also leaves the block standing</b>
     * in a GameTest world — verified by running exactly that as a control. So an assertion either way
     * would be measuring the test environment rather than the node:
     * <ul>
     *   <li>"the block is gone" fails even though the node is correct;</li>
     *   <li>"the block is still there" <em>passes for the wrong reason</em>, which is worse — it would
     *       keep passing if {@code destroyBlocks} were wired to the wrong argument, or ignored entirely.</li>
     * </ul>
     * A vacuous assertion is not a weaker test, it is a misleading one. What is left is what this
     * environment can actually witness: that the call goes through, and that a nonsensical radius is
     * refused. The {@code destroyBlocks} default itself is a code-level decision, documented on the node.
     *
     * <p>If block destruction ever needs real coverage, it wants a dedicated world rather than a GameTest
     * structure area.
     */
    public static void explodes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(new BlockPos(1, 2, 1));

        assertTrue(helper, "a default explosion reported success",
                run(level, WorldInteractionNodes.Explode.class, "pos", centre, "radius", 4f).ok());
        assertTrue(helper, "a destructive one reported success",
                run(level, WorldInteractionNodes.Explode.class,
                        "pos", centre, "radius", 4f, "destroyBlocks", true).ok());
        assertTrue(helper, "and one that sets fires",
                run(level, WorldInteractionNodes.Explode.class,
                        "pos", centre, "radius", 2f, "fire", true).ok());

        assertFalse(helper, "a zero radius is refused",
                run(level, WorldInteractionNodes.Explode.class, "pos", centre, "radius", 0f).ok());
        assertFalse(helper, "a negative radius is refused",
                run(level, WorldInteractionNodes.Explode.class, "pos", centre, "radius", -1f).ok());
        helper.succeed();
    }

    // ---- helpers -----------------------------------------------------------------------------

    private record Result(GraphExecutor exec, NodeModel node) {
        boolean ok() {
            return get("ok", Boolean.class);
        }

        <T> T get(String output, Class<T> type) {
            return exec.evaluate(node.getOutputsById().get(output), type);
        }
    }

    private static Result run(ServerLevel level, Class<? extends Node> action, Object... inputs) {
        BlueprintGraph g = newGraph();
        NodeModel entry = addNode(g, EntryNode.class);
        NodeModel node = addNode(g, action);
        for (int i = 0; i + 1 < inputs.length; i += 2) {
            setInputConstant(node, (String) inputs[i], inputs[i + 1]);
        }
        PortModel levelPort = node.getInputsById().get("level");
        if (levelPort != null) {
            var v = (VariableDeclarationModelBase)
                    g.graphModel.createVariable("level", KGTypeHandles.LEVEL, null, VariableKind.INPUT);
            wire(g, levelPort,
                    g.graphModel.createVariableNode(v, new Vector2f(0, 0), null, null).getOutputPort());
        }
        wire(g, node.getInputsById().get("trigger"), entry.getOutputsById().get("next"));

        var exec = new GraphExecutor(g, EvaluationEnvironment.with(Map.of("level", level)));
        exec.executeFrom(entry);
        return new Result(exec, node);
    }
}
