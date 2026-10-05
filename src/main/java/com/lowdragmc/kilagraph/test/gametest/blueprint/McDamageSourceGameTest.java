package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.action.EntityActionNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.gameplay.DamageSourceNodes;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.Map;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * The damage source nodes, against sources the game itself builds.
 *
 * <p>Reading is checked on the game's own sources — an arrow's, a mob's melee, a fall — so the
 * assertions are about what Minecraft puts on a source rather than what these nodes put there. Building
 * and hurting are checked on the world: the victim really lost health, and the source it remembers is
 * the very object the graph built.
 */
public final class McDamageSourceGameTest {

    private static final String READS = "mc_damage_source_reads_what_a_source_says";
    private static final String TAG = "mc_damage_source_tests_a_damage_type_tag";
    private static final String MAKES = "mc_damage_source_makes_a_source";
    private static final String DAMAGES = "mc_damage_source_damages_with_a_source";

    private McDamageSourceGameTest() {
    }

    public static void registerFunctions() {
        KGGameTests.registerFunction(READS, McDamageSourceGameTest::readsWhatASourceSays);
        KGGameTests.registerFunction(TAG, McDamageSourceGameTest::testsADamageTypeTag);
        KGGameTests.registerFunction(MAKES, McDamageSourceGameTest::makesASource);
        KGGameTests.registerFunction(DAMAGES, McDamageSourceGameTest::damagesWithASource);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment);
        for (String p : new String[]{READS, TAG, MAKES, DAMAGES}) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    /** An arrow's source names the shooter and the arrow apart; a melee source names one entity twice. */
    public static void readsWhatASourceSays(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity shooter = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
        Arrow arrow = helper.spawn(EntityTypes.ARROW, new BlockPos(3, 2, 1));

        var shot = probe(level, true, DamageSourceNodes.Info.class,
                "source", level.damageSources().arrow(arrow, shooter));
        assertEq(helper, "an arrow's type", Identifier.withDefaultNamespace("arrow"),
                shot.eval("type", Identifier.class));
        assertEq(helper, "the shooter is responsible", shooter, shot.eval("entity", Object.class));
        assertEq(helper, "the arrow struck", arrow, shot.eval("directEntity", Object.class));
        assertFalse(helper, "so it is not direct", shot.eval("direct", Boolean.class));
        assertTrue(helper, "it has a position", shot.eval("hasPosition", Boolean.class));
        Vector3f at = shot.eval("position", Vector3f.class);
        // compared in floats: GameTests run millions of blocks out, where a float is a whole block wide
        assertEq(helper, "the arrow's position", (float) arrow.getX(), at.x, 0f);
        assertEq(helper, "the arrow's height", (float) arrow.getY(), at.y, 0f);

        var melee = probe(level, true, DamageSourceNodes.Info.class,
                "source", level.damageSources().mobAttack((LivingEntity) shooter));
        assertEq(helper, "a mob's melee type", Identifier.withDefaultNamespace("mob_attack"),
                melee.eval("type", Identifier.class));
        assertEq(helper, "names the mob as responsible", shooter, melee.eval("entity", Object.class));
        assertEq(helper, "and as what struck", shooter, melee.eval("directEntity", Object.class));
        assertTrue(helper, "which is direct", melee.eval("direct", Boolean.class));

        var fall = probe(level, true, DamageSourceNodes.Info.class, "source", level.damageSources().fall());
        assertEq(helper, "a fall's type", Identifier.withDefaultNamespace("fall"),
                fall.eval("type", Identifier.class));
        assertEq(helper, "has nobody behind it", null, fall.eval("entity", Object.class));
        assertFalse(helper, "and no position", fall.eval("hasPosition", Boolean.class));

        var none = probe(level, true, DamageSourceNodes.Info.class);
        assertEq(helper, "no source has no type", null, none.eval("type", Object.class));
        assertFalse(helper, "and no position", none.eval("hasPosition", Boolean.class));
        helper.succeed();
    }

    /** Tag membership, the game's own way of asking what kind of damage something is. */
    public static void testsADamageTypeTag(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity shooter = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
        Arrow arrow = helper.spawn(EntityTypes.ARROW, new BlockPos(3, 2, 1));
        DamageSource shot = level.damageSources().arrow(arrow, shooter);

        assertTrue(helper, "an arrow is a projectile",
                probe(level, true, DamageSourceNodes.Is.class, "source", shot,
                        "tag", Identifier.withDefaultNamespace("is_projectile")).eval("out", Boolean.class));
        assertFalse(helper, "and not fire",
                probe(level, true, DamageSourceNodes.Is.class, "source", shot,
                        "tag", Identifier.withDefaultNamespace("is_fire")).eval("out", Boolean.class));
        assertFalse(helper, "a tag nobody defined is false",
                probe(level, true, DamageSourceNodes.Is.class, "source", shot,
                        "tag", Identifier.fromNamespaceAndPath("kilagraph", "no_such_tag")).eval("out", Boolean.class));
        assertFalse(helper, "and so is no source",
                probe(level, true, DamageSourceNodes.Is.class,
                        "tag", Identifier.withDefaultNamespace("is_projectile")).eval("out", Boolean.class));
        helper.succeed();
    }

