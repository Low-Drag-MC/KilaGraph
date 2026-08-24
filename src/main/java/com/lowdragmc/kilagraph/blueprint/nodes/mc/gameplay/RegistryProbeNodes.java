package com.lowdragmc.kilagraph.blueprint.nodes.mc.gameplay;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.Level;

/**
 * Existence and headline facts for the gameplay registries a blueprint refers to by id.
 *
 * <h2>Why these are probes and not values</h2>
 * A {@code MobEffect} or a {@code SoundEvent} is only useful in order to <em>apply</em> or <em>play</em>
 * it, and both of those are side effects — exec-flow nodes in a later batch. What a data-only graph can
 * usefully do beforehand is validate: check that an id a user typed or a config supplied actually names
 * something, and read the one or two facts needed to build a sensible value around it (an effect's
 * display name for a tooltip, an attribute's base value for a comparison).
 *
 * <p>So this group is deliberately thin, and will grow when the action nodes land rather than before.
 *
 * <h2>Enchantments need a world; the others do not</h2>
 * 1.21 moved enchantments into a datapack registry, so there is no {@code BuiltInRegistries.ENCHANTMENT}
 * to consult — {@code mc_enchantment_exists} therefore takes a {@code level} and goes through
 * {@code registryAccess()}, while effects, sounds, particles and attributes are all still built in.
 */
public final class RegistryProbeNodes {

    private static final String GROUP = "mc/gameplay";

    private RegistryProbeNodes() {
    }

    /** Whether a status-effect id names a real effect, plus its display name. */
    @NodeAttribute(name = "mc_effect_exists", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class EffectExists extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_effect_exists.tooltip");
        }

        @InputPort public ResourceLocation id;
        @OutputPort public boolean out;
        @OutputPort public Component displayName;

        @Override
        public void evaluate(EvalContext ctx) {
            ResourceLocation rl = id(ctx);
            MobEffect effect = rl == null ? null : BuiltInRegistries.MOB_EFFECT.get(rl);
            // MOB_EFFECT is not a defaulted registry, so a null result really does mean "no such id"
            ctx.setOutput("out", effect != null);
            ctx.setOutput("displayName", effect == null ? Component.empty() : effect.getDisplayName());
        }
    }

    @NodeAttribute(name = "mc_sound_exists", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SoundExists extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_sound_exists.tooltip");
        }

        @InputPort public ResourceLocation id;
        @OutputPort public boolean out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", contains(ctx, BuiltInRegistries.SOUND_EVENT));
        }
    }

    @NodeAttribute(name = "mc_particle_exists", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ParticleExists extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_particle_exists.tooltip");
        }

        @InputPort public ResourceLocation id;
        @OutputPort public boolean out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", contains(ctx, BuiltInRegistries.PARTICLE_TYPE));
        }
    }

    /** Whether an attribute id is real, and the value an entity has for it before any modifier. */
    @NodeAttribute(name = "mc_attribute_exists", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class AttributeExists extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_attribute_exists.tooltip");
        }

        @InputPort public ResourceLocation id;
        @OutputPort public boolean out;
        @OutputPort public double defaultValue;

        @Override
        public void evaluate(EvalContext ctx) {
            ResourceLocation rl = id(ctx);
            var attribute = rl == null ? null : BuiltInRegistries.ATTRIBUTE.get(rl);
            ctx.setOutput("out", attribute != null);
            ctx.setOutput("defaultValue", attribute == null ? 0d : attribute.getDefaultValue());
        }
    }

    /**
     * Whether an enchantment id is real in this world's datapacks, and its maximum level.
     *
     * <p>Takes a {@code level} because enchantments are datapack-registered in 1.21: the set depends on
     * the world's loaded packs, so there is nothing to check against without one. A null level answers
     * false rather than guessing from vanilla.</p>
     */
    @NodeAttribute(name = "mc_enchantment_exists", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class EnchantmentExists extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_enchantment_exists.tooltip");
        }

        @InputPort public Level level;
        @InputPort public ResourceLocation id;
        @OutputPort public boolean out;
        @OutputPort public int maxLevel;

        @Override
        public void evaluate(EvalContext ctx) {
            Level l = ctx.getInput("level", Level.class, null);
            ResourceLocation rl = id(ctx);
            if (l == null || rl == null) {
                ctx.setOutput("out", false);
                ctx.setOutput("maxLevel", 0);
                return;
            }
            var registry = l.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            var enchantment = registry.get(rl);
            ctx.setOutput("out", enchantment != null);
            ctx.setOutput("maxLevel", enchantment == null ? 0 : enchantment.getMaxLevel());
        }
    }

    // ---- shared ------------------------------------------------------------------------------

    private static ResourceLocation id(EvalContext ctx) {
        return ctx.getInput("id", ResourceLocation.class, null);
    }

    private static boolean contains(EvalContext ctx, Registry<?> registry) {
        ResourceLocation rl = id(ctx);
        return rl != null && registry.containsKey(rl);
    }
}
