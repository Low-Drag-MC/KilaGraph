package com.lowdragmc.kilagraph.rendertype.iris;

import com.lowdragmc.kilagraph.rendertype.compiler.InjectionSnippet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which surfaces a shaderpack program carries: one per graph a material uses, and the most recently released of the
 * ones no material uses any more — so a graph shown again needs no recompile, while an editing session doesn't grow
 * the injected programs by a surface per edit.
 */
class IrisSurfaceRegistryTest {

    private static final InjectionSnippet SNIPPET = new InjectionSnippet(List.of(), List.of(), "",
            "vec3(1.0), 1.0, vec3(0.0, 0.0, 1.0), 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0", false, false,
            List.of(), Map.of(), null, null, null, null, List.of(), false);

    @BeforeEach
    void reset() {
        IrisSurfaceRegistry.reset();
    }

    private static int register(String hash) {
        return IrisSurfaceRegistry.register(hash, SNIPPET);
    }

    private static boolean injected(int id) {
        return IrisSurfaceRegistry.snapshot().stream().anyMatch(s -> s.id() == id);
    }

    @Test
    void materialsOfOneGraphShareItsSurface() {
        int first = register("a");
        int generation = IrisSurfaceRegistry.generation();
        assertEquals(first, register("a"));
        assertEquals(generation, IrisSurfaceRegistry.generation(), "no recompile for a graph already injected");
    }

    @Test
    void aGraphShownAgainReusesItsSurface() {
        int id = register("a");
        IrisSurfaceRegistry.release("a");
        assertTrue(injected(id), "kept after its last material closes");
        int generation = IrisSurfaceRegistry.generation();
        assertEquals(id, register("a"));
        assertEquals(generation, IrisSurfaceRegistry.generation());
    }

    @Test
    void theLongestUnusedSurfaceIsDroppedPastTheLimit() {
        int oldest = register("old");
        IrisSurfaceRegistry.release("old");
        int[] newer = new int[IrisSurfaceRegistry.MAX_UNUSED_SURFACES];
        for (int i = 0; i < newer.length; i++) {
            newer[i] = register("g" + i);
            IrisSurfaceRegistry.release("g" + i);
        }
        assertFalse(injected(oldest), "the longest unused surface is dropped");
        for (int id : newer) assertTrue(injected(id), "the more recently released ones stay");

        int generation = IrisSurfaceRegistry.generation();
        int again = register("old");
        assertNotEquals(oldest, again, "a dropped surface's id isn't reused — older programs still hold it");
        assertTrue(IrisSurfaceRegistry.generation() > generation, "a dropped graph shown again recompiles");
    }

    @Test
    void aSurfaceInUseIsNeverDropped() {
        int live = register("live");
        for (int i = 0; i < IrisSurfaceRegistry.MAX_UNUSED_SURFACES + 5; i++) {
            register("g" + i);
            IrisSurfaceRegistry.release("g" + i);
        }
        assertTrue(injected(live));
    }

    @Test
    void aSurfaceStaysInUseUntilItsLastMaterialCloses() {
        register("a");
        int id = register("a");
        IrisSurfaceRegistry.release("a");
        for (int i = 0; i < IrisSurfaceRegistry.MAX_UNUSED_SURFACES + 5; i++) {
            register("g" + i);
            IrisSurfaceRegistry.release("g" + i);
        }
        assertTrue(injected(id), "one of its two materials still uses it");
    }

    @Test
    void surfacesInUseComeFirstThenTheMostRecentlyReleased() {
        int a = register("a"), b = register("b"), c = register("c");
        IrisSurfaceRegistry.release("b");
        IrisSurfaceRegistry.release("a");
        assertEquals(List.of(c, a, b), IrisSurfaceRegistry.snapshot().stream().map(IrisSurfaceRegistry.Surface::id).toList());
        assertEquals(java.util.Set.of(c), IrisSurfaceRegistry.liveIds());
    }

    @Test
    void releasingAGraphWithoutASurfaceIsIgnored() {
        IrisSurfaceRegistry.release("never registered");
        int id = register("a");
        IrisSurfaceRegistry.release("a");
        IrisSurfaceRegistry.release("a"); // a second close of the same graph changes nothing
        assertTrue(injected(id));
        assertEquals(id, register("a"));
    }
}
