package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.EntityDataNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.gameplay.RegistryProbeNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.world.WorldQueryNodes;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * World queries and entity data against a real {@link ServerLevel}.
 *
 * <p>The {@code level} reaches the nodes the only supported way — a wire-only graph variable seeded on
 * the environment, per the same pattern as {@code McWorldQueryGameTest}. The executor never knows about
 * the world; a graph that needs one says so on a port.
 */
@GameTestHolder(Kilagraph.MODID)
public final class McWorldEntityGameTest {

    private static final float EPS = 1e-3f;

    private McWorldEntityGameTest() {
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void worldQueries(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(new BlockPos(0, 2, 0));
        level.setBlock(abs, Blocks.WATER.defaultBlockState(), 3);

        assertEq(helper, "the biome has an id", true,
                probe(level, WorldQueryNodes.BiomeId.class, "pos", abs)
                        .eval("out", ResourceLocation.class) != null);

        assertEq(helper, "water block reports water", Fluids.WATER,
                probe(level, WorldQueryNodes.GetFluid.class, "pos", abs).eval("out", Object.class));

        // Both light values are read; the assertion is on the range rather than an exact number,
        // because what a GameTest structure area is lit to is not this node's business.
        var light = probe(level, WorldQueryNodes.GetLight.class, "pos", helper.absolutePos(new BlockPos(0, 5, 0)));
        int blockLight = light.eval("block", Integer.class);
        int skyLight = light.eval("sky", Integer.class);
        assertTrue(helper, "block light is a light level, got " + blockLight,
                blockLight >= 0 && blockLight <= 15);
        assertTrue(helper, "sky light is a light level, got " + skyLight,
                skyLight >= 0 && skyLight <= 15);

        assertTrue(helper, "an open column sees the sky",
                probe(level, WorldQueryNodes.CanSeeSky.class, "pos", helper.absolutePos(new BlockPos(0, 5, 0)))
                        .eval("out", Boolean.class));

        assertEq(helper, "dimension id", level.dimension().location(),
                probe(level, WorldQueryNodes.DimensionId.class).eval("out", ResourceLocation.class));

        // getHeight is a real query even in a flat test world; assert it answers rather than throws
        assertTrue(helper, "height is a number",
                probe(level, WorldQueryNodes.GetHeight.class, "x", abs.getX(), "z", abs.getZ())
                        .eval("out", Integer.class) != null);
        helper.succeed();
    }

    /** A ray straight down onto a placed block hits its top face. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void raycastFindsAPlacedBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(0, 2, 0));
        level.setBlock(target, Blocks.STONE.defaultBlockState(), 3);

        // The ray's endpoints are JOML Vector3f, because Vec3 is deliberately not a pin type. That
        // conversion is float, and a GameTest runs at absolute coordinates around 1.3e7 — where a float
        // cannot represent a half-block offset at all. So the expectation is computed from the SAME
        // floats the node will see rather than from the BlockPos, which is the honest form of the
        // assertion and a live demonstration of the documented precision limit in McConvert.
        Vector3f from = new Vector3f(target.getX() + 0.5f, target.getY() + 4f, target.getZ() + 0.5f);
        Vector3f to = new Vector3f(target.getX() + 0.5f, target.getY() - 1f, target.getZ() + 0.5f);
        BlockPos expected = BlockPos.containing(from.x, target.getY(), from.z);
        level.setBlock(expected, Blocks.STONE.defaultBlockState(), 3);

        var ray = probe(level, WorldQueryNodes.RaycastBlock.class, "from", from, "to", to);
        assertTrue(helper, "the ray hit something", ray.eval("hit", Boolean.class));
        assertEq(helper, "and it was the block the ray passes through", expected,
                ray.eval("pos", BlockPos.class).immutable());
        assertEq(helper, "entering through the top face", Direction.UP,
                ray.eval("face", Direction.class));

        // A ray through empty air misses, and reports the neutral answer rather than throwing.
        var miss = probe(level, WorldQueryNodes.RaycastBlock.class,
                "from", new Vector3f(target.getX() + 0.5f, target.getY() + 40f, target.getZ() + 0.5f),
                "to", new Vector3f(target.getX() + 0.5f, target.getY() + 30f, target.getZ() + 0.5f));
        assertFalse(helper, "a ray through air misses", miss.eval("hit", Boolean.class));
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void entityQueriesAndData(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));

        // --- the box-based world queries find it ---
        AABB wide = new AABB(pig.blockPosition()).inflate(8);
        var inBox = probe(level, WorldQueryNodes.EntitiesInBox.class, "box", wide);
        assertTrue(helper, "the pig is in the box", inBox.eval("out", List.class).contains(pig));

        var ofType = probe(level, WorldQueryNodes.EntitiesOfTypeInBox.class,
                "box", wide, "type", EntityType.PIG);
        assertTrue(helper, "and is found by type", ofType.eval("out", List.class).contains(pig));
        var wrongType = probe(level, WorldQueryNodes.EntitiesOfTypeInBox.class,
                "box", wide, "type", EntityType.COW);
        assertTrue(helper, "but not as a cow", wrongType.eval("out", List.class).isEmpty());

        // --- its own data ---
        var pos = probe(level, EntityDataNodes.Position.class, "entity", pig);
        Object v = pos.eval("out", Object.class);
        float[] c = com.lowdragmc.kilagraph.blueprint.nodes.vector.VectorNodes.components(v);
        assertEq(helper, "position x matches the entity", (float) pig.getX(), c[0], EPS);
        assertEq(helper, "position y matches the entity", (float) pig.getY(), c[1], EPS);

        assertEq(helper, "block pos", pig.blockPosition(),
                probe(level, EntityDataNodes.EntityBlockPos.class, "entity", pig)
                        .eval("out", BlockPos.class));

        assertEq(helper, "bounding box", pig.getBoundingBox(),
                probe(level, EntityDataNodes.BoundingBox.class, "entity", pig).eval("out", AABB.class));

        assertTrue(helper, "is a pig",
                probe(level, EntityDataNodes.IsType.class, "entity", pig, "type", EntityType.PIG)
                        .eval("out", Boolean.class));
        assertFalse(helper, "is not a cow",
                probe(level, EntityDataNodes.IsType.class, "entity", pig, "type", EntityType.COW)
                        .eval("out", Boolean.class));

        // a look direction is a unit vector
        float[] look = com.lowdragmc.kilagraph.blueprint.nodes.vector.VectorNodes.components(
                probe(level, EntityDataNodes.LookDirection.class, "entity", pig).eval("out", Object.class));
        float len = (float) Math.sqrt(look[0] * look[0] + look[1] * look[1] + look[2] * look[2]);
        assertEq(helper, "look direction is a unit vector", 1f, len, 1e-2f);

        // a pig holds nothing, and asking is not an error
        assertTrue(helper, "a pig's main hand is empty",
                probe(level, EntityDataNodes.HeldItem.class, "entity", pig)
                        .eval("out", ItemStack.class).isEmpty());

        // living-entity data: a pig has movement_speed but no speed effect
        var speed = probe(level, EntityDataNodes.Attribute.class, "entity", pig,
                "attribute", ResourceLocation.parse("minecraft:generic.movement_speed"));
        assertTrue(helper, "a pig has a movement speed attribute", speed.eval("found", Boolean.class));
        assertTrue(helper, "and it is positive", speed.eval("value", Double.class) > 0);

        var effect = probe(level, EntityDataNodes.HasEffect.class, "entity", pig,
                "effect", ResourceLocation.parse("minecraft:speed"));
        assertFalse(helper, "an untouched pig has no speed effect", effect.eval("has", Boolean.class));
        assertEq(helper, "and its amplifier reads zero", 0, effect.eval("amplifier", Integer.class).intValue());
        helper.succeed();
    }

    /** Enchantments are a datapack registry in 1.21, so this one needs the world. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void enchantmentProbeUsesTheWorldRegistry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var sharp = probe(level, RegistryProbeNodes.EnchantmentExists.class,
                "id", ResourceLocation.parse("minecraft:sharpness"));
        assertTrue(helper, "sharpness exists", sharp.eval("out", Boolean.class));
        assertTrue(helper, "and has a max level above zero",
                sharp.eval("maxLevel", Integer.class) > 0);

        var nope = probe(level, RegistryProbeNodes.EnchantmentExists.class,
                "id", ResourceLocation.parse("kilagraph:no_such_enchantment"));
        assertFalse(helper, "an unknown enchantment", nope.eval("out", Boolean.class));
        helper.succeed();
    }

    // ---- helpers -----------------------------------------------------------------------------

    /** A node in its own graph, wired to a level variable seeded with the test's live level. */
    private record LevelProbe(BlueprintGraph graph, NodeModel model, ServerLevel level) {
        <T> T eval(String output, Class<T> type) {
            var exec = new GraphExecutor(graph, EvaluationEnvironment.with(Map.of("level", level)));
            return exec.evaluate(model.getOutputsById().get(output), type);
        }
    }

    private static LevelProbe probe(ServerLevel level, Class<? extends Node> cls, Object... inputs) {
        var g = newGraph();
        NodeModel n = addNode(g, cls);
        for (int i = 0; i + 1 < inputs.length; i += 2) {
            setInputConstant(n, (String) inputs[i], inputs[i + 1]);
        }
        // Only wire the level for nodes that declare one — the entity-data nodes do not.
        PortModel levelPort = n.getInputsById().get("level");
        if (levelPort != null) {
            var v = (VariableDeclarationModelBase)
                    g.graphModel.createVariable("level", KGTypeHandles.LEVEL, null, VariableKind.INPUT);
            wire(g, levelPort,
                    g.graphModel.createVariableNode(v, new Vector2f(0, 0), null, null).getOutputPort());
        }
        return new LevelProbe(g, n, level);
    }
}
