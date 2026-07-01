package com.lowdragmc.kilagraph.test.gametest.blueprint;


import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTest;
import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.CacheClearNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.CacheNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ForNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import net.minecraft.gametest.framework.GameTestHelper;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/** Phase 3 — Cache memoisation and CacheClear invalidation (NODE_REF). */
@GameTestHolder(Kilagraph.MODID)
public final class CacheGameTest {
    private static final String CACHE_MEMOIZES = "exec_cache_memoizes";
    private static final String CACHE_CLEAR_RECOMPUTES = "exec_cache_clear_recomputes";
    private static final String CACHE_CLEAR_UNWIRED_NOOP = "exec_cache_clear_unwired_noop";
    private static final String CACHE_CLEAR_SELECTIVE = "exec_cache_clear_selective";

    private CacheGameTest() {}

    /**
     * For(3) body: SetVar("seen", Cache.cached); Cache.value ← For.index. The Cache memoises the
     * first index (0) and keeps serving it across iterations despite the loop's clearCache().
     * A plain index→SetVar wire would leave seen==2; with Cache it stays 0.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void cacheMemoizes(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var fr = addNode(g, ForNode.class);
        setInputConstant(fr, "count", 3);

        var cache = addNode(g, CacheNode.class);
        wire(g, cache.getInputsById().get("value"), fr.getOutputsById().get("index"));

        var setC = addNode(g, SetVarNode.class);
        setOption(setC, "varName", "seen");
        wire(g, setC.getInputsById().get("value"), cache.getOutputsById().get("cached"));

        wire(g, fr.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, setC.getInputsById().get("trigger"), fr.getOutputsById().get("body"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        Object seen = exec.getEnvironment().variables().get("seen");
        if (!(seen instanceof Number n) || n.intValue() != 0) {
            helper.fail("expected seen=0 (memoised first index), got " + seen);
            return;
        }
        helper.succeed();
    }

    /**
     * Same graph as {@link #cacheMemoizes}, but after SetVar a CacheClear(ref ← Cache.ref) fires.
     * Each iteration recomputes the Cache, so seen tracks the current index → final seen==2.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void cacheClearRecomputes(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var fr = addNode(g, ForNode.class);
        setInputConstant(fr, "count", 3);

        var cache = addNode(g, CacheNode.class);
        wire(g, cache.getInputsById().get("value"), fr.getOutputsById().get("index"));

        var setC = addNode(g, SetVarNode.class);
        setOption(setC, "varName", "seen");
        wire(g, setC.getInputsById().get("value"), cache.getOutputsById().get("cached"));

        var cc = addNode(g, CacheClearNode.class);
        wire(g, cc.getInputsById().get("ref"), cache.getOutputsById().get("ref"));

        wire(g, fr.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, setC.getInputsById().get("trigger"), fr.getOutputsById().get("body"));
        // body flow: SetVar -> CacheClear (clear after each read).
        wire(g, cc.getInputsById().get("in"), setC.getOutputsById().get("next"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        Object seen = exec.getEnvironment().variables().get("seen");
        if (!(seen instanceof Number n) || n.intValue() != 2) {
            helper.fail("expected seen=2 (CacheClear forces recompute each iter), got " + seen);
            return;
        }
        helper.succeed();
    }

    /**
     * Two independent Caches (A and B) both memoise For.index. Each iteration clears <em>only</em>
     * Cache A. So seenA tracks the live index (recomputes → final 2) while seenB keeps its first
     * memo (0). Proves {@code invalidateNode} is targeted, not a global cache wipe.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void cacheClearSelective(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var fr = addNode(g, ForNode.class);
        setInputConstant(fr, "count", 3);

        var cacheA = addNode(g, CacheNode.class);
        wire(g, cacheA.getInputsById().get("value"), fr.getOutputsById().get("index"));
        var cacheB = addNode(g, CacheNode.class);
        wire(g, cacheB.getInputsById().get("value"), fr.getOutputsById().get("index"));

        var setA = addNode(g, SetVarNode.class);
        setOption(setA, "varName", "seenA");
        wire(g, setA.getInputsById().get("value"), cacheA.getOutputsById().get("cached"));
        var setB = addNode(g, SetVarNode.class);
        setOption(setB, "varName", "seenB");
        wire(g, setB.getInputsById().get("value"), cacheB.getOutputsById().get("cached"));

        var cc = addNode(g, CacheClearNode.class);
        wire(g, cc.getInputsById().get("ref"), cacheA.getOutputsById().get("ref"));

        // body chain: setA -> setB -> clear(A only)
        wire(g, fr.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, setA.getInputsById().get("trigger"), fr.getOutputsById().get("body"));
        wire(g, setB.getInputsById().get("trigger"), setA.getOutputsById().get("next"));
        wire(g, cc.getInputsById().get("in"), setB.getOutputsById().get("next"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        Object seenA = exec.getEnvironment().variables().get("seenA");
        Object seenB = exec.getEnvironment().variables().get("seenB");
        boolean aOk = seenA instanceof Number an && an.intValue() == 2;
        boolean bOk = seenB instanceof Number bn && bn.intValue() == 0;
        if (!aOk || !bOk) {
            helper.fail("expected seenA=2 (cleared each iter) and seenB=0 (memo kept), got seenA=" + seenA + ", seenB=" + seenB);
            return;
        }
        helper.succeed();
    }

    /** CacheClear with an unwired ref must be a harmless no-op (and still flow). */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void cacheClearUnwiredNoop(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var cc = addNode(g, CacheClearNode.class);
        wire(g, cc.getInputsById().get("in"), entry.getOutputsById().get("next"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        helper.succeed();
    }
}
