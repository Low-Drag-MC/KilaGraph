package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.action.EntityActionNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.gameplay.PotionNodes;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * Potion stacks and status-effect removal.
 *
 * <p>The potion nodes are pure data, so they are checked against the vanilla brewing table's own numbers —
 * strong swiftness really is 1800 ticks at amplifier 1, and a node that returned a constant or read the
 * wrong field would disagree. The removal nodes change a live entity, so those assertions are on the
 * entity afterwards, never on {@code ok} alone.
 */
@GameTestHolder(Kilagraph.MODID)
public final class McPotionGameTest {

    private static final ResourceLocation SWIFTNESS = ResourceLocation.parse("minecraft:swiftness");
    private static final ResourceLocation STRONG_SWIFTNESS = ResourceLocation.parse("minecraft:strong_swiftness");
    private static final ResourceLocation SPEED = ResourceLocation.parse("minecraft:speed");
    private static final ResourceLocation STRENGTH = ResourceLocation.parse("minecraft:strength");
    private static final ResourceLocation NOT_A_THING = ResourceLocation.parse("kilagraph:nope");

    private McPotionGameTest() {
    }

    // ---- potions -------------------------------------------------------------------------------

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void potionsAreBuiltAndRead(GameTestHelper helper) {
        var made = node(PotionNodes.Make.class, "item", Items.POTION, "potion", SWIFTNESS);
        assertTrue(helper, "making a swiftness potion worked", eval(made, "ok", Boolean.class));
        ItemStack potion = eval(made, "out", ItemStack.class);
        assertEq(helper, "it is a drinkable bottle", Items.POTION, potion.getItem());
        assertEq(helper, "of one", 1, potion.getCount());

        var type = node(PotionNodes.Type.class, "stack", potion);
        assertTrue(helper, "the base potion is named", eval(type, "found", Boolean.class));
        assertEq(helper, "and it is swiftness", SWIFTNESS, eval(type, "out", ResourceLocation.class));

        var effects = node(PotionNodes.Effects.class, "stack", potion);
        assertEq(helper, "one effect", 1, eval(effects, "count", Integer.class).intValue());
        assertEq(helper, "which is speed", List.of(SPEED), eval(effects, "ids", List.class));
        assertEq(helper, "for three minutes", List.of(3600), eval(effects, "durations", List.class));
        assertEq(helper, "at the base level", List.of(0), eval(effects, "amplifiers", List.class));

        // Strong swiftness is the case that makes the amplifier port load-bearing: 1, not 0.
        var strong = node(PotionNodes.Make.class, "item", Items.SPLASH_POTION, "potion", STRONG_SWIFTNESS);
        ItemStack splash = eval(strong, "out", ItemStack.class);
        assertEq(helper, "the item chooses the form", Items.SPLASH_POTION, splash.getItem());
        var strongEffects = node(PotionNodes.Effects.class, "stack", splash);
        assertEq(helper, "strong swiftness is amplifier 1", List.of(1),
                eval(strongEffects, "amplifiers", List.class));
        assertEq(helper, "and shorter", List.of(1800), eval(strongEffects, "durations", List.class));

        // An unknown potion gives an empty stack, not a plain bottle of water.
        var unknown = node(PotionNodes.Make.class, "item", Items.POTION, "potion", NOT_A_THING);
        assertFalse(helper, "an unknown potion id is refused", eval(unknown, "ok", Boolean.class));
        assertTrue(helper, "and yields nothing", eval(unknown, "out", ItemStack.class).isEmpty());

        // A stack with no potion component reads as empty rather than throwing.
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        assertFalse(helper, "a diamond has no potion type",
                eval(node(PotionNodes.Type.class, "stack", diamond), "found", Boolean.class));
        assertEq(helper, "and no effects", 0,
                eval(node(PotionNodes.Effects.class, "stack", diamond), "count", Integer.class).intValue());
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void customEffectsStackOnTheBase(GameTestHelper helper) {
        ItemStack potion = eval(node(PotionNodes.Make.class, "item", Items.POTION, "potion", SWIFTNESS),
                "out", ItemStack.class);

        var added = node(PotionNodes.AddCustomEffect.class,
                "stack", potion, "effect", STRENGTH, "duration", 100, "amplifier", 2);
        assertTrue(helper, "adding a custom effect worked", eval(added, "ok", Boolean.class));
        ItemStack mixed = eval(added, "out", ItemStack.class);

        var effects = node(PotionNodes.Effects.class, "stack", mixed);
        assertEq(helper, "both effects are there", 2, eval(effects, "count", Integer.class).intValue());
        assertEq(helper, "base first, then the custom one", List.of(SPEED, STRENGTH),
                eval(effects, "ids", List.class));
        assertEq(helper, "with their own durations", List.of(3600, 100),
                eval(effects, "durations", List.class));
        assertEq(helper, "and their own amplifiers", List.of(0, 2),
                eval(effects, "amplifiers", List.class));

        // The base potion survives — a custom effect adds to it rather than replacing it.
        assertEq(helper, "still swiftness underneath", SWIFTNESS,
                eval(node(PotionNodes.Type.class, "stack", mixed), "out", ResourceLocation.class));

        // Value semantics: the stack that went in is untouched.
        assertEq(helper, "the input stack still has one effect", 1,
                eval(node(PotionNodes.Effects.class, "stack", potion), "count", Integer.class).intValue());

        // A stack that had no potion component gets one holding only this effect.
        var bare = node(PotionNodes.AddCustomEffect.class,
                "stack", new ItemStack(Items.DIAMOND), "effect", STRENGTH, "duration", 100, "amplifier", 0);
        assertTrue(helper, "any item can carry the component", eval(bare, "ok", Boolean.class));
        assertEq(helper, "with just the custom effect on it", 1,
                eval(node(PotionNodes.Effects.class, "stack", eval(bare, "out", ItemStack.class)),
                        "count", Integer.class).intValue());

        // -1 is the game's "infinite", so it is accepted where 0 is not.
        assertTrue(helper, "an infinite duration is allowed",
                eval(node(PotionNodes.AddCustomEffect.class, "stack", potion,
                        "effect", STRENGTH, "duration", -1), "ok", Boolean.class));
        assertFalse(helper, "but a zero duration is a mistake",
                eval(node(PotionNodes.AddCustomEffect.class, "stack", potion,
                        "effect", STRENGTH, "duration", 0), "ok", Boolean.class));
        assertFalse(helper, "and an unknown effect is refused",
                eval(node(PotionNodes.AddCustomEffect.class, "stack", potion,
                        "effect", NOT_A_THING, "duration", 100), "ok", Boolean.class));
        assertFalse(helper, "as is an empty stack",
                eval(node(PotionNodes.AddCustomEffect.class, "stack", ItemStack.EMPTY,
                        "effect", STRENGTH, "duration", 100), "ok", Boolean.class));
        helper.succeed();
    }

    // ---- effect removal ------------------------------------------------------------------------

    /**
     * {@code mc_remove_effect} and {@code mc_clear_effects}, asserted on the pig rather than on {@code ok}.
     *
     * <p>Both nodes report what they changed, not what they attempted, so removing an effect that is not
     * there is {@code false} — that is the branch a graph hits when it clears a buff it never applied.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void effectsAreRemoved(GameTestHelper helper) {
        LivingEntity pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));

        assertTrue(helper, "gave the pig speed",
                run(EntityActionNodes.AddEffect.class,
                        "entity", pig, "effect", SPEED, "duration", 200).ok());
        assertTrue(helper, "and it really has it", pig.hasEffect(MobEffects.MOVEMENT_SPEED));

        var removed = run(EntityActionNodes.RemoveEffect.class, "entity", pig, "effect", SPEED);
        assertTrue(helper, "removal reported success", removed.ok());
        assertFalse(helper, "and the effect is gone", pig.hasEffect(MobEffects.MOVEMENT_SPEED));

        assertFalse(helper, "removing it again changes nothing",
                run(EntityActionNodes.RemoveEffect.class, "entity", pig, "effect", SPEED).ok());
        assertFalse(helper, "an unknown effect id is refused",
                run(EntityActionNodes.RemoveEffect.class, "entity", pig, "effect", NOT_A_THING).ok());

        // Two effects, then clear them both at once.
        run(EntityActionNodes.AddEffect.class, "entity", pig, "effect", SPEED, "duration", 200);
        run(EntityActionNodes.AddEffect.class, "entity", pig, "effect", STRENGTH, "duration", 200);
        assertEq(helper, "the pig has two effects", 2, pig.getActiveEffects().size());

        var cleared = run(EntityActionNodes.ClearEffects.class, "entity", pig);
        assertTrue(helper, "clearing reported success", cleared.ok());
        assertEq(helper, "and counted both", 2, cleared.get("removed", Integer.class).intValue());
        assertTrue(helper, "leaving none behind", pig.getActiveEffects().isEmpty());

        var again = run(EntityActionNodes.ClearEffects.class, "entity", pig);
        assertFalse(helper, "clearing nothing is not a success", again.ok());
        assertEq(helper, "and removed nothing", 0, again.get("removed", Integer.class).intValue());

        // Not a living entity: refused rather than thrown, like every other living-only action.
        Entity cart = helper.spawn(EntityType.MINECART, new BlockPos(3, 2, 1));
        assertFalse(helper, "a minecart cannot lose effects",
                run(EntityActionNodes.ClearEffects.class, "entity", cart).ok());
        assertFalse(helper, "nor have one removed",
                run(EntityActionNodes.RemoveEffect.class, "entity", cart, "effect", SPEED).ok());
        assertFalse(helper, "and no entity at all is refused",
                run(EntityActionNodes.ClearEffects.class).ok());
        helper.succeed();
    }

    // ---- helpers -------------------------------------------------------------------------------

    /** One node in its own graph, carried with the graph so it can be evaluated. */
    private record Probe(BlueprintGraph graph, NodeModel model) {
    }

    /** A node in its own graph with the given input constants applied, as {@code id, value} pairs. */
    private static Probe node(Class<? extends Node> cls, Object... inputs) {
        var g = newGraph();
        NodeModel n = addNode(g, cls);
        for (int i = 0; i + 1 < inputs.length; i += 2) {
            setInputConstant(n, (String) inputs[i], inputs[i + 1]);
        }
        return new Probe(g, n);
    }

    private static <T> T eval(Probe probe, String output, Class<T> type) {
        return new GraphExecutor(probe.graph())
                .evaluate(probe.model().getOutputsById().get(output), type);
    }

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
     * <p>None of the effect actions take a world port — they reach it through the entity — so unlike
     * {@code McActionGameTest} this needs no environment seeding.</p>
     */
    private static Result run(Class<? extends Node> action, Object... inputs) {
        BlueprintGraph g = newGraph();
        NodeModel entry = addNode(g, EntryNode.class);
        NodeModel node = addNode(g, action);
        for (int i = 0; i + 1 < inputs.length; i += 2) {
            setInputConstant(node, (String) inputs[i], inputs[i + 1]);
        }
        wire(g, node.getInputsById().get("trigger"), entry.getOutputsById().get("next"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        return new Result(exec, node);
    }
}
