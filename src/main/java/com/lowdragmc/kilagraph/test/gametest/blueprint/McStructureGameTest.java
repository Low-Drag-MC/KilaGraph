package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.convert.InstanceOfNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.block.BlockStateNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.component.DataComponentNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.fluid.FluidNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.gameplay.RegistryProbeNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.item.ItemStackNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.text.TextNodes;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * Block states, item stacks, fluids, text and the registry probes — everything that needs no world.
 */
@GameTestHolder(Kilagraph.MODID)
public final class McStructureGameTest {

    private McStructureGameTest() {
    }

    // ---- block states ------------------------------------------------------------------------

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void blockStateBasics(GameTestHelper helper) {
        var def = node(BlockStateNodes.DefaultState.class, "block", Blocks.OAK_STAIRS);
        BlockState state = eval(def, "out", BlockState.class);
        assertEq(helper, "default state's block", Blocks.OAK_STAIRS, state.getBlock());

        var is = node(BlockStateNodes.IsBlock.class, "state", state, "block", Blocks.OAK_STAIRS);
        assertTrue(helper, "state is its own block", eval(is, "out", Boolean.class));
        var isNot = node(BlockStateNodes.IsBlock.class, "state", state, "block", Blocks.STONE);
        assertFalse(helper, "state is not another block", eval(isNot, "out", Boolean.class));

        // Stairs have facing/half/shape/waterlogged — a block with several so a single-property
        // implementation could not pass
        var props = node(BlockStateNodes.Properties.class, "state", state);
        List<?> names = eval(props, "out", List.class);
        assertTrue(helper, "stairs expose a facing property, got " + names, names.contains("facing"));
        assertTrue(helper, "stairs expose waterlogged", names.contains("waterlogged"));

        // Waterlogged stairs report water, which is the case that distinguishes "the block IS a fluid"
        // from "the state CONTAINS one".
        var dry = node(BlockStateNodes.StateFluid.class, "state", state);
        assertEq(helper, "dry stairs hold no fluid", Fluids.EMPTY, eval(dry, "out", Object.class));
        helper.succeed();
    }

