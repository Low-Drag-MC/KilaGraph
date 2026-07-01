package com.lowdragmc.kilagraph.test.gametest.blueprint;


import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTest;
import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.EntitiesInAabbNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.EntitiesInRadiusNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.EntityDistanceNode;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.joml.Vector2f;

import java.util.List;
import java.util.Map;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * Entity-query nodes against a live {@link ServerLevel}: find entities in a radius / box, and
 * distance between two entities. Level and entities reach the nodes via seeded wire-only variables.
 */
@GameTestHolder(Kilagraph.MODID)
public final class EntityNodeGameTest {
    private static final String IN_RADIUS = "mc_entity_in_radius";
    private static final String IN_AABB = "mc_entity_in_aabb";
    private static final String DISTANCE = "mc_entity_distance_test";

    private EntityNodeGameTest() {}

    private static PortModel source(BlueprintGraph g, String name, TypeHandle type) {
        var v = (VariableDeclarationModelBase) g.graphModel.createVariable(name, type, null, VariableKind.INPUT);
        return g.graphModel.createVariableNode(v, new Vector2f(0, 0), null, null).getOutputPort();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

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
        assertTrue(helper, "radius contains both pigs", out.contains(a) && out.contains(b));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void inAabb(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity a = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        BlockPos min = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = helper.absolutePos(new BlockPos(3, 4, 3));

        var g = newGraph();
        PortModel levelOut = source(g, "level", KGTypeHandles.LEVEL);
        var node = addNode(g, EntitiesInAabbNode.class);
        wire(g, node.getInputsById().get("level"), levelOut);
        setInputConstant(node, "min", min);
        setInputConstant(node, "max", max);

        var exec = new GraphExecutor(g, EvaluationEnvironment.with(Map.of("level", level)));
        List<?> out = exec.evaluate(node.getOutputsById().get("out"), List.class);
        assertTrue(helper, "aabb result non-null", out != null);
        assertTrue(helper, "aabb contains pig", out.contains(a));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void distance(GameTestHelper helper) {
        Entity a = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        Entity b = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 4));
        double expected = a.distanceTo(b);

        var g = newGraph();
        PortModel aOut = source(g, "a", KGTypeHandles.ENTITY);
        PortModel bOut = source(g, "b", KGTypeHandles.ENTITY);
        var node = addNode(g, EntityDistanceNode.class);
        wire(g, node.getInputsById().get("a"), aOut);
        wire(g, node.getInputsById().get("b"), bOut);

        var env = EvaluationEnvironment.with(Map.of("a", a, "b", b));
        Double v = new GraphExecutor(g, env).evaluate(node.getOutputsById().get("out"), Double.class);
        assertEq(helper, "entity distance", (float) expected, v.floatValue(), 1e-3f);
        helper.succeed();
    }
}
