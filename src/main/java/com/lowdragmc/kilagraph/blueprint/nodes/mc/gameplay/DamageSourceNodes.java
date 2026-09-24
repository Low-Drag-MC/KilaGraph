package com.lowdragmc.kilagraph.blueprint.nodes.mc.gameplay;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.mc.McConvert;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Reading and building a {@code DamageSource} — how something was hurt.
 *
 * <h2>A source is a type and up to three things</h2>
 * Who is responsible ({@code entity}: the player who drew the bow), what physically did it
 * ({@code directEntity}: the arrow), where it came from, and a damage type that decides what armour,
 * enchantments and death messages make of it — through its tags ({@code minecraft:is_projectile},
 * {@code minecraft:bypasses_armor}). Hurting something with the right source is what gets the death
 * message, the aggro and the kill credit right; {@code mc_damage_entity} hurts with a generic source
 * from nobody.
 *
 * <p>Damage types are a datapack registry, like enchantments (see {@link EnchantmentNodes}), so
 * building a source resolves its type through a world.
 */
public final class DamageSourceNodes {

    private static final String GROUP = "mc/gameplay";

    private DamageSourceNodes() {
    }

    /**
     * What a source says.
     *
     * <p>{@code position} is where the damage came from: the source's own position when it was made
     * with one (an explosion with no entity behind it), otherwise where the direct entity is.
     * {@code hasPosition} tells that apart from a source with neither.</p>
     */
    @NodeAttribute(name = "mc_damage_source_info", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Info extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_damage_source_info.tooltip");
        }

        @InputPort public DamageSource source;
        @OutputPort public ResourceLocation type;
        @OutputPort public Entity entity;
        @OutputPort public Entity directEntity;
        @OutputPort public Vector3f position;
        @OutputPort public boolean hasPosition;
        @OutputPort public boolean direct;

        @Override
        public void evaluate(EvalContext ctx) {
            DamageSource s = ctx.getInput("source", DamageSource.class, null);
            Vec3 at = s == null ? null : s.getSourcePosition();
            ctx.setOutput("type", s == null ? null : s.typeHolder().unwrapKey().map(ResourceKey::location).orElse(null));
            ctx.setOutput("entity", s == null ? null : s.getEntity());
            ctx.setOutput("directEntity", s == null ? null : s.getDirectEntity());
            ctx.setOutput("position", at == null ? new Vector3f() : McConvert.toJoml(at));
            ctx.setOutput("hasPosition", at != null);
            ctx.setOutput("direct", s != null && s.isDirect());
        }
    }

    /**
     * Whether a source's damage type is in a damage type tag — the game's own way of asking "is this
     * fire", "is this a projectile", "does armour stop this". A tag id that does not exist is false.
     */
    @NodeAttribute(name = "mc_damage_source_is", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Is extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_damage_source_is.tooltip");
        }

        @InputPort public DamageSource source;
        @InputPort public ResourceLocation tag;
        @OutputPort public boolean out;

        @Override
        public void evaluate(EvalContext ctx) {
            DamageSource s = ctx.getInput("source", DamageSource.class, null);
            ResourceLocation rl = ctx.getInput("tag", ResourceLocation.class, null);
            ctx.setOutput("out", s != null && rl != null && s.is(TagKey.create(Registries.DAMAGE_TYPE, rl)));
        }
    }

    /**
     * A source of a given damage type, from an entity.
     *
     * <p>{@code directEntity} left unwired is {@code entity} itself — a melee hit, where the one
     * responsible is also the one that struck, which is what the game's own one-entity sources are.
     * {@code position} is only used with {@code usePosition} on, because an unwired vector reads as the
     * world's origin rather than as "none".
     *
     * <p>The world is only needed to resolve the type: the {@code level} input, else the world of
     * whichever entity is given. {@code ok} is false for a type no loaded datapack defines.</p>
     */
    @NodeAttribute(name = "mc_make_damage_source", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Make extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_make_damage_source.tooltip");
        }

        @InputPort public Level level;
        @InputPort public ResourceLocation type = ResourceLocation.withDefaultNamespace("generic");
        @InputPort public Entity entity;
        @InputPort public Entity directEntity;
        @InputPort public Vector3f position;
        @InputPort public boolean usePosition = false;
        @OutputPort public DamageSource source;
        @OutputPort public boolean ok;

        @Override
        public void evaluate(EvalContext ctx) {
            Entity causing = ctx.getInput("entity", Entity.class, null);
            Entity direct = ctx.getInput("directEntity", Entity.class, causing);
            Level world = ctx.getInput("level", Level.class, null);
            if (world == null) {
                world = causing != null ? causing.level() : direct != null ? direct.level() : null;
            }
            ResourceLocation id = ctx.getInput("type", ResourceLocation.class, null);
            Holder<DamageType> holder = world == null || id == null ? null
                    : world.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                            .getHolder(ResourceKey.create(Registries.DAMAGE_TYPE, id)).orElse(null);
            if (holder == null) {
                ctx.setOutput("source", null);
                ctx.setOutput("ok", false);
                return;
            }
            Vector3f at = ctx.getBool("usePosition", false) ? ctx.getInput("position", Vector3f.class, null) : null;
            ctx.setOutput("source", new DamageSource(holder, direct, causing, at == null ? null : McConvert.toVec3(at)));
            ctx.setOutput("ok", true);
        }
    }
}
