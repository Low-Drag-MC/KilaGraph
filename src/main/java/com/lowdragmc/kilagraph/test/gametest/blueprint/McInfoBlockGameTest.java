package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.block.BlockEntityInfoBlocks;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.block.BlockEntityInfoNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.EntityInfoBlocks;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.EntityInfoNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.PlayerInfoBlocks;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.PlayerInfoNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.world.LevelInfoBlocks;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.world.LevelInfoNode;
import com.lowdragmc.kilagraph.blueprint.nodes.vector.VectorNodes;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.BlockNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.ContextNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addBlock;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;

/**
 * What each property block actually reads.
 *
 * <p>Every assertion compares the block's output against the same Minecraft call made directly, on the
 * same live object. That is deliberate: asserting a pig's height is {@code 0.9} would be testing
 * Minecraft, while asserting the block reports whatever {@code EntityType.PIG.getHeight()} returns is
 * testing the block. The few literals are ones this test set itself.
 */
public final class McInfoBlockGameTest {
    private static final String LEVEL_BLOCKS = "mc_info_block_level_blocks";
    private static final String ENTITY_BLOCKS = "mc_info_block_entity_blocks";
    private static final String HEALTH_ON_A_NON_LIVING_ENTITY = "mc_info_block_health_on_a_non_living_entity";
    private static final String PLAYER_BLOCKS = "mc_info_block_player_blocks";
    private static final String BLOCK_ENTITY_BLOCKS = "mc_info_block_block_entity_blocks";

