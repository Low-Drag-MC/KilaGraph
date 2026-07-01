package com.lowdragmc.kilagraph.test.gametest.blueprint;


import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTest;
import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.ItemStackCreateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.info.EntityInfoNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.info.InfoFieldBlock;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.info.ItemStackInfoNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.mc.MemberInfoRegistry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addBlock;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * The InfoNode context/block framework + {@link MemberInfoRegistry}. An
 * {@link com.lowdragmc.kilagraph.blueprint.nodes.mc.info.InfoContextNode} holds a {@code target};
 * each {@link InfoFieldBlock} added to it reads one selected property of that target.
 */
@GameTestHolder(Kilagraph.MODID)
public final class InfoNodeGameTest {
    private static final String MULTI_BLOCK = "info_multi_block";
    private static final String CACHE_IDENTITY = "info_cache_identity";
    private static final String OVERRIDE = "info_override_extra_exclude";
    private static final String UNKNOWN_KEY = "info_unknown_key_null";
    private static final String NULL_TARGET = "info_null_target_null";

    /** Reflection fixture for the override test (public so reflection sees its members). */
    public static final class Dummy {
        public int a = 7;
        public int getB() {
            return 9;
        }
    }

    private InfoNodeGameTest() {}

    /** One ItemStack context feeding three blocks reading three different properties. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void multiBlock(GameTestHelper helper) {
        var g = newGraph();
        var create = addNode(g, ItemStackCreateNode.class);
        setInputConstant(create, "item", Items.DIAMOND);
        setInputConstant(create, "count", 7);

        var ctx = addNode(g, ItemStackInfoNode.class);
        wire(g, ctx.getInputsById().get("target"), create.getOutputsById().get("out"));

        var countBlock = addBlock(g, ctx, InfoFieldBlock.class);
        setOption(countBlock, "field", "count");
        var maxBlock = addBlock(g, ctx, InfoFieldBlock.class);
        setOption(maxBlock, "field", "maxStackSize");
        var emptyBlock = addBlock(g, ctx, InfoFieldBlock.class);
        setOption(emptyBlock, "field", "isEmpty");

        var exec = new GraphExecutor(g);
        assertEq(helper, "count", 7, ((Number) exec.evaluate(countBlock.getOutputsById().get("value"), Object.class)).intValue());
        assertEq(helper, "maxStackSize", 64, ((Number) exec.evaluate(maxBlock.getOutputsById().get("value"), Object.class)).intValue());
        assertFalse(helper, "isEmpty", (Boolean) exec.evaluate(emptyBlock.getOutputsById().get("value"), Object.class));
        helper.succeed();
    }

    /** Member map is cached per class — same instance on repeated lookups. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void cacheIdentity(GameTestHelper helper) {
        var a = MemberInfoRegistry.membersFor(net.minecraft.world.item.ItemStack.class);
        var b = MemberInfoRegistry.membersFor(net.minecraft.world.item.ItemStack.class);
        assertTrue(helper, "same cached instance", a == b);
        assertTrue(helper, "has count", a.containsKey("count"));
        helper.succeed();
    }

    /** registerExtra adds a member; exclude removes a reflective one. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void overrideExtraExclude(GameTestHelper helper) {
        var base = MemberInfoRegistry.membersFor(Dummy.class);
        assertTrue(helper, "field a present", base.containsKey("a"));
        assertTrue(helper, "getter b present", base.containsKey("b"));

        MemberInfoRegistry.registerExtra(Dummy.class, "c", int.class, o -> 42);
        MemberInfoRegistry.exclude(Dummy.class, "a");

        var after = MemberInfoRegistry.membersFor(Dummy.class);
        assertFalse(helper, "a excluded", after.containsKey("a"));
        assertTrue(helper, "b kept", after.containsKey("b"));
        assertTrue(helper, "c added", after.containsKey("c"));
        var c = MemberInfoRegistry.member(Dummy.class, "c");
        assertEq(helper, "c value", 42, ((Number) c.getter().apply(new Dummy())).intValue());
        assertEq(helper, "b value", 9, ((Number) after.get("b").getter().apply(new Dummy())).intValue());
        helper.succeed();
    }

    /** An unknown member key yields a null output (no throw). */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void unknownKeyNull(GameTestHelper helper) {
        var g = newGraph();
        var create = addNode(g, ItemStackCreateNode.class);
        setInputConstant(create, "item", Items.DIAMOND);
        setInputConstant(create, "count", 1);

        var ctx = addNode(g, ItemStackInfoNode.class);
        wire(g, ctx.getInputsById().get("target"), create.getOutputsById().get("out"));
        var block = addBlock(g, ctx, InfoFieldBlock.class);
        setOption(block, "field", "totallyNotAField");

        var exec = new GraphExecutor(g);
        assertEq(helper, "unknown key → null", null, exec.evaluate(block.getOutputsById().get("value"), Object.class));
        helper.succeed();
    }

    /** A null target (wire-only Entity context, unconnected) yields a null output. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void nullTargetNull(GameTestHelper helper) {
        var g = newGraph();
        var ctx = addNode(g, EntityInfoNode.class);
        // target left unconnected — Entity has no embedded constant, so it resolves to null.
        var block = addBlock(g, ctx, InfoFieldBlock.class);
        setOption(block, "field", "x");

        var exec = new GraphExecutor(g);
        assertEq(helper, "null target → null", null, exec.evaluate(block.getOutputsById().get("value"), Object.class));
        helper.succeed();
    }
}
