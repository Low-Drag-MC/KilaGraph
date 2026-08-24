package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

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
public final class KGTypeHandlesGameTest {
    private static final String EVERY_CONSTANT_TYPE_HAS_A_NON_NULL_DEFAULT = "kg_type_handles_every_constant_type_has_a_non_null_default";
    private static final String DIRECTION_AND_NBT_DEFAULTS_ARE_THE_ONES_THE_NODES_USE = "kg_type_handles_direction_and_nbt_defaults_are_the_ones_the_nodes_use";
    private static final String WIRE_ONLY_TYPES_ARE_PICKABLE_BUT_NOT_AUTHORABLE = "kg_type_handles_wire_only_types_are_pickable_but_not_authorable";
    private static final String NEW_VALUE_TYPES_ARE_SURFACED_IN_BOTH_LISTS = "kg_type_handles_new_value_types_are_surfaced_in_both_lists";

    public static void registerFunctions() {
        KGGameTests.registerFunction(EVERY_CONSTANT_TYPE_HAS_A_NON_NULL_DEFAULT, KGTypeHandlesGameTest::everyConstantTypeHasANonNullDefault);
        KGGameTests.registerFunction(DIRECTION_AND_NBT_DEFAULTS_ARE_THE_ONES_THE_NODES_USE, KGTypeHandlesGameTest::directionAndNbtDefaultsAreTheOnesTheNodesUse);
        KGGameTests.registerFunction(WIRE_ONLY_TYPES_ARE_PICKABLE_BUT_NOT_AUTHORABLE, KGTypeHandlesGameTest::wireOnlyTypesArePickableButNotAuthorable);
        KGGameTests.registerFunction(NEW_VALUE_TYPES_ARE_SURFACED_IN_BOTH_LISTS, KGTypeHandlesGameTest::newValueTypesAreSurfacedInBothLists);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{
                EVERY_CONSTANT_TYPE_HAS_A_NON_NULL_DEFAULT, DIRECTION_AND_NBT_DEFAULTS_ARE_THE_ONES_THE_NODES_USE, WIRE_ONLY_TYPES_ARE_PICKABLE_BUT_NOT_AUTHORABLE,
                NEW_VALUE_TYPES_ARE_SURFACED_IN_BOTH_LISTS
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    private KGTypeHandlesGameTest() {
    }

    /**
     * Every type the item library offers as a Constant node must have a non-null default value.
     *
     * <p>Enumerated from {@code getLibrarySupportTypes()} rather than hand-listed, so a type added
     * later cannot skip the check by not being mentioned here.</p>
     */
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