    public static void registerFunctions() {
        KGGameTests.registerFunction(LEVEL_BLOCKS, McInfoBlockGameTest::levelBlocks);
        KGGameTests.registerFunction(ENTITY_BLOCKS, McInfoBlockGameTest::entityBlocks);
        KGGameTests.registerFunction(HEALTH_ON_A_NON_LIVING_ENTITY, McInfoBlockGameTest::healthOnANonLivingEntity);
        KGGameTests.registerFunction(PLAYER_BLOCKS, McInfoBlockGameTest::playerBlocks);
        KGGameTests.registerFunction(BLOCK_ENTITY_BLOCKS, McInfoBlockGameTest::blockEntityBlocks);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{
                LEVEL_BLOCKS, ENTITY_BLOCKS, HEALTH_ON_A_NON_LIVING_ENTITY,
                PLAYER_BLOCKS, BLOCK_ENTITY_BLOCKS
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    private static final float EPS = 1.0e-4f;

    private McInfoBlockGameTest() {
    }

    // ---- Level -------------------------------------------------------------------------------

    public static void levelBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var probe = new Probe(LevelInfoNode.class, level);

        assertEq(helper, "rainLevel", level.getRainLevel(1.0f),
                probe.read(LevelInfoBlocks.RainLevel.class, "value", Float.class), EPS);
        assertEq(helper, "thunderLevel", level.getThunderLevel(1.0f),
                probe.read(LevelInfoBlocks.ThunderLevel.class, "value", Float.class), EPS);

        var weather = probe.block(LevelInfoBlocks.Weather.class);
        assertEq(helper, "raining", level.isRaining(), weather.get("raining", Boolean.class));
        assertEq(helper, "thundering", level.isThundering(), weather.get("thundering", Boolean.class));

        var time = probe.block(LevelInfoBlocks.Time.class);
        assertEq(helper, "dayTime", level.getOverworldClockTime(), time.get("dayTime", Long.class).longValue());
        assertEq(helper, "gameTime", level.getGameTime(), time.get("gameTime", Long.class).longValue());
        assertEq(helper, "day", level.isBrightOutside(), time.get("day", Boolean.class));
        // day and night are complements, so a graph branching on one is branching on both.
        assertEq(helper, "night is the opposite of day",
                !time.get("day", Boolean.class), time.get("night", Boolean.class));

        assertEq(helper, "dimension", level.dimension().identifier(),
                probe.read(LevelInfoBlocks.Dimension.class, "value", Identifier.class));

        var bounds = probe.block(LevelInfoBlocks.Bounds.class);
        assertEq(helper, "minY", level.getMinY(), bounds.get("minY", Integer.class).intValue());
        assertEq(helper, "maxY", level.getMaxY(), bounds.get("maxY", Integer.class).intValue());
        assertEq(helper, "seaLevel", level.getSeaLevel(), bounds.get("seaLevel", Integer.class).intValue());

        var difficulty = probe.block(LevelInfoBlocks.Difficulty.class);
        assertEq(helper, "difficulty is the serialized name", level.getDifficulty().getSerializedName(),
                difficulty.get("value", String.class));
        assertEq(helper, "hardcore", level.getLevelData().isHardcore(),
                difficulty.get("hardcore", Boolean.class));

        // A gametest runs on the server, so this is the one value the test knows independently.
        assertFalse(helper, "a server level is not client-side",
                probe.read(LevelInfoBlocks.IsClient.class, "value", Boolean.class));
        helper.succeed();
    }

    // ---- Entity ------------------------------------------------------------------------------

    public static void entityBlocks(GameTestHelper helper) {
        Entity pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        var probe = new Probe(EntityInfoNode.class, pig);

        assertVector(helper, "position", pig.position(),
                probe.read(EntityInfoBlocks.Position.class, "value", Object.class));
        assertVector(helper, "eyePosition", pig.getEyePosition(),
                probe.read(EntityInfoBlocks.EyePosition.class, "value", Object.class));
        Object look = probe.read(EntityInfoBlocks.LookDirection.class, "value", Object.class);
        assertVector(helper, "lookAngle", pig.getLookAngle(), look);
        // And it really is normalised, which is what makes it safe to scale by a reach distance.
        float[] l = VectorNodes.components(look);
        assertEq(helper, "look direction is a unit vector", 1f,
                (float) Math.sqrt(l[0] * l[0] + l[1] * l[1] + l[2] * l[2]), 1e-2f);
        assertVector(helper, "velocity", pig.getDeltaMovement(),
                probe.read(EntityInfoBlocks.Velocity.class, "value", Object.class));

        assertEq(helper, "blockPosition", pig.blockPosition(),
                probe.read(EntityInfoBlocks.BlockPosition.class, "value", BlockPos.class));
        assertEq(helper, "boundingBox", pig.getBoundingBox(),
                probe.read(EntityInfoBlocks.BoundingBox.class, "value", AABB.class));
        assertEq(helper, "type", pig.getType(),
                probe.read(EntityInfoBlocks.Type.class, "value", EntityType.class));

        var rotation = probe.block(EntityInfoBlocks.Rotation.class);
        assertEq(helper, "yaw", pig.getYRot(), rotation.get("yaw", Float.class), EPS);
        assertEq(helper, "pitch", pig.getXRot(), rotation.get("pitch", Float.class), EPS);

        var identity = probe.block(EntityInfoBlocks.Identity.class);
        assertEq(helper, "id", pig.getId(), identity.get("id", Integer.class).intValue());
        assertEq(helper, "uuid", pig.getUUID().toString(), identity.get("uuid", String.class));
        assertEq(helper, "name", pig.getName().getString(), identity.get("name", Component.class).getString());

        var state = probe.block(EntityInfoBlocks.State.class);
        assertEq(helper, "alive", pig.isAlive(), state.get("alive", Boolean.class));
        assertEq(helper, "inWater", pig.isInWater(), state.get("inWater", Boolean.class));
        assertEq(helper, "onFire", pig.isOnFire(), state.get("onFire", Boolean.class));
        assertEq(helper, "onGround", pig.onGround(), state.get("onGround", Boolean.class));
        assertEq(helper, "invisible", pig.isInvisible(), state.get("invisible", Boolean.class));
        assertEq(helper, "sprinting", pig.isSprinting(), state.get("sprinting", Boolean.class));

        var age = probe.block(EntityInfoBlocks.Age.class);
        assertEq(helper, "tickCount", pig.tickCount, age.get("tickCount", Integer.class).intValue());
        assertEq(helper, "fallDistance", (float) pig.fallDistance,
                age.get("fallDistance", Double.class).floatValue(), EPS);

        // A pig is a LivingEntity, so its health numbers mean something.
        var health = probe.block(EntityInfoBlocks.Health.class);
        assertTrue(helper, "a pig is living", health.get("living", Boolean.class));
        assertEq(helper, "health", ((LivingEntity) pig).getHealth(), health.get("value", Float.class), EPS);
        assertEq(helper, "maxHealth", ((LivingEntity) pig).getMaxHealth(), health.get("max", Float.class), EPS);
        helper.succeed();
    }

    /**
     * Health on something that is not alive.
     *
     * <p>An arrow has no health at all — the numbers read zero and {@code living} says so, rather than the
     * block refusing to evaluate. This is the case a reflective context could not express, because the
     * getter is not on {@code Entity}.</p>
     */
    public static void healthOnANonLivingEntity(GameTestHelper helper) {
        Entity arrow = helper.spawn(EntityType.ARROW, new BlockPos(2, 2, 2));
        var health = new Probe(EntityInfoNode.class, arrow).block(EntityInfoBlocks.Health.class);
        assertFalse(helper, "an arrow is not living", health.get("living", Boolean.class));
        assertEq(helper, "and reads zero health", 0f, health.get("value", Float.class), EPS);
        assertEq(helper, "and zero max", 0f, health.get("max", Float.class), EPS);
        helper.succeed();
    }

    // ---- Player ------------------------------------------------------------------------------

    public static void playerBlocks(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        var probe = new Probe(PlayerInfoNode.class, player);

        var food = probe.block(PlayerInfoBlocks.Food.class);
        assertEq(helper, "food", player.getFoodData().getFoodLevel(), food.get("food", Integer.class).intValue());
        assertEq(helper, "saturation", player.getFoodData().getSaturationLevel(),
                food.get("saturation", Float.class), EPS);

        var xp = probe.block(PlayerInfoBlocks.Experience.class);
        assertEq(helper, "level", player.experienceLevel, xp.get("level", Integer.class).intValue());
        assertEq(helper, "progress", player.experienceProgress, xp.get("progress", Float.class), EPS);
        assertEq(helper, "total", player.totalExperience, xp.get("total", Integer.class).intValue());

        var held = probe.block(PlayerInfoBlocks.HeldItems.class);
        assertEq(helper, "main hand", Items.DIAMOND_SWORD,
                held.get("mainHand", ItemStack.class).getItem());
        assertTrue(helper, "off hand is empty", held.get("offHand", ItemStack.class).isEmpty());

        var mode = probe.block(PlayerInfoBlocks.GameMode.class);
        assertFalse(helper, "survival is not creative", mode.get("creative", Boolean.class));
        assertFalse(helper, "survival is not spectator", mode.get("spectator", Boolean.class));
        assertEq(helper, "canBuild", player.mayBuild(), mode.get("canBuild", Boolean.class));

        var posture = probe.block(PlayerInfoBlocks.Posture.class);
        assertEq(helper, "sleeping", player.isSleeping(), posture.get("sleeping", Boolean.class));
        assertEq(helper, "crouching", player.isCrouching(), posture.get("crouching", Boolean.class));

        // And the entity blocks work in a player context, which is the point of scoping them to both.
        assertEq(helper, "a player context reads entity properties too", player.getId(),
                probe.block(EntityInfoBlocks.Identity.class).get("id", Integer.class).intValue());
        helper.succeed();
    }

    // ---- BlockEntity -------------------------------------------------------------------------

    public static void blockEntityBlocks(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 2, 1);
        helper.setBlock(relative, Blocks.CHEST);
        BlockEntity chest = helper.getBlockEntity(relative, BlockEntity.class);
        assertTrue(helper, "the chest has a block entity", chest != null);
        var probe = new Probe(BlockEntityInfoNode.class, chest);

        assertEq(helper, "position", chest.getBlockPos(),
                probe.read(BlockEntityInfoBlocks.Position.class, "value", BlockPos.class));
        assertEq(helper, "state", chest.getBlockState(),
                probe.read(BlockEntityInfoBlocks.State.class, "value", BlockState.class));

        var containing = probe.block(BlockEntityInfoBlocks.ContainingLevel.class);
        assertTrue(helper, "level is present", containing.get("present", Boolean.class));
        assertEq(helper, "and is the test's level", helper.getLevel(),
                containing.get("value", Level.class));

        var identity = probe.block(BlockEntityInfoBlocks.Identity.class);
        assertEq(helper, "type id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(chest.getType()),
                identity.get("type", Identifier.class));
        assertFalse(helper, "not removed", identity.get("removed", Boolean.class));
        helper.succeed();
    }

