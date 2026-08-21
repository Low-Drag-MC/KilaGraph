package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.geometry.AabbNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.geometry.BlockPosNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.geometry.ChunkPosNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.geometry.DirectionNodes;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3f;

import java.util.List;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;

/**
 * Block positions, bounding boxes, chunk coordinates and direction maths.
 *
 * <p>Values are chosen asymmetric wherever a symmetric one would let a swapped axis pass — a box from
 * (1,2,3) to (4,6,8) has three different edge lengths, so a node that mixed up Y and Z could not agree
 * with the expectation by coincidence.
 */
@GameTestHolder(Kilagraph.MODID)
public final class McGeometryGameTest {

    private static final float EPS = 1e-4f;

    private McGeometryGameTest() {
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void blockPosArithmetic(GameTestHelper helper) {
        var add = node(BlockPosNodes.Add.class, "a", new BlockPos(1, 2, 3), "b", new BlockPos(10, 20, 30));
        assertEq(helper, "add", new BlockPos(11, 22, 33), eval(add, "out", BlockPos.class));

        var sub = node(BlockPosNodes.Subtract.class, "a", new BlockPos(11, 22, 33), "b", new BlockPos(1, 2, 3));
        assertEq(helper, "subtract", new BlockPos(10, 20, 30), eval(sub, "out", BlockPos.class));

        // 3-4-5 in the XZ plane, so both outputs are exact and distinguishable
        var dist = node(BlockPosNodes.Distance.class, "a", BlockPos.ZERO, "b", new BlockPos(3, 0, 4));
        assertEq(helper, "distance", 5f, eval(dist, "out", Float.class), EPS);
        assertEq(helper, "distance squared", 25f, eval(dist, "sqr", Float.class), EPS);
        helper.succeed();
    }

    /**
     * The corner/centre distinction, which is the whole reason the node has an option.
     *
     * <p>A test that only checked one of them would pass against a node that ignored the option.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void blockPosVectorConversionsRespectTheCentreOption(GameTestHelper helper) {
        var centre = node(BlockPosNodes.ToVector.class, "in", new BlockPos(1, 2, 3));
        assertVec(helper, "centre", new float[] {1.5f, 2.5f, 3.5f}, eval(centre, "out", Object.class));

        var corner = node(BlockPosNodes.ToVector.class, "in", new BlockPos(1, 2, 3));
        setInputConstant(corner.model(), "center", false);
        assertVec(helper, "corner", new float[] {1f, 2f, 3f}, eval(corner, "out", Object.class));

        // containing() floors, so a point anywhere inside a block names that block — including the
        // negative side, where rounding and flooring disagree.
        var from = node(BlockPosNodes.FromVector.class, "in", new Vector3f(1.9f, 2.1f, 3.99f));
        assertEq(helper, "from vector floors", new BlockPos(1, 2, 3), eval(from, "out", BlockPos.class));
        var neg = node(BlockPosNodes.FromVector.class, "in", new Vector3f(-0.5f, -0.5f, -0.5f));
        assertEq(helper, "from vector floors below zero", new BlockPos(-1, -1, -1),
                eval(neg, "out", BlockPos.class));
        helper.succeed();
    }

    /**
     * {@code between} enumerates the box and says when it stopped.
     *
     * <p>The truncation case is asserted, not just the small one: a cap that silently returned a prefix
     * would look identical from the small case alone, and the whole point of the {@code truncated}
     * output is that a graph can tell.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void betweenEnumeratesAndReportsTruncation(GameTestHelper helper) {
        var small = node(BlockPosNodes.Between.class, "min", BlockPos.ZERO, "max", new BlockPos(1, 1, 1));
        List<?> positions = eval(small, "out", List.class);
        assertEq(helper, "a 2x2x2 box has 8 positions", 8, positions.size());
        assertFalse(helper, "8 positions is not truncated", eval(small, "truncated", Boolean.class));
        // every element distinct: betweenClosed reuses one mutable cursor, so a node that forgot to
        // copy would return N references to the last position
        assertEq(helper, "positions are distinct copies", 8, new java.util.HashSet<>(positions).size());

        var huge = node(BlockPosNodes.Between.class, "min", BlockPos.ZERO,
                "max", new BlockPos(200, 200, 200));
        assertEq(helper, "capped at the limit", BlockPosNodes.Between.LIMIT,
                eval(huge, "out", List.class).size());
        assertTrue(helper, "and it says so", eval(huge, "truncated", Boolean.class));
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void boundingBoxes(GameTestHelper helper) {
        var corners = node(AabbNodes.FromCorners.class, "a", new Vector3f(1, 2, 3),
                "b", new Vector3f(4, 6, 8));
        AABB box = eval(corners, "out", AABB.class);
        assertEq(helper, "minX", 1f, (float) box.minX, EPS);
        assertEq(helper, "maxZ", 8f, (float) box.maxZ, EPS);
        // corners in the other order must give the same box
        var flipped = node(AabbNodes.FromCorners.class, "a", new Vector3f(4, 6, 8),
                "b", new Vector3f(1, 2, 3));
        assertEq(helper, "corner order does not matter", box, eval(flipped, "out", AABB.class));

        var cube = node(AabbNodes.FromBlockPos.class, "pos", new BlockPos(5, 6, 7));
        assertEq(helper, "a block's box is one wide", 1f,
                (float) eval(cube, "out", AABB.class).getXsize(), EPS);

        // ofSize takes the FULL edge length, so 2 around the origin spans -1..1
        var around = node(AabbNodes.Around.class, "center", new Vector3f(0, 0, 0), "size", 2f);
        AABB a = eval(around, "out", AABB.class);
        assertEq(helper, "around: min", -1f, (float) a.minX, EPS);
        assertEq(helper, "around: max", 1f, (float) a.maxX, EPS);

        var inflated = node(AabbNodes.Inflate.class, "in", new AABB(0, 0, 0, 1, 1, 1), "amount", 1f);
        assertEq(helper, "inflate grows both sides", 3f,
                (float) eval(inflated, "out", AABB.class).getXsize(), EPS);
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void boundingBoxRelations(GameTestHelper helper) {
        AABB unit = new AABB(0, 0, 0, 1, 1, 1);
        AABB overlapping = new AABB(0.5, 0.5, 0.5, 2, 2, 2);
        AABB apart = new AABB(5, 5, 5, 6, 6, 6);

        var hit = node(AabbNodes.Intersects.class, "a", unit, "b", overlapping);
        assertTrue(helper, "overlapping boxes intersect", eval(hit, "out", Boolean.class));
        var miss = node(AabbNodes.Intersects.class, "a", unit, "b", apart);
        assertFalse(helper, "distant boxes do not", eval(miss, "out", Boolean.class));

        var inside = node(AabbNodes.Contains.class, "box", unit, "point", new Vector3f(0.5f, 0.5f, 0.5f));
        assertTrue(helper, "the centre is inside", eval(inside, "out", Boolean.class));
        var outside = node(AabbNodes.Contains.class, "box", unit, "point", new Vector3f(1.5f, 0.5f, 0.5f));
        assertFalse(helper, "a point past maxX is not", eval(outside, "out", Boolean.class));

        var union = node(AabbNodes.Union.class, "a", unit, "b", apart);
        AABB u = eval(union, "out", AABB.class);
        assertEq(helper, "union spans both", 0f, (float) u.minX, EPS);
        assertEq(helper, "union spans both", 6f, (float) u.maxX, EPS);

        var overlap = node(AabbNodes.Intersect.class, "a", unit, "b", overlapping);
        AABB i = eval(overlap, "out", AABB.class);
        assertEq(helper, "intersection min", 0.5f, (float) i.minX, EPS);
        assertEq(helper, "intersection max", 1f, (float) i.maxX, EPS);
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void chunkCoordinates(GameTestHelper helper) {
        var create = node(ChunkPosNodes.Create.class, "x", 3, "z", -2);
        assertEq(helper, "create", new ChunkPos(3, -2), eval(create, "out", ChunkPos.class));

        // block 40 is in chunk 2 (40 >> 4), and -1 is in chunk -1 — the negative side is where a
        // division-instead-of-shift bug shows up
        var from = node(ChunkPosNodes.FromBlockPos.class, "pos", new BlockPos(40, 64, -1));
        assertEq(helper, "from block pos", new ChunkPos(2, -1), eval(from, "out", ChunkPos.class));

        var origin = node(ChunkPosNodes.Origin.class, "in", new ChunkPos(2, -1));
        assertEq(helper, "origin is the chunk's corner", new BlockPos(32, 0, -16),
                eval(origin, "out", BlockPos.class));
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void directionMaths(GameTestHelper helper) {
        var rotate = node(DirectionNodes.Rotate.class, "in", Direction.NORTH,
                "rotation", Rotation.CLOCKWISE_90);
        assertEq(helper, "north rotated 90 clockwise is east", Direction.EAST,
                eval(rotate, "out", Direction.class));

        var mirror = node(DirectionNodes.MirrorNode.class, "in", Direction.EAST,
                "mirror", Mirror.FRONT_BACK);
        assertEq(helper, "east mirrored front-back is west", Direction.WEST,
                eval(mirror, "out", Direction.class));

        var cw = node(DirectionNodes.Turn.class, "in", Direction.NORTH, "clockwise", true);
        assertEq(helper, "turn clockwise about Y", Direction.EAST, eval(cw, "out", Direction.class));
        var ccw = node(DirectionNodes.Turn.class, "in", Direction.NORTH, "clockwise", false);
        assertEq(helper, "turn anticlockwise about Y", Direction.WEST, eval(ccw, "out", Direction.class));

        // north is -Z in Minecraft, which is the sign a hand-written normal table gets wrong
        var normal = node(DirectionNodes.Normal.class, "in", Direction.NORTH);
        assertEq(helper, "north x", 0, eval(normal, "x", Integer.class).intValue());
        assertEq(helper, "north y", 0, eval(normal, "y", Integer.class).intValue());
        assertEq(helper, "north z", -1, eval(normal, "z", Integer.class).intValue());

        var byName = node(DirectionNodes.FromName.class, "name", "up");
        assertEq(helper, "by name", Direction.UP, eval(byName, "out", Direction.class));
        assertTrue(helper, "by name found", eval(byName, "found", Boolean.class));
        var junk = node(DirectionNodes.FromName.class, "name", "sideways");
        assertFalse(helper, "an unknown name is not found", eval(junk, "found", Boolean.class));
        assertEq(helper, "and falls back to north", Direction.NORTH, eval(junk, "out", Direction.class));

        var nearest = node(DirectionNodes.Nearest.class, "vector", new Vector3f(0.1f, 0.9f, 0.2f));
        assertEq(helper, "mostly up is up", Direction.UP, eval(nearest, "out", Direction.class));

        var horizontal = node(DirectionNodes.All.class);
        assertEq(helper, "the horizontal plane has four faces", 4,
                eval(horizontal, "out", List.class).size());
        var vertical = node(DirectionNodes.All.class);
        setOption(vertical.model(), "plane", Direction.Plane.VERTICAL);
        assertEq(helper, "the vertical plane has two", 2, eval(vertical, "out", List.class).size());
        helper.succeed();
    }

    // ---- helpers -----------------------------------------------------------------------------

    private record Probe(BlueprintGraph graph, NodeModel model) {
    }

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

    private static void assertVec(GameTestHelper helper, String label, float[] expected, Object actual) {
        float[] got = com.lowdragmc.kilagraph.blueprint.nodes.vector.VectorNodes.components(actual);
        for (int i = 0; i < expected.length; i++) {
            assertEq(helper, label + " component " + i, expected[i], got[i], EPS);
        }
    }
}
