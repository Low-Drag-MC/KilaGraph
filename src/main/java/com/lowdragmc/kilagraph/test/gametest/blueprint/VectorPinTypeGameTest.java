package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.vector.VectorGeometryNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.vector.VectorMathNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.vector.VectorNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.vector.VectorStructNodes;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.kilagraph.graph.type.Vectors;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addRegisteredNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * What a vector pin's <em>type</em> promises, as opposed to what the arithmetic behind it does.
 *
 * <p>Every operation here was already width-polymorphic at runtime and tested as such. What this
 * file guards is the thing a graph author can actually see: a {@code VECTOR} pin means "any width,
 * answered in kind" and a {@code VEC3} pin means "genuinely three-dimensional, anything wider is
 * truncated". Nothing in the arithmetic notices if a node is given the wrong one of those, so the
 * split is only real if it is asserted.
 */
@GameTestHolder(Kilagraph.MODID)
public final class VectorPinTypeGameTest {

    private static final float EPS = 1e-4f;

    /** Reads whatever width arrives and answers in kind — every vector pin must be VECTOR. */
    private static final List<Class<? extends Node>> POLYMORPHIC = List.of(
            VectorNodes.Break.class, VectorNodes.Add.class, VectorNodes.Subtract.class,
            VectorNodes.Scale.class, VectorNodes.Dot.class, VectorNodes.Length.class,
            VectorNodes.Normalize.class, VectorNodes.Distance.class, VectorNodes.Lerp.class,
            VectorNodes.Flatten.class,
            VectorStructNodes.Swizzle.class, VectorStructNodes.Concat.class,
            VectorStructNodes.Append.class, VectorStructNodes.SetComponent.class,
            VectorStructNodes.GetComponent.class,
            VectorMathNodes.Multiply.class, VectorMathNodes.Divide.class,
            VectorMathNodes.Negate.class, VectorMathNodes.Abs.class, VectorMathNodes.Min.class,
            VectorMathNodes.Max.class, VectorMathNodes.Clamp.class, VectorMathNodes.Round.class,
            VectorGeometryNodes.LengthSquared.class, VectorGeometryNodes.DistanceSquared.class,
            VectorGeometryNodes.ClampLength.class, VectorGeometryNodes.Project.class,
            VectorGeometryNodes.Reject.class, VectorGeometryNodes.Reflect.class,
            VectorGeometryNodes.AngleBetween.class, VectorGeometryNodes.MoveTowards.class,
            VectorGeometryNodes.NearlyEquals.class);

    /** Reads the first three of anything and answers a Vector3 — every vector pin must be VEC3. */
    private static final List<Class<? extends Node>> THREE_DIMENSIONAL = List.of(
            VectorNodes.Cross.class, VectorNodes.YawBetween.class,
            VectorGeometryNodes.RotateAxis.class, VectorGeometryNodes.FromRotation.class,
            VectorGeometryNodes.ToRotation.class);

    private VectorPinTypeGameTest() {
    }

