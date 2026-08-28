package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Every port whose Java type has an override in {@code KGTypeHandles} must carry that override's
 * handle.
 *
 * <h2>The shape of the bug this exists for</h2>
 * A handful of types are registered in {@code KGTypeHandles.OVERRIDES} so that a Java type resolves
 * to a KilaGraph-canonical handle rather than to whatever LDLib2 would mint for the bare class:
 * {@code List}, {@code Map}/{@code HashMap}, {@code NodeRef} and the three vectors. Declaring a port
 * from an <em>annotated field</em> goes through {@code KGTypeHandles.handleFor} and picks the
 * override up. Declaring one <em>imperatively</em> with the class — {@code addInputPort(id,
 * List.class)} — does not: that overload resolves through LDLib2's own helper, which knows nothing
 * about the overrides. The port then carries a handle with a different identification, and every
 * property the override existed to attach is silently absent.
 *
 * <p>What that costs depends on which type slipped, and none of it is visible headlessly:</p>
 * <ul>
 *   <li>{@code LIST}/{@code MAP}/{@code NODE_REF} are registered <b>without a default value</b>
 *       precisely so their ports get no embedded constant. A bare-class port does get one, and
 *       building its editor asks a collection that names no element type for its element type. That
 *       throws inside the screen tick, so it does not break one inspector row — it closes the
 *       editor.</li>
 *   <li>The vectors carry a custom colour, a default value, and the identification that
 *       {@code KGGraphModel.canAssignTo} matches on to allow VEC2↔VEC3↔VEC4. A bare-class vector
 *       port loses all three: it will not accept another width, and — per {@code KGTypeHandles}'
 *       own warning — a vector handle with no default is a null dereference the first time someone
 *       drops the node.</li>
 * </ul>
 *
 * <h2>Why it is checked against the table rather than against a list of types</h2>
 * The first version of this test asked "is this a raw collection, and if so is it LIST/MAP". That is
 * the symptom of the one node that had the bug, not the rule being broken — and it says nothing
 * about vectors or {@code NodeRef}, which fail differently and just as silently. Asking
 * {@code lookupOverride} instead states the actual invariant, and a type registered in
 * {@code KGTypeHandles} tomorrow is covered without anyone remembering to come back here.
 */
@GameTestHolder(Kilagraph.MODID)
public final class PortConstantTypeGameTest {

    private PortConstantTypeGameTest() {}

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void overriddenTypesUseTheCanonicalHandle(GameTestHelper helper) {
        KGTypeHandles.init();
        var failures = new ArrayList<String>();
        int checked = 0;
        int unspawnable = 0;

        for (var nodeClass : BlueprintGraph.NODE_REGISTRY.getNodeClasses()) {
            NodeModel model;
            try {
                model = KGGameTestHelpers.addRegisteredNode(KGGameTestHelpers.newGraph(), nodeClass);
            } catch (Throwable t) {
                // Spawning is another test's business; a node that cannot spawn has no ports to check.
                unspawnable++;
                continue;
            }
            if (model == null) continue;
            checked++;
            String name = nodeClass.getSimpleName();

            for (var entry : model.getInputsById().entrySet()) {
                check(failures, name, "input " + entry.getKey(), entry.getValue());
            }
            // Outputs cannot produce the editor crash — only an unconnected input gets a constant —
            // but the handle is still what decides wire compatibility, and what
            // CustomGraphModelImpl.detectSupportedTypes harvests into the graph's type pickers. One
            // stray output would offer a second, wrong "List" in every dropdown.
            for (var port : model.getOutputsByDisplayOrder()) {
                check(failures, name, "output " + port.getPortId(), port);
            }
            // An option is a port with a constant, so it fails exactly the way an input does.
            for (var option : model.getNodeOptions()) {
                check(failures, name, "option " + option.id, option.portModel);
            }
        }

        // A floor, not a formality: the catch above turns a node that cannot spawn into a silent
        // skip, so a change that broke spawning for most of the registry would leave this test
        // passing while it checked almost nothing.
        if (checked == 0) {
            helper.fail("no blueprint nodes were registered at all — the registry did not scan");
            return;
        }
        if (unspawnable > checked) {
            helper.fail(unspawnable + " of " + (checked + unspawnable) + " node classes failed to "
                    + "spawn, so this swept almost nothing — fix spawning before trusting a pass");
            return;
        }
        if (!failures.isEmpty()) {
            helper.fail(failures.size() + " port(s) bypassing a KGTypeHandles override: "
                    + String.join(" | ", failures));
            return;
        }
        helper.succeed();
    }

    private static void check(List<String> failures, String node, String where, PortModel port) {
        if (port == null) return;
        TypeHandle expected = overrideFor(port.getDataType());
        if (expected == null) return;   // not an overridden type — nothing to say about it
        TypeHandle actual = port.getDataTypeHandle();
        if (expected.equals(actual)) return;
        failures.add(node + "." + where + " resolves to " + typeName(port)
                + ", which KGTypeHandles overrides to " + expected.getIdentification()
                + ", but the port carries " + (actual == null ? "<null>" : actual.getIdentification())
                + " — declare it with the KGTypeHandles handle, not the bare class");
    }

    /**
     * {@link KGTypeHandles#lookupOverride} plus the raw-type fallback {@code handleFor} has.
     *
     * <p>Without it a port whose type is {@code List<Float>} rather than bare {@code List} is a
     * {@code ParameterizedType}, which is not a key in the override table, so the check would skip
     * precisely the ports this test says are the well-behaved ones.</p>
     */
    @Nullable
    private static TypeHandle overrideFor(@Nullable Type type) {
        TypeHandle direct = KGTypeHandles.lookupOverride(type);
        if (direct != null) return direct;
        return type instanceof ParameterizedType pt
                ? KGTypeHandles.lookupOverride(pt.getRawType()) : null;
    }

    private static String typeName(PortModel port) {
        var type = port.getDataType();
        return type instanceof Class<?> c ? c.getSimpleName() : String.valueOf(type);
    }
}