    /**
     * Building a source: the type from the registry, the entity on it, the direct entity defaulting to
     * it, a position only when asked, and the world found through the entity when no level is wired.
     */
    public static void makesASource(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity caster = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
        Arrow arrow = helper.spawn(EntityTypes.ARROW, new BlockPos(3, 2, 1));

        var magic = probe(level, true, DamageSourceNodes.Make.class,
                "type", Identifier.withDefaultNamespace("magic"), "entity", caster);
        assertTrue(helper, "magic is a known type", magic.eval("ok", Boolean.class));
        DamageSource made = magic.eval("source", DamageSource.class);
        assertTrue(helper, "and the source is magic", made != null && made.is(DamageTypes.MAGIC));
        assertEq(helper, "from the caster", caster, made.getEntity());
        assertEq(helper, "which also struck, unwired", caster, made.getDirectEntity());
        assertEq(helper, "with no position of its own", null, made.sourcePositionRaw());

        DamageSource thrown = probe(level, true, DamageSourceNodes.Make.class,
                "type", Identifier.withDefaultNamespace("arrow"), "entity", caster, "directEntity", arrow)
                .eval("source", DamageSource.class);
        assertEq(helper, "a wired direct entity is what struck", arrow, thrown.getDirectEntity());
        assertEq(helper, "and the caster stays responsible", caster, thrown.getEntity());

        DamageSource placed = probe(level, true, DamageSourceNodes.Make.class,
                "entity", caster, "position", new Vector3f(1f, 2f, 3f), "usePosition", true)
                .eval("source", DamageSource.class);
        assertTrue(helper, "the type defaults to generic", placed.is(DamageTypes.GENERIC));
        assertEq(helper, "a position is used when asked", new Vec3(1, 2, 3), placed.sourcePositionRaw());

        var unknown = probe(level, true, DamageSourceNodes.Make.class,
                "type", Identifier.fromNamespaceAndPath("kilagraph", "no_such_damage"), "entity", caster);
        assertFalse(helper, "an unknown type is refused", unknown.eval("ok", Boolean.class));
        assertEq(helper, "with no source", null, unknown.eval("source", Object.class));

        var unwired = probe(level, false, DamageSourceNodes.Make.class,
                "type", Identifier.withDefaultNamespace("magic"), "entity", caster);
        assertTrue(helper, "no level wired: the caster's world resolves the type", unwired.eval("ok", Boolean.class));
        assertFalse(helper, "no level and no entity: nothing to resolve it in",
                probe(level, false, DamageSourceNodes.Make.class,
                        "type", Identifier.withDefaultNamespace("magic")).eval("ok", Boolean.class));
        helper.succeed();
    }

    /** Hurting with a source: the health goes down and the victim remembers that exact source. */
    public static void damagesWithASource(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LivingEntity pig = helper.spawn(EntityTypes.PIG, new BlockPos(1, 2, 1));
        LivingEntity zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 2, 1));
        float full = pig.getHealth();
        DamageSource bite = level.damageSources().mobAttack(zombie);

        assertTrue(helper, "damage reported success",
                action(level, EntityActionNodes.DamageEntityWithSource.class,
                        "entity", pig, "source", bite, "amount", 3f).eval("ok", Boolean.class));
        assertEq(helper, "and the pig lost three", full - 3f, pig.getHealth(), 0.01f);
        assertTrue(helper, "to that very source", pig.getLastDamageSource() == bite);

        assertFalse(helper, "no source is refused",
                action(level, EntityActionNodes.DamageEntityWithSource.class,
                        "entity", zombie, "amount", 3f).eval("ok", Boolean.class));
        assertEq(helper, "and hurts nobody", zombie.getMaxHealth(), zombie.getHealth(), 0.01f);
        Entity arrow = helper.spawn(EntityTypes.ARROW, new BlockPos(1, 2, 1));
        assertFalse(helper, "an arrow cannot be damaged",
                action(level, EntityActionNodes.DamageEntityWithSource.class,
                        "entity", arrow, "source", bite, "amount", 3f).eval("ok", Boolean.class));
        helper.succeed();
    }

    // ---- helpers -----------------------------------------------------------------------------

    private record Probe(GraphExecutor exec, NodeModel node) {
        <T> T eval(String output, Class<T> type) {
            return exec.evaluate(node.getOutputsById().get(output), type);
        }
    }

    /** A data node alone in a graph; {@code wireLevel} feeds its level port from a graph variable. */
    private static Probe probe(ServerLevel level, boolean wireLevel, Class<? extends Node> cls, Object... inputs) {
        BlueprintGraph g = newGraph();
        NodeModel n = build(g, cls, wireLevel, inputs);
        return new Probe(new GraphExecutor(g, EvaluationEnvironment.with(Map.of("level", level))), n);
    }

    /** Entry → an exec node, run to completion. */
    private static Probe action(ServerLevel level, Class<? extends Node> cls, Object... inputs) {
        BlueprintGraph g = newGraph();
        NodeModel entry = addNode(g, EntryNode.class);
        NodeModel n = build(g, cls, true, inputs);
        wire(g, n.getInputsById().get("trigger"), entry.getOutputsById().get("next"));
        var exec = new GraphExecutor(g, EvaluationEnvironment.with(Map.of("level", level)));
        exec.executeFrom(entry);
        return new Probe(exec, n);
    }

    private static NodeModel build(BlueprintGraph g, Class<? extends Node> cls, boolean wireLevel, Object... inputs) {
        NodeModel n = addNode(g, cls);
        for (int i = 0; i + 1 < inputs.length; i += 2) {
            setInputConstant(n, (String) inputs[i], inputs[i + 1]);
        }
        PortModel levelPort = n.getInputsById().get("level");
        if (wireLevel && levelPort != null) {
            var v = (VariableDeclarationModelBase)
                    g.graphModel.createVariable("level", KGTypeHandles.LEVEL, null, VariableKind.INPUT);
            wire(g, levelPort,
                    g.graphModel.createVariableNode(v, new Vector2f(0, 0), null, null).getOutputPort());
        }
        return n;
    }
}