    /**
     * Property read/write by name, including both failure modes.
     *
     * <p>A round trip is not enough on its own: a node that ignored the value and returned the input
     * would pass "set then get" if the value happened to already be that. So the write is asserted to
     * have actually changed the state, and both an unknown property name and an illegal value are
     * asserted to report {@code ok = false} while leaving the state intact.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void blockStatePropertiesReadAndWrite(GameTestHelper helper) {
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState();

        var read = node(BlockStateNodes.GetProperty.class, "state", stairs, "name", "waterlogged");
        assertTrue(helper, "waterlogged found", eval(read, "found", Boolean.class));
        assertEq(helper, "stairs start dry", "false", eval(read, "value", String.class));

        var write = node(BlockStateNodes.SetProperty.class, "state", stairs,
                "name", "waterlogged", "value", "true");
        assertTrue(helper, "write ok", eval(write, "ok", Boolean.class));
        BlockState wet = eval(write, "out", BlockState.class);
        assertTrue(helper, "the state really changed", !wet.equals(stairs));

        var readBack = node(BlockStateNodes.GetProperty.class, "state", wet, "name", "waterlogged");
        assertEq(helper, "and reads back", "true", eval(readBack, "value", String.class));

        // now a waterlogged state reports water
        var wetFluid = node(BlockStateNodes.StateFluid.class, "state", wet);
        assertEq(helper, "waterlogged stairs hold water", Fluids.WATER, eval(wetFluid, "out", Object.class));

        var noSuchProperty = node(BlockStateNodes.SetProperty.class, "state", stairs,
                "name", "not_a_property", "value", "true");
        assertFalse(helper, "unknown property is not ok", eval(noSuchProperty, "ok", Boolean.class));
        assertEq(helper, "and leaves the state alone", stairs,
                eval(noSuchProperty, "out", BlockState.class));

        var illegalValue = node(BlockStateNodes.SetProperty.class, "state", stairs,
                "name", "waterlogged", "value", "banana");
        assertFalse(helper, "illegal value is not ok", eval(illegalValue, "ok", Boolean.class));
        assertEq(helper, "and leaves the state alone", stairs,
                eval(illegalValue, "out", BlockState.class));
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void blockStateRotateAndMirror(GameTestHelper helper) {
        // A north-facing stair rotated 90 clockwise faces east.
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState();
        var facing = node(BlockStateNodes.GetProperty.class, "state", stairs, "name", "facing");
        String before = eval(facing, "value", String.class);

        var rotated = node(BlockStateNodes.Rotate.class, "state", stairs, "rotation", Rotation.CLOCKWISE_90);
        var afterFacing = node(BlockStateNodes.GetProperty.class,
                "state", eval(rotated, "out", BlockState.class), "name", "facing");
        assertTrue(helper, "rotation changed the facing from " + before,
                !before.equals(eval(afterFacing, "value", String.class)));

        // Which axis each Mirror flips is easy to get backwards: LEFT_RIGHT flips north/south and
        // FRONT_BACK flips east/west. Assert both, using a facing that actually moves in each case —
        // a no-op would otherwise pass whichever way round the implementation had it.
        var northMirrored = node(BlockStateNodes.MirrorState.class, "state", stairs,
                "mirror", Mirror.LEFT_RIGHT);
        var northFacing = node(BlockStateNodes.GetProperty.class,
                "state", eval(northMirrored, "out", BlockState.class), "name", "facing");
        assertEq(helper, "north mirrored left-right is south", "south",
                eval(northFacing, "value", String.class));

        BlockState east = eval(node(BlockStateNodes.SetProperty.class, "state", stairs,
                "name", "facing", "value", "east"), "out", BlockState.class);
        var eastMirrored = node(BlockStateNodes.MirrorState.class, "state", east,
                "mirror", Mirror.FRONT_BACK);
        var eastFacing = node(BlockStateNodes.GetProperty.class,
                "state", eval(eastMirrored, "out", BlockState.class), "name", "facing");
        assertEq(helper, "east mirrored front-back is west", "west",
                eval(eastFacing, "value", String.class));
        helper.succeed();
    }

    // ---- item stacks -------------------------------------------------------------------------

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void itemStackCountAndEquality(GameTestHelper helper) {
        var counted = node(ItemStackNodes.WithCount.class, "stack", new ItemStack(Items.DIAMOND, 1),
                "count", 5);
        assertEq(helper, "with count", 5, eval(counted, "out", ItemStack.class).getCount());

        // a negative count clamps to zero rather than producing an invalid stack
        var negative = node(ItemStackNodes.WithCount.class, "stack", new ItemStack(Items.DIAMOND, 1),
                "count", -3);
        assertTrue(helper, "a negative count gives an empty stack",
                eval(negative, "out", ItemStack.class).isEmpty());

        var same = node(ItemStackNodes.SameItem.class, "a", new ItemStack(Items.DIAMOND, 1),
                "b", new ItemStack(Items.DIAMOND, 64));
        assertTrue(helper, "count does not affect item identity", eval(same, "out", Boolean.class));

        var different = node(ItemStackNodes.SameItem.class, "a", new ItemStack(Items.DIAMOND),
                "b", new ItemStack(Items.EMERALD));
        assertFalse(helper, "different items", eval(different, "out", Boolean.class));

        // Components DO affect the stronger test: a renamed diamond is the same item but not
        // interchangeable, which is the distinction between the two nodes.
        ItemStack named = new ItemStack(Items.DIAMOND);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Shiny"));
        var sameItem = node(ItemStackNodes.SameItem.class, "a", new ItemStack(Items.DIAMOND), "b", named);
        assertTrue(helper, "renaming keeps the item", eval(sameItem, "out", Boolean.class));
        var sameComponents = node(ItemStackNodes.SameComponents.class,
                "a", new ItemStack(Items.DIAMOND), "b", named);
        assertFalse(helper, "but not the components", eval(sameComponents, "out", Boolean.class));
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void itemStackComponents(GameTestHelper helper) {
        ItemStack plain = new ItemStack(Items.DIAMOND);

        // custom data round trip
        CompoundTag tag = new CompoundTag();
        tag.putInt("charge", 7);
        var set = node(ItemStackNodes.SetCustomData.class, "stack", plain, "tag", tag);
        ItemStack withData = eval(set, "out", ItemStack.class);
        var get = node(ItemStackNodes.GetCustomData.class, "stack", withData);
        assertEq(helper, "custom data round trips", 7, eval(get, "out", CompoundTag.class).getInt("charge"));

        // the input must not have been mutated — every node here copies
        assertTrue(helper, "setting custom data did not touch the input",
                plain.get(DataComponents.CUSTOM_DATA) == null);

        var empty = node(ItemStackNodes.GetCustomData.class, "stack", plain);
        assertTrue(helper, "no custom data reads as an empty compound",
                eval(empty, "out", CompoundTag.class).isEmpty());

        // custom name
        var noName = node(ItemStackNodes.GetCustomName.class, "stack", plain);
        assertFalse(helper, "a plain stack has no custom name", eval(noName, "has", Boolean.class));

        var rename = node(ItemStackNodes.SetCustomName.class, "stack", plain,
                "name", Component.literal("Shiny"));
        ItemStack renamed = eval(rename, "out", ItemStack.class);
        var hasName = node(ItemStackNodes.GetCustomName.class, "stack", renamed);
        assertTrue(helper, "renamed stack has a custom name", eval(hasName, "has", Boolean.class));
        assertEq(helper, "and it is the one we set", "Shiny",
                eval(hasName, "out", Component.class).getString());

        var display = node(ItemStackNodes.DisplayName.class, "stack", renamed);
        assertEq(helper, "display name uses the custom name", "Shiny",
                eval(display, "out", Component.class).getString());

        var lore = node(ItemStackNodes.Lore.class, "stack", plain);
        assertTrue(helper, "a plain stack has no lore", eval(lore, "out", List.class).isEmpty());
        helper.succeed();
    }

    // ---- fluids ------------------------------------------------------------------------------

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void fluidStacks(GameTestHelper helper) {
        var create = node(FluidNodes.Create.class, "fluid", Fluids.WATER, "amount", 500);
        FluidStack stack = eval(create, "out", FluidStack.class);
        assertEq(helper, "amount", 500, stack.getAmount());
        assertEq(helper, "fluid", Fluids.WATER, stack.getFluid());

        // an empty fluid or a zero amount both mean nothing
        var zero = node(FluidNodes.Create.class, "fluid", Fluids.WATER, "amount", 0);
        assertTrue(helper, "zero amount is empty", eval(zero, "out", FluidStack.class).isEmpty());
        var none = node(FluidNodes.Create.class, "fluid", Fluids.EMPTY, "amount", 500);
        assertTrue(helper, "empty fluid is empty", eval(none, "out", FluidStack.class).isEmpty());

        var resized = node(FluidNodes.WithAmount.class, "stack", new FluidStack(Fluids.LAVA, 100),
                "amount", 250);
        assertEq(helper, "with amount", 250, eval(resized, "out", FluidStack.class).getAmount());
        assertEq(helper, "with amount keeps the fluid", Fluids.LAVA,
                eval(resized, "out", FluidStack.class).getFluid());

        var same = node(FluidNodes.SameFluid.class, "a", new FluidStack(Fluids.WATER, 1),
                "b", new FluidStack(Fluids.WATER, 1000));
        assertTrue(helper, "amount does not affect fluid identity", eval(same, "out", Boolean.class));

        var toBlock = node(FluidNodes.ToBlock.class, "in", Fluids.WATER);
        assertEq(helper, "water's block", Blocks.WATER, eval(toBlock, "out", Object.class));
        var fromBlock = node(FluidNodes.FromBlock.class, "in", Blocks.WATER);
        assertEq(helper, "the water block's fluid", Fluids.WATER, eval(fromBlock, "out", Object.class));
        var airFluid = node(FluidNodes.FromBlock.class, "in", Blocks.STONE);
        assertEq(helper, "stone holds no fluid", Fluids.EMPTY, eval(airFluid, "out", Object.class));
        helper.succeed();
    }

    // ---- text --------------------------------------------------------------------------------

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void textNodes(GameTestHelper helper) {
        var literal = node(TextNodes.Literal.class, "text", "hello");
        assertEq(helper, "literal", "hello", eval(literal, "out", Component.class).getString());

        var append = node(TextNodes.Append.class, "a", Component.literal("foo"),
                "b", Component.literal("bar"));
        assertEq(helper, "append", "foobar", eval(append, "out", Component.class).getString());

        // toString() on a Component is a debug dump; getString() is the text. That difference is the
        // entire reason this node exists, so assert it rather than just the happy path.
        Component c = Component.literal("plain");
        var toString = node(TextNodes.ToString.class, "in", c);
        assertEq(helper, "to string flattens", "plain", eval(toString, "out", String.class));
        assertTrue(helper, "and is not Component.toString(), which is " + c,
                !c.toString().equals("plain"));

        var styled = node(TextNodes.Styled.class, "in", Component.literal("x"), "color", 0xFF0000);
        setInputConstant(styled.model(), "bold", true);
        Component s = eval(styled, "out", Component.class);
        assertTrue(helper, "styled is bold", s.getStyle().isBold());
        assertEq(helper, "styled keeps its text", "x", s.getString());

        var colored = node(TextNodes.Colored.class, "in", Component.literal("y"));
        setOption(colored.model(), "color", ChatFormatting.RED);
        assertEq(helper, "colored keeps its text", "y",
                eval(colored, "out", Component.class).getString());

        var translatable = node(TextNodes.Translatable.class, "key", "kg.node.math_abs.tooltip");
        assertTrue(helper, "a translatable resolves to something non-empty",
                !eval(translatable, "out", Component.class).getString().isEmpty());
        helper.succeed();
    }

    // ---- data components ---------------------------------------------------------------------

    /**
     * The generic component family, and the property that makes it usable: a get/set round trip.
     *
     * <p>Both shapes are exercised, because they take different paths through the wrap/unwrap pair:
     * {@code damage} encodes to a bare int and has to be wrapped, {@code custom_name} to a string, and
     * a structural component to a compound that passes straight through. A round trip that only ever
     * saw compounds would not notice the wrapper being wrong.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void dataComponentsRoundTrip(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        ResourceLocation damage = id("minecraft:damage");

        // damage is an Integer component — the non-compound case
        var setDamage = node(DataComponentNodes.SetComponent.class, "stack", sword,
                "component", damage, "nbt", wrapped(7));
        assertTrue(helper, "setting damage is ok", eval(setDamage, "ok", Boolean.class));
        ItemStack damaged = eval(setDamage, "out", ItemStack.class);
        assertEq(helper, "and the game agrees it is damaged", 7,
                damaged.getOrDefault(DataComponents.DAMAGE, 0).intValue());

        var getDamage = node(DataComponentNodes.GetComponent.class, "stack", damaged,
                "component", damage);
        assertTrue(helper, "reading damage finds it", eval(getDamage, "found", Boolean.class));
        assertEq(helper, "and round trips through the value wrapper", 7,
                eval(getDamage, "nbt", CompoundTag.class).getInt(DataComponentNodes.VALUE_KEY));
        assertEq(helper, "its text form is the raw value", "7",
                eval(getDamage, "text", String.class));

        // feeding Get's own output straight back into Set is the property that matters most
        var again = node(DataComponentNodes.SetComponent.class, "stack", sword,
                "component", damage, "nbt", eval(getDamage, "nbt", CompoundTag.class));
        assertTrue(helper, "Get's output is accepted by Set", eval(again, "ok", Boolean.class));
        assertEq(helper, "and yields the same value", 7,
                eval(again, "out", ItemStack.class).getOrDefault(DataComponents.DAMAGE, 0).intValue());

        // presence, listing and removal
        var has = node(DataComponentNodes.HasComponent.class, "stack", damaged, "component", damage);
        assertTrue(helper, "damaged stack has the component", eval(has, "out", Boolean.class));

        var list = node(DataComponentNodes.ItemComponents.class, "stack", damaged);
        assertTrue(helper, "and it is listed, got " + eval(list, "out", List.class),
                eval(list, "out", List.class).contains(damage));

        var removed = node(DataComponentNodes.RemoveComponent.class, "stack", damaged,
                "component", damage);
        var hasNot = node(DataComponentNodes.HasComponent.class,
                "stack", eval(removed, "out", ItemStack.class), "component", damage);
        assertFalse(helper, "after removal it is gone", eval(hasNot, "out", Boolean.class));

        // an unknown id fails cleanly rather than throwing, and leaves the stack alone
        var bogus = node(DataComponentNodes.SetComponent.class, "stack", sword,
                "component", id("kilagraph:no_such_component"), "nbt", wrapped(1));
        assertFalse(helper, "an unknown component is not ok", eval(bogus, "ok", Boolean.class));
        assertEq(helper, "and the stack is untouched", sword, eval(bogus, "out", ItemStack.class));
        helper.succeed();
    }

    /**
     * {@code Is Type} answers what {@code Cast} would, without throwing when the answer is no.
     *
     * <p>The value has to arrive over a wire: the input is declared {@code UNKNOWN}, and LDLib2 gives an
     * UNKNOWN port no embedded constant on purpose — an untyped port takes its value purely from
     * upstream. That is the same reason {@code Cast} has no constant on its input.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void instanceOfAnswersWithoutThrowing(GameTestHelper helper) {
        assertTrue(helper, "an identifier is an Identifier",
                probeIsType(KGTypeHandles.RESOURCE_LOCATION));
        // Anything coerces to String — Cast accepts that, so this must agree or a graph guarded by it
        // would refuse work that would have succeeded.
        assertTrue(helper, "and coerces to String", probeIsType(TypeHandles.STRING));
        assertFalse(helper, "but is not a BlockPos", probeIsType(KGTypeHandles.BLOCK_POS));

        // An unwired input is nothing, and nothing is not any type.
        var g = newGraph();
        NodeModel lonely = addNode(g, InstanceOfNode.class);
        setOption(lonely, "targetType", KGTypeHandles.BLOCK_POS.getIdentification());
        assertFalse(helper, "an unwired input is not a BlockPos",
                new GraphExecutor(g).evaluate(lonely.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }

    /** Is Type over a wired-in {@code ResourceLocation}, against {@code target}. */
    private static boolean probeIsType(com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle target) {
        var g = newGraph();
        NodeModel source = addNode(g, com.lowdragmc.kilagraph.blueprint.nodes.mc.id.McIdNodes.Create.class);
        setInputConstant(source, "namespace", "minecraft");
        setInputConstant(source, "path", "stone");
        NodeModel test = addNode(g, InstanceOfNode.class);
        setOption(test, "targetType", target.getIdentification());
        wire(g, test.getInputsById().get("in"), source.getOutputsById().get("out"));
        return new GraphExecutor(g).evaluate(test.getOutputsById().get("out"), Boolean.class);
    }

