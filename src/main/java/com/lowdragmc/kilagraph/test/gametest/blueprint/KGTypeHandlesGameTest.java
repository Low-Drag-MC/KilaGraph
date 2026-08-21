package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;

/**
 * The pin-type vocabulary: which types the graph offers, and the one invariant every offered type has
 * to satisfy.
 *
 * <h2>Why a defaultless handle is a bug and not a blank</h2>
 * LDLib2 gives every non-EXEC pin type an embedded constant automatically, initialised from
 * {@code TypeHandle.getDefaultValue()}. When that returns null the failure is type-dependent and in
 * both shapes silent:
 * <ul>
 *   <li>an <b>accessor-backed</b> type NPEs inside its own configurator, which dereferences the value
 *       to build the editor row (this is why {@code BLOCK_POS}/{@code BLOCK_STATE} carry explicit
 *       defaults);</li>
 *   <li>an <b>enum</b> renders through {@code SelectorConfigurator}, which displays the first candidate
 *       when the value is null <em>without writing it back</em> — so the node shows a plausible value
 *       and emits null. That was the state of {@code Direction} until this suite existed.</li>
 * </ul>
 * Hence {@link #everyConstantTypeHasANonNullDefault}: it is a property of the whole vocabulary rather
 * than of any one type, so it belongs in one enumerating test rather than in each type's own.
 */
@GameTestHolder(Kilagraph.MODID)
public final class KGTypeHandlesGameTest {

    private KGTypeHandlesGameTest() {
    }

    /**
     * Every type the item library offers as a Constant node must have a non-null default value.
     *
     * <p>Enumerated from {@code getLibrarySupportTypes()} rather than hand-listed, so a type added
     * later cannot skip the check by not being mentioned here.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void everyConstantTypeHasANonNullDefault(GameTestHelper helper) {
        List<TypeHandle> offered = newGraph().getLibrarySupportTypes();
        assertTrue(helper, "the library offers some constant types", !offered.isEmpty());
        for (TypeHandle handle : offered) {
            assertTrue(helper,
                    "type " + handle.getIdentification() + " is offered as a constant but has no "
                            + "default value, so its constant node would emit null",
                    handle.getDefaultValue() != null);
        }
        helper.succeed();
    }

    /**
     * The two defaults that were missing, pinned by value.
     *
     * <p>Separate from the enumerating test above because that one only proves non-nullness. A wrong
     * default is a different bug from an absent one, and for {@code Direction} the wrong default is
     * the one a reader would assume: {@code SelectorConfigurator} shows {@code DOWN} first, so
     * anything that "fixed" this by taking the first enum constant would still disagree with every
     * node in the library, all of which default their Direction inputs to NORTH.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void directionAndNbtDefaultsAreTheOnesTheNodesUse(GameTestHelper helper) {
        assertEq(helper, "Direction default", Direction.NORTH, TypeHandles.DIRECTION.getDefaultValue());

        Object nbt = KGTypeHandles.NBT_COMPOUND.getDefaultValue();
        assertTrue(helper, "CompoundTag default is a CompoundTag, got " + nbt, nbt instanceof CompoundTag);
        assertTrue(helper, "CompoundTag default is empty", ((CompoundTag) nbt).isEmpty());
        helper.succeed();
    }

    /**
     * Wire-only types belong in the type pickers but not in the constant library.
     *
     * <p>The two lists answer different questions — "can a port carry this" versus "can a literal of
     * this be authored" — and the default for the second is the first, which is wrong. A {@code Level}
     * has no accessor, so its constant node renders an empty inspector row and emits null; offering it
     * is offering a node that cannot do anything.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void wireOnlyTypesArePickableButNotAuthorable(GameTestHelper helper) {
        var graph = newGraph();
        List<TypeHandle> pickable = graph.getSupportTypes();
        List<TypeHandle> authorable = graph.getLibrarySupportTypes();

        for (TypeHandle wireOnly : List.of(KGTypeHandles.LEVEL, KGTypeHandles.ENTITY,
                KGTypeHandles.PLAYER, KGTypeHandles.BLOCK_ENTITY,
                KGTypeHandles.LIST, KGTypeHandles.MAP, KGTypeHandles.NODE_REF)) {
            assertTrue(helper, wireOnly.getIdentification() + " should be pickable as a port type",
                    pickable.contains(wireOnly));
            assertTrue(helper, wireOnly.getIdentification() + " must not be offered as a constant",
                    !authorable.contains(wireOnly));
        }
        helper.succeed();
    }

    /**
     * The new value types are reachable from both lists.
     *
     * <p>Guards the step that is easy to forget: minting a handle in {@code KGTypeHandles} does
     * nothing on its own, because both surfaces are hand-maintained lists in {@code BlueprintGraph}.
     * {@code NBT_COMPOUND} was minted and left out of both for exactly that reason.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void newValueTypesAreSurfacedInBothLists(GameTestHelper helper) {
        var graph = newGraph();
        List<TypeHandle> pickable = graph.getSupportTypes();
        List<TypeHandle> authorable = graph.getLibrarySupportTypes();

        for (TypeHandle value : List.of(KGTypeHandles.RESOURCE_LOCATION, KGTypeHandles.AABB,
                KGTypeHandles.CHUNK_POS, KGTypeHandles.TEXT, KGTypeHandles.ROTATION,
                KGTypeHandles.MIRROR, KGTypeHandles.AXIS, KGTypeHandles.EQUIPMENT_SLOT,
                KGTypeHandles.NBT_COMPOUND)) {
            assertTrue(helper, value.getIdentification() + " missing from getSupportTypes()",
                    pickable.contains(value));
            assertTrue(helper, value.getIdentification() + " missing from getLibrarySupportTypes()",
                    authorable.contains(value));
        }
        helper.succeed();
    }
}