    // ---- helpers -----------------------------------------------------------------------------

    /**
     * A context holding one target, into which blocks can be dropped and read.
     *
     * <p>Each {@link #block} adds to the <em>same</em> graph and context, so a test that reads several
     * properties exercises the shape a real graph uses — one wired target, many blocks — rather than
     * building a fresh context per property.</p>
     */
    private static final class Probe {
        private final BlueprintGraph graph = newGraph();
        private final NodeModel context;

        Probe(Class<? extends ContextNode> contextClass, Object target) {
            this.context = addNode(graph, contextClass);
            setInputConstant(context, "target", target);
        }

        Read block(Class<? extends BlockNode> blockClass) {
            NodeModel block = addBlock(graph, context, blockClass);
            return new Read(new GraphExecutor(graph), block);
        }

        <T> T read(Class<? extends BlockNode> blockClass, String output, Class<T> type) {
            return block(blockClass).get(output, type);
        }
    }

    /** One block's outputs, evaluated on demand. */
    private record Read(GraphExecutor exec, NodeModel block) {
        <T> T get(String output, Class<T> type) {
            return exec.evaluate(block.getOutputsById().get(output), type);
        }
    }

    /** The block's JOML vector against the Vec3 it came from, at float precision. */
    private static void assertVector(GameTestHelper helper, String label,
                                     Vec3 expected, Object actual) {
        float[] c = VectorNodes.components(actual);
        assertEq(helper, label + " x", (float) expected.x, c[0], EPS);
        assertEq(helper, label + " y", (float) expected.y, c[1], EPS);
        assertEq(helper, label + " z", (float) expected.z, c[2], EPS);
    }
}