    /** A CompoundTag holding one int under the wrapper key the component nodes use. */
    private static CompoundTag wrapped(int value) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(DataComponentNodes.VALUE_KEY, value);
        return tag;
    }

    // ---- registry probes ---------------------------------------------------------------------

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void registryProbes(GameTestHelper helper) {
        var effect = node(RegistryProbeNodes.EffectExists.class, "id", id("minecraft:speed"));
        assertTrue(helper, "speed is an effect", eval(effect, "out", Boolean.class));
        assertTrue(helper, "and has a display name",
                !eval(effect, "displayName", Component.class).getString().isEmpty());
        var noEffect = node(RegistryProbeNodes.EffectExists.class, "id", id("kilagraph:nope"));
        assertFalse(helper, "an unknown effect", eval(noEffect, "out", Boolean.class));

        var sound = node(RegistryProbeNodes.SoundExists.class, "id", id("minecraft:entity.pig.ambient"));
        assertTrue(helper, "a real sound", eval(sound, "out", Boolean.class));
        var noSound = node(RegistryProbeNodes.SoundExists.class, "id", id("kilagraph:nope"));
        assertFalse(helper, "an unknown sound", eval(noSound, "out", Boolean.class));

        var particle = node(RegistryProbeNodes.ParticleExists.class, "id", id("minecraft:flame"));
        assertTrue(helper, "a real particle", eval(particle, "out", Boolean.class));

        var attribute = node(RegistryProbeNodes.AttributeExists.class,
                "id", id("minecraft:generic.movement_speed"));
        assertTrue(helper, "movement speed is an attribute", eval(attribute, "out", Boolean.class));
        helper.succeed();
    }

    // ---- helpers -----------------------------------------------------------------------------

    private record Probe(BlueprintGraph graph, NodeModel model) {
    }

    private static ResourceLocation id(String s) {
        return ResourceLocation.parse(s);
    }

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
}
