package com.lowdragmc.kilagraph.test.gametest.blueprint;


import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.SmoothstepNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.EntitiesInRadiusNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.NearestEntityNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.gameplay.DamageSourceNodes;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.joml.Vector2f;

import java.util.List;
import java.util.Map;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * Entity-query nodes against a live {@link ServerLevel}: find entities in a radius / box, and
 * distance between two entities. Level and entities reach the nodes via seeded wire-only variables.
 */
public final class EntityNodeGameTest {
    private static final String IN_RADIUS = "mc_entity_in_radius";
    private static final String IN_AABB = "mc_entity_in_aabb";
    private static final String DISTANCE = "mc_entity_distance_test";
    private static final String PIN_LABELS = "mc_entity_pin_labels";

    private EntityNodeGameTest() {}


    public static void registerFunctions() {
        KGGameTests.registerFunction(IN_RADIUS, EntityNodeGameTest::inRadius);
        KGGameTests.registerFunction(PIN_LABELS, EntityNodeGameTest::anUndisplayedPortIsLabelledByItsPinKey);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment);
        for (String p : new String[]{
                IN_RADIUS, PIN_LABELS
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    /**
     * Enough to tell the two ways this can fail apart: a wrong query, or an entity the level cannot see.
     *
     * <p>{@code visible=false} means the entity exists but was never published to the level's section
     * index — it is in a chunk that is loaded but not entity-ticking, which is what
     * {@link KGGameTests#DEFAULT_STRUCTURE} exists to prevent. That is not a bug in the node.</p>
     */
    private static String diag(ServerLevel level, Entity e) {
        return e.position() + (e.isRemoved() ? " removed" : "")
                + " visible=" + (level.getEntity(e.getId()) != null)
                + " ticking=" + level.isPositionEntityTicking(e.blockPosition());
    }

    private static PortModel source(BlueprintGraph g, String name, TypeHandle type) {
        var v = (VariableDeclarationModelBase) g.graphModel.createVariable(name, type, null, VariableKind.INPUT);
        return g.graphModel.createVariableNode(v, new Vector2f(0, 0), null, null).getOutputPort();
    }



    public static void inRadius(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity a = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        Entity b = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 3));
        BlockPos center = helper.absolutePos(new BlockPos(1, 2, 2));

        var g = newGraph();
        PortModel levelOut = source(g, "level", KGTypeHandles.LEVEL);
        var node = addNode(g, EntitiesInRadiusNode.class);
        wire(g, node.getInputsById().get("level"), levelOut);
        setInputConstant(node, "center", center);
        setInputConstant(node, "radius", 5.0);

        var exec = new GraphExecutor(g, EvaluationEnvironment.with(Map.of("level", level)));
        List<?> out = exec.evaluate(node.getOutputsById().get("out"), List.class);
        assertTrue(helper, "radius result non-null", out != null);
        // Spelled out rather than assertTrue: this one has been seen to fail intermittently under a full
        // suite run, and "expected true" says nothing about whether the query came back short, came back
        // with the wrong centre, or the pigs were gone. Built only on the failing path.
        if (!out.contains(a) || !out.contains(b)) {
            helper.fail("radius contains both pigs: centre=" + center.getCenter()
                    + " | a=" + diag(level, a) + " | b=" + diag(level, b) + " | found=" + out);
        }
        helper.succeed();
    }

    /** An annotated port with no {@code display} is labelled by {@code kg.pin.<id>}, the id in words as fallback. */
    public static void anUndisplayedPortIsLabelledByItsPinKey(GameTestHelper helper) {
        var g = newGraph();
        var inRadius = addNode(g, EntitiesInRadiusNode.class);
        var nearest = addNode(g, NearestEntityNode.class);
        var info = addNode(g, DamageSourceNodes.Info.class);
        var smoothstep = addNode(g, SmoothstepNode.class);
        var setVar = addNode(g, SetVarNode.class);

        if (!pinLabel(helper, inRadius.getInputsById().get("level"), "Level")) return;
        // words: camel case splits, a digit starts a word, exec pins too
        if (!pinLabel(helper, nearest.getInputsById().get("livingOnly"), "Living Only")) return;
        if (!pinLabel(helper, info.getOutputsById().get("directEntity"), "Direct Entity")) return;
        if (!pinLabel(helper, smoothstep.getInputsById().get("edge0"), "Edge 0")) return;
        if (!pinLabel(helper, setVar.getInputsById().get("trigger"), "Trigger")) return;
        helper.succeed();
    }

    private static boolean pinLabel(GameTestHelper helper, PortModel port, String fallback) {
        var label = port.getDisplayName();
        boolean ok = label.getContents() instanceof TranslatableContents contents
                && ("kg.pin." + port.getPortId()).equals(contents.getKey()) && fallback.equals(contents.getFallback());
        if (!ok) helper.fail(port.getPortId() + ": expected kg.pin." + port.getPortId() + " / " + fallback + ", got " + label);
        return ok;
    }
}