    /**
     * The split itself: polymorphic nodes carry VECTOR pins, three-dimensional ones carry VEC3.
     *
     * <p>This is the whole point of the {@code VECTOR} handle. Nothing at runtime can tell the two
     * apart — the arithmetic reads {@code components()} either way — so a node that drifted onto the
     * wrong handle would keep working while its pin colour told the author the opposite of the truth.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void polymorphicPinsSayVectorAndThreeDimensionalOnesSayVec3(GameTestHelper helper) {
        var failures = new ArrayList<String>();
        for (Class<? extends Node> cls : POLYMORPHIC) {
            forEachVectorPort(cls, (where, handle) -> {
                if (!KGTypeHandles.VECTOR.equals(handle)) {
                    failures.add(cls.getSimpleName() + "." + where + " is "
                            + handle.getIdentification() + ", but the node reads any width — use VECTOR");
                }
            });
        }
        for (Class<? extends Node> cls : THREE_DIMENSIONAL) {
            forEachVectorPort(cls, (where, handle) -> {
                if (!KGTypeHandles.VEC3.equals(handle)) {
                    failures.add(cls.getSimpleName() + "." + where + " is "
                            + handle.getIdentification() + ", but the node is 3D — use VEC3");
                }
            });
        }
        if (!failures.isEmpty()) {
            helper.fail(failures.size() + " vector pin(s) on the wrong handle: "
                    + String.join(" | ", failures));
            return;
        }
        helper.succeed();
    }

    /**
     * Make/Make2/Make4 keep their exact widths.
     *
     * <p>They are the one family that is not polymorphic and not 3D: each produces one specific width
     * and its output type is the only thing that says which. Putting VECTOR on them would throw that
     * away and leave three nodes whose only difference was invisible.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void theMakeNodesAdvertiseTheExactWidthTheyProduce(GameTestHelper helper) {
        assertEq(helper, "vector_make out", KGTypeHandles.VEC3, outputHandle(VectorNodes.Make.class));
        assertEq(helper, "vector_make2 out", KGTypeHandles.VEC2, outputHandle(VectorNodes.Make2.class));
        assertEq(helper, "vector_make4 out", KGTypeHandles.VEC4, outputHandle(VectorNodes.Make4.class));
        helper.succeed();
    }

    /**
     * A VECTOR constant keeps its width across a save.
     *
     * <p>The reason {@code Vectors.CODEC} exists. A VECTOR port resolves to {@code Vector3f}, so the
     * default {@code AccessorRegistries} path writes exactly three floats — a Vector2 literal would
     * come back a Vector3 and a Vector4 would lose its w, silently, and only after a reload. The
     * width picker in the editor would look like it worked right up until you closed the world.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aVectorConstantKeepsItsWidthAcrossASave(GameTestHelper helper) {
        BlueprintGraph g = newGraph();
        NodeModel node = addRegisteredNode(g, VectorMathNodes.Multiply.class);
        var uid = node.getUid();
        setInputConstant(node, "a", new Vector2f(6f, 8f));
        setInputConstant(node, "b", new Vector4f(1f, 2f, 3f, 4f));

        var provider = Platform.getFrozenRegistry();
        CompoundTag snapshot = g.graphModel.serializeNBT(provider);

        // Overwrite both with a width that is neither, so a restore that silently produced Vector3
        // could not pass by having never changed.
        setInputConstant(node, "a", new Vector3f(-1f, -1f, -1f));
        setInputConstant(node, "b", new Vector3f(-1f, -1f, -1f));

        g.graphModel.deserializeNBT(provider, snapshot);

        // Re-resolved by uid, not reused: deserialize rebuilds the node models, so the reference
        // from before the round-trip is detached and still holds the overwrite.
        NodeModel restored = (NodeModel) g.graphModel.getModel(uid);
        assertTrue(helper, "the node survives the round-trip", restored != null);
        Object a = constant(restored, "a");
        Object b = constant(restored, "b");
        assertTrue(helper, "a Vector2 constant comes back a Vector2, got " + a, a instanceof Vector2f);
        assertVec(helper, "restored a", new float[] {6f, 8f}, a);
        assertTrue(helper, "a Vector4 constant comes back a Vector4, got " + b, b instanceof Vector4f);
        assertVec(helper, "restored b", new float[] {1f, 2f, 3f, 4f}, b);
        helper.succeed();
    }

    /**
     * The scalar pins on these nodes are still plain FLOAT/BOOL pins.
     *
     * <p>Moving to imperative declaration meant naming those types as {@code Float.class} /
     * {@code Boolean.class} where the annotated fields had said {@code float} / {@code boolean}.
     * Those agree only because {@code TypeHandleHelpers.convertType} normalises primitives to their
     * wrappers — and if they ever stopped agreeing, the symptom would not be a failing evaluation
     * but a wire the editor silently refuses to draw.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void scalarPinsKeepTheOrdinaryScalarHandles(GameTestHelper helper) {
        assertEq(helper, "vector_length out", TypeHandles.FLOAT, outputHandle(VectorNodes.Length.class));
        assertEq(helper, "vector_dot out", TypeHandles.FLOAT, outputHandle(VectorNodes.Dot.class));
        assertEq(helper, "vector_get_component out", TypeHandles.FLOAT,
                outputHandle(VectorStructNodes.GetComponent.class));
        assertEq(helper, "vector_nearly_equals out", TypeHandles.BOOL,
                outputHandle(VectorGeometryNodes.NearlyEquals.class));

        // And the wire the editor would have refused: a vector reduction feeding ordinary maths.
        BlueprintGraph g = newGraph();
        NodeModel length = addNode(g, VectorNodes.Length.class);
        NodeModel add = addNode(g, AddNode.class);
        wire(g, add.getInputsById().get("in1"), length.getOutputsById().get("out"));
        assertTrue(helper, "a vector Length can drive a math Add",
                add.getInputsById().get("in1").getConnectedPorts()
                        .contains(length.getOutputsById().get("out")));

        // ...and a vector's scalar input taking one back, which is the other direction.
        NodeModel scale = addNode(g, VectorNodes.Scale.class);
        wire(g, scale.getInputsById().get("scale"), length.getOutputsById().get("out"));
        assertTrue(helper, "a float can drive a vector Scale's factor",
                scale.getInputsById().get("scale").getConnectedPorts()
                        .contains(length.getOutputsById().get("out")));
        helper.succeed();
    }

    /** Any width reaches a VECTOR pin, which is the whole promise the handle makes. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void everyWidthReachesAVectorPin(GameTestHelper helper) {
        assertReaches(helper, "Vector2", VectorNodes.Make2.class);
        assertReaches(helper, "Vector3", VectorNodes.Make.class);
        assertReaches(helper, "Vector4", VectorNodes.Make4.class);
        helper.succeed();
    }

    private static void assertReaches(GameTestHelper helper, String label, Class<? extends Node> maker) {
        BlueprintGraph g = newGraph();
        NodeModel source = addNode(g, maker);
        NodeModel sink = addNode(g, VectorMathNodes.Multiply.class);
        wire(g, sink.getInputsById().get("a"), source.getOutputsById().get("out"));
        assertTrue(helper, label + " reaches a VECTOR pin",
                sink.getInputsById().get("a").getConnectedPorts()
                        .contains(source.getOutputsById().get("out")));
    }

    /** A fresh VECTOR pin starts at a usable width-3 value rather than null. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aFreshVectorPinDefaultsToWidthThree(GameTestHelper helper) {
        BlueprintGraph g = newGraph();
        NodeModel node = addNode(g, VectorMathNodes.Multiply.class);
        Object value = constant(node, "a");
        // Not decoration: the editor builds the inline value editor straight off this, and the
        // width picker reads its length. A null here is the NPE that KGTypeHandles warns about.
        assertTrue(helper, "an unwired VECTOR pin has a value, got " + value, value != null);
        assertEq(helper, "default width", 3, Vectors.components(value).length);
        helper.succeed();
    }

    // ---- helpers

    private interface PortCheck {
        void accept(String where, TypeHandle handle);
    }

    /** Every port of {@code cls} whose handle is one of the vector handles. */
    private static void forEachVectorPort(Class<? extends Node> cls, PortCheck check) {
        NodeModel model = addNode(newGraph(), cls);
        for (var entry : model.getInputsById().entrySet()) {
            visit(check, "input " + entry.getKey(), entry.getValue());
        }
        for (PortModel port : model.getOutputsByDisplayOrder()) {
            visit(check, "output " + port.getPortId(), port);
        }
    }

    private static void visit(PortCheck check, String where, PortModel port) {
        TypeHandle handle = port.getDataTypeHandle();
        if (handle == null) return;
        if (KGTypeHandles.VECTOR.equals(handle) || KGTypeHandles.VEC2.equals(handle)
                || KGTypeHandles.VEC3.equals(handle) || KGTypeHandles.VEC4.equals(handle)) {
            check.accept(where, handle);
        }
    }

    private static TypeHandle outputHandle(Class<? extends Node> cls) {
        return addNode(newGraph(), cls).getOutputsById().get("out").getDataTypeHandle();
    }

    private static Object constant(NodeModel node, String portId) {
        var c = node.getInputConstantsById().get(portId);
        return c == null ? null : c.getValue();
    }

    private static void assertVec(GameTestHelper helper, String label, float[] expected, Object actual) {
        float[] got = Vectors.components(actual);
        assertEq(helper, label + " width", expected.length, got.length);
        for (int i = 0; i < expected.length; i++) {
            assertEq(helper, label + " component " + i, expected[i], got[i], EPS);
        }
    }
}
