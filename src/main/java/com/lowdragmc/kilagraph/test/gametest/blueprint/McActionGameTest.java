package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.action.BlockActionNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.action.EntityActionNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.action.WorldEffectNodes;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
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
 * The {@code mc_action} nodes, against a real world.
 *
 * <p>These are the first nodes that <em>change</em> anything, so the assertions are on the world after
 * the flow has run — the block is really there, the entity really moved — and not only on the node's
 * {@code ok} output. A node that reported success without doing anything would pass a test that trusted
 * {@code ok}.
 *
 * <p>Each test also checks the failure path, because every action here is written to refuse rather than
 * throw: an unknown sound id, an entity that is not living, a position outside the world. Those branches
 * are the ones a half-built graph will hit, so they are the ones most worth pinning.
 */
@GameTestHolder(Kilagraph.MODID)
public final class McActionGameTest {

    private McActionGameTest() {
    }

    // ---- blocks ------------------------------------------------------------------------------

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void setAndBreakBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(0, 2, 0));
        level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);

        var set = run(level, BlockActionNodes.SetBlock.class,
                "pos", at, "state", Blocks.GOLD_BLOCK.defaultBlockState());
        assertTrue(helper, "set reported success", set.ok());
        assertEq(helper, "and the block is really there", Blocks.GOLD_BLOCK, level.getBlockState(at).getBlock());

        var broke = run(level, BlockActionNodes.BreakBlock.class, "pos", at, "drop", false);
        assertTrue(helper, "break reported success", broke.ok());
        assertTrue(helper, "and the block is gone", level.getBlockState(at).isAir());

        // Breaking air is not an error, but it is not a success either.
        assertFalse(helper, "breaking air reports false",
                run(level, BlockActionNodes.BreakBlock.class, "pos", at, "drop", false).ok());

        // Far outside the build height: refused rather than silently doing nothing.
        assertFalse(helper, "a position outside the world is refused",
                run(level, BlockActionNodes.SetBlock.class,
                        "pos", new BlockPos(at.getX(), 5000, at.getZ()),
                        "state", Blocks.GOLD_BLOCK.defaultBlockState()).ok());
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void fillAndReplaceBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos min = helper.absolutePos(new BlockPos(0, 2, 0));
        BlockPos max = min.offset(2, 0, 2);   // a 3x1x3 slab = 9 blocks

        var fill = run(level, BlockActionNodes.FillBlocks.class,
                "min", min, "max", max, "state", Blocks.STONE.defaultBlockState());
        assertTrue(helper, "fill reported success", fill.ok());
        assertEq(helper, "placed nine blocks", 9, fill.get("placed", Integer.class).intValue());
        assertFalse(helper, "and was not truncated", fill.get("truncated", Boolean.class));
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            assertEq(helper, "filled at " + p, Blocks.STONE, level.getBlockState(p).getBlock());
        }

        // Compare-and-set: succeeds on the expected block, refuses on anything else.
        var hit = run(level, BlockActionNodes.ReplaceBlock.class,
                "pos", min, "expected", Blocks.STONE, "state", Blocks.GOLD_BLOCK.defaultBlockState());
        assertTrue(helper, "replace matched", hit.ok());
        assertEq(helper, "and swapped the block", Blocks.GOLD_BLOCK, level.getBlockState(min).getBlock());

        var miss = run(level, BlockActionNodes.ReplaceBlock.class,
                "pos", min, "expected", Blocks.DIRT, "state", Blocks.DIAMOND_BLOCK.defaultBlockState());
        assertFalse(helper, "replace did not match", miss.ok());
        assertEq(helper, "and left the block alone", Blocks.GOLD_BLOCK, level.getBlockState(min).getBlock());
        helper.succeed();
    }

    // ---- entities ----------------------------------------------------------------------------

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void spawnMoveAndRemoveEntity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));

        var spawn = run(level, EntityActionNodes.SpawnEntity.class, "type", EntityType.PIG, "pos", at);
        assertTrue(helper, "spawn reported success", spawn.ok());
        Entity pig = spawn.get("entity", Entity.class);
        assertTrue(helper, "and handed back the entity", pig != null);
        assertTrue(helper, "which is really in the world", pig.isAlive() && !pig.isRemoved());
        assertEq(helper, "and is a pig", EntityType.PIG, pig.getType());

        // Teleport, then confirm the entity actually moved rather than trusting ok.
        Vector3f to = new Vector3f(at.getX() + 4.5f, at.getY(), at.getZ() + 4.5f);
        assertTrue(helper, "teleport reported success",
                run(level, EntityActionNodes.TeleportEntity.class, "entity", pig, "pos", to).ok());
        assertEq(helper, "and the entity moved", to.x, (float) pig.getX(), 0.01f);

        assertTrue(helper, "velocity reported success",
                run(level, EntityActionNodes.SetVelocity.class,
                        "entity", pig, "velocity", new Vector3f(0, 0.5f, 0)).ok());
        assertTrue(helper, "and the entity is moving up", pig.getDeltaMovement().y > 0.4);

        assertTrue(helper, "remove reported success",
                run(level, EntityActionNodes.RemoveEntity.class, "entity", pig).ok());
        assertTrue(helper, "and the entity is gone", pig.isRemoved());

        // Removing it twice is refused rather than throwing.
        assertFalse(helper, "removing an already-removed entity reports false",
                run(level, EntityActionNodes.RemoveEntity.class, "entity", pig).ok());
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void damageHealAndEffect(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LivingEntity pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        float full = pig.getHealth();

        assertTrue(helper, "damage reported success",
                run(level, EntityActionNodes.DamageEntity.class, "entity", pig, "amount", 3f).ok());
        assertTrue(helper, "and the pig lost health, was " + full + " now " + pig.getHealth(),
                pig.getHealth() < full);

        // Heal past the maximum clamps rather than overshooting.
        assertTrue(helper, "heal reported success",
                run(level, EntityActionNodes.HealEntity.class, "entity", pig, "amount", 100f).ok());
        assertEq(helper, "and health is back at the maximum", pig.getMaxHealth(), pig.getHealth(), 0.01f);

        var effect = run(level, EntityActionNodes.AddEffect.class, "entity", pig,
                "effect", ResourceLocation.parse("minecraft:speed"), "duration", 200, "amplifier", 1);
        assertTrue(helper, "effect reported success", effect.ok());
        var speed = pig.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
        assertTrue(helper, "and the effect is on the entity", speed != null);
        assertEq(helper, "with the amplifier given", 1, speed.getAmplifier());

        // An unknown effect id is refused.
        assertFalse(helper, "an unknown effect id reports false",
                run(level, EntityActionNodes.AddEffect.class, "entity", pig,
                        "effect", ResourceLocation.parse("kilagraph:no_such_effect")).ok());

        // Damage on a non-living entity is refused, not an error.
        Entity arrow = helper.spawn(EntityType.ARROW, new BlockPos(1, 2, 1));
        assertFalse(helper, "an arrow cannot be damaged",
                run(level, EntityActionNodes.DamageEntity.class, "entity", arrow, "amount", 5f).ok());
        helper.succeed();
    }

    // ---- world effects -----------------------------------------------------------------------

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void soundAndParticles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));

        assertTrue(helper, "a known sound plays",
                run(level, WorldEffectNodes.PlaySound.class, "pos", at,
                        "sound", ResourceLocation.parse("minecraft:block.anvil.land")).ok());
        assertFalse(helper, "an unknown sound id is refused",
                run(level, WorldEffectNodes.PlaySound.class, "pos", at,
                        "sound", ResourceLocation.parse("kilagraph:no_such_sound")).ok());

        assertTrue(helper, "a simple particle spawns",
                run(level, WorldEffectNodes.SpawnParticle.class, "pos", at,
                        "particle", ResourceLocation.parse("minecraft:flame"), "count", 4).ok());
        // A parameterised particle type has no representation in the graph, so it is refused rather
        // than guessed at — this is the documented limitation, asserted.
        assertFalse(helper, "a parameterised particle is refused",
                run(level, WorldEffectNodes.SpawnParticle.class, "pos", at,
                        "particle", ResourceLocation.parse("minecraft:dust"), "count", 1).ok());
        assertFalse(helper, "an unknown particle id is refused",
                run(level, WorldEffectNodes.SpawnParticle.class, "pos", at,
                        "particle", ResourceLocation.parse("kilagraph:no_such_particle")).ok());
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void dropItem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));

        var drop = run(level, WorldEffectNodes.DropItem.class,
                "pos", at, "stack", new ItemStack(Items.DIAMOND, 3));
        assertTrue(helper, "drop reported success", drop.ok());
        Entity item = drop.get("entity", Entity.class);
        assertTrue(helper, "and handed back the item entity", item instanceof net.minecraft.world.entity.item.ItemEntity);
        var stack = ((net.minecraft.world.entity.item.ItemEntity) item).getItem();
        assertEq(helper, "holding the right item", Items.DIAMOND, stack.getItem());
        assertEq(helper, "and the right count", 3, stack.getCount());

        assertFalse(helper, "dropping nothing is refused",
                run(level, WorldEffectNodes.DropItem.class, "pos", at, "stack", ItemStack.EMPTY).ok());
        helper.succeed();
    }

    // ---- helpers -----------------------------------------------------------------------------

    /** A finished action: its {@code ok} plus any other output. */
    private record Result(GraphExecutor exec, NodeModel node) {
        boolean ok() {
            return get("ok", Boolean.class);
        }

        <T> T get(String output, Class<T> type) {
            return exec.evaluate(node.getOutputsById().get(output), type);
        }
    }

    /**
     * Builds Entry → action, runs the flow, and returns the action's outputs.
     *
     * <p>The level arrives through a graph variable seeded on the environment, which is the only supported
     * way — the executor never knows about the world, so a node that needs one says so on a port.</p>
     */
    private static Result run(ServerLevel level, Class<? extends Node> action, Object... inputs) {
        BlueprintGraph g = newGraph();
        NodeModel entry = addNode(g, EntryNode.class);
        NodeModel node = addNode(g, action);
        for (int i = 0; i + 1 < inputs.length; i += 2) {
            setInputConstant(node, (String) inputs[i], inputs[i + 1]);
        }
        PortModel levelPort = node.getInputsById().get("level");
        if (levelPort != null) {
            var v = (VariableDeclarationModelBase)
                    g.graphModel.createVariable("level", KGTypeHandles.LEVEL, null, VariableKind.INPUT);
            wire(g, levelPort,
                    g.graphModel.createVariableNode(v, new Vector2f(0, 0), null, null).getOutputPort());
        }
        wire(g, node.getInputsById().get("trigger"), entry.getOutputsById().get("next"));

        var exec = new GraphExecutor(g, EvaluationEnvironment.with(Map.of("level", level)));
        exec.executeFrom(entry);
        return new Result(exec, node);
    }
}
