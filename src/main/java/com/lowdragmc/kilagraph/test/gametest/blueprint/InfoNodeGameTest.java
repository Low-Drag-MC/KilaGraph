package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.block.BlockEntityInfoBlocks;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.block.BlockEntityInfoNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.EntityInfoBlocks;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.EntityInfoNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.PlayerInfoBlocks;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.PlayerInfoNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.world.LevelInfoBlocks;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.world.LevelInfoNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.BlockNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.UseWithContext;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addBlock;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;

/**
 * The context/property-block mechanism itself, as opposed to what any one block reads (that is
 * {@link McInfoBlockGameTest}).
 *
 * <p>Four things have to hold for the design to work: several blocks in one context all see the same
 * target; a missing target degrades to null instead of throwing; a block only appears in the contexts it
 * declares; and a block whose declared type does not match the context's target reads as absent rather
 * than class-casting. The last two are what replaced a reflective property picker, so they are the ones
 * worth pinning.
 */
@GameTestHolder(Kilagraph.MODID)
public final class InfoNodeGameTest {

    private InfoNodeGameTest() {
    }

    /** One context feeding several blocks: each reads a different property of the same target. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void oneTargetManyBlocks(GameTestHelper helper) {
        Entity pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));

        var g = newGraph();
        var ctx = addNode(g, EntityInfoNode.class);
        setInputConstant(ctx, "target", pig);
        var pos = addBlock(g, ctx, EntityInfoBlocks.BlockPosition.class);
        var identity = addBlock(g, ctx, EntityInfoBlocks.Identity.class);
        var state = addBlock(g, ctx, EntityInfoBlocks.State.class);

        var exec = new GraphExecutor(g);
        assertEq(helper, "block position", pig.blockPosition(),
                exec.evaluate(pos.getOutputsById().get("value"), BlockPos.class));
        assertEq(helper, "entity id", pig.getId(),
                exec.evaluate(identity.getOutputsById().get("id"), Integer.class).intValue());
        assertTrue(helper, "alive", exec.evaluate(state.getOutputsById().get("alive"), Boolean.class));
        helper.succeed();
    }

    /**
     * No target wired: every output reads null, and nothing throws.
     *
     * <p>A half-built graph has to stay evaluable — the alternative would let one unwired context take
     * down branches that do not depend on it.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void missingTargetReadsNull(GameTestHelper helper) {
        var g = newGraph();
        var ctx = addNode(g, EntityInfoNode.class);
        // target deliberately left unconnected — Entity is wire-only, so it has no embedded constant
        var block = addBlock(g, ctx, EntityInfoBlocks.Identity.class);

        var exec = new GraphExecutor(g);
        assertEq(helper, "uuid is null", null,
                exec.evaluate(block.getOutputsById().get("uuid"), Object.class));
        assertEq(helper, "name is null", null,
                exec.evaluate(block.getOutputsById().get("name"), Object.class));
        helper.succeed();
    }

    /**
     * The model refuses a block its context does not accept.
     *
     * <p>Scoping is enforced at insert time by LDLib2, not just filtered in the UI — attaching a
     * player-only block to an entity context throws rather than producing a block that reads nothing.
     * That is the stronger guarantee, and it is why {@code InfoPropertyBlock}'s runtime type check is
     * defence in depth rather than the mechanism: a mismatched pair cannot be built through this API at
     * all, so the check only covers a target whose runtime type is narrower than the port's.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void contextRefusesAForeignBlock(GameTestHelper helper) {
        var g = newGraph();
        var ctx = addNode(g, EntityInfoNode.class);

        boolean threw = false;
        try {
            addBlock(g, ctx, PlayerInfoBlocks.Food.class);
        } catch (RuntimeException expected) {
            threw = true;
        }
        assertTrue(helper, "an entity context rejects a player block", threw);

        // And the accepted pairing really does attach, so the rejection above is about scoping rather
        // than about addBlock being broken.
        assertTrue(helper, "but accepts an entity block",
                addBlock(g, ctx, EntityInfoBlocks.Identity.class) != null);
        helper.succeed();
    }

    /**
     * Scoping: which blocks each context accepts.
     *
     * <p>{@code PlayerInfoNode} takes entity blocks as well as its own — that is the whole reason it is a
     * separate context rather than a duplicate one — while {@code EntityInfoNode} takes only entity
     * blocks, and neither takes another context's.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void blocksAreScopedToTheirContexts(GameTestHelper helper) {
        assertTrue(helper, "entity context takes entity blocks",
                accepts(EntityInfoNode.class, EntityInfoBlocks.Position.class));
        assertTrue(helper, "player context takes entity blocks too",
                accepts(PlayerInfoNode.class, EntityInfoBlocks.Position.class));
        assertTrue(helper, "player context takes player blocks",
                accepts(PlayerInfoNode.class, PlayerInfoBlocks.Food.class));

        assertFalse(helper, "entity context does not take player blocks",
                accepts(EntityInfoNode.class, PlayerInfoBlocks.Food.class));
        assertFalse(helper, "entity context does not take level blocks",
                accepts(EntityInfoNode.class, LevelInfoBlocks.RainLevel.class));
        assertFalse(helper, "level context does not take entity blocks",
                accepts(LevelInfoNode.class, EntityInfoBlocks.Position.class));
        assertFalse(helper, "block entity context does not take level blocks",
                accepts(BlockEntityInfoNode.class, LevelInfoBlocks.RainLevel.class));
        assertTrue(helper, "block entity context takes its own",
                accepts(BlockEntityInfoNode.class, BlockEntityInfoBlocks.Position.class));
        helper.succeed();
    }

    /**
     * Every block declaring a position emits the graph's vector type, never a {@code Vec3}.
     *
     * <p>This used to need a whole curation registry: reflection typed these members by their Java return
     * type, so they came out as {@code Vec3} — a pin the graph cannot carry, which rendered as a dead end
     * and had to be swapped out member by member. A declared output port cannot get this wrong, and this
     * asserts it stays that way.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void positionBlocksUseTheGraphVectorType(GameTestHelper helper) {
        Entity pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        for (Class<? extends BlockNode> cls : java.util.List.of(
                EntityInfoBlocks.Position.class,
                EntityInfoBlocks.EyePosition.class,
                EntityInfoBlocks.LookDirection.class,
                EntityInfoBlocks.Velocity.class)) {
            var g = newGraph();
            var ctx = addNode(g, EntityInfoNode.class);
            setInputConstant(ctx, "target", pig);
            var block = addBlock(g, ctx, cls);
            var port = block.getOutputsById().get("value");
            assertEq(helper, cls.getSimpleName() + " outputs VEC3", KGTypeHandles.VEC3, port.getDataTypeHandle());

            Object value = new GraphExecutor(g).evaluate(port, Object.class);
            assertTrue(helper, cls.getSimpleName() + " is a JOML vector", value instanceof org.joml.Vector3f);
        }
        helper.succeed();
    }

    /** Whether {@code context} would accept {@code block}, without building a graph for it. */
    private static boolean accepts(Class<? extends com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.ContextNode> context,
                                   Class<? extends BlockNode> block) {
        // Read the annotation directly: ContextNode.acceptsBlock consults @UseWithContext first and only
        // falls back to getSupportBlocks(), which needs a live graph model. The annotation is the
        // declaration under test.
        UseWithContext scope = block.getAnnotation(UseWithContext.class);
        if (scope == null) return false;
        for (var allowed : scope.value()) {
            if (allowed.isAssignableFrom(context)) return true;
        }
        return false;
    }
}
