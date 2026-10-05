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
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Reading and building a {@code DamageSource}: who is responsible ({@code entity}), what struck
 * ({@code directEntity}), where from, and a damage type. Damage types are a datapack registry, like
 * enchantments, so building a source resolves its type through a world.
 */
public final class DamageSourceNodes {

    private static final String GROUP = "mc/gameplay";

    private DamageSourceNodes() {
    }

    /** What a source says; {@code position} is its own position, else the direct entity's. */
    @NodeAttribute(name = "mc_damage_source_info", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Info extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_damage_source_info.tooltip");
        }

        @InputPort public DamageSource source;
        @OutputPort public Identifier type;
        @OutputPort public Entity entity;
        @OutputPort public Entity directEntity;
        @OutputPort public Vector3f position;
        @OutputPort public boolean hasPosition;
        @OutputPort public boolean direct;

        @Override
        public void evaluate(EvalContext ctx) {
            DamageSource s = ctx.getInput("source", DamageSource.class, null);
            Vec3 at = s == null ? null : s.getSourcePosition();
            ctx.setOutput("type", s == null ? null : s.typeHolder().unwrapKey().map(ResourceKey::identifier).orElse(null));
            ctx.setOutput("entity", s == null ? null : s.getEntity());
            ctx.setOutput("directEntity", s == null ? null : s.getDirectEntity());
            ctx.setOutput("position", at == null ? new Vector3f() : McConvert.toJoml(at));
            ctx.setOutput("hasPosition", at != null);
            ctx.setOutput("direct", s != null && s.isDirect());
        }
    }

    /** Whether a source's damage type is in a damage type tag. A tag id that does not exist is false. */
    @NodeAttribute(name = "mc_damage_source_is", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Is extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_damage_source_is.tooltip");
        }

        @InputPort public DamageSource source;
        @InputPort public Identifier tag;
        @OutputPort public boolean out;

        @Override
        public void evaluate(EvalContext ctx) {
            DamageSource s = ctx.getInput("source", DamageSource.class, null);
            Identifier id = ctx.getInput("tag", Identifier.class, null);
            ctx.setOutput("out", s != null && id != null && s.is(TagKey.create(Registries.DAMAGE_TYPE, id)));
        }
    }

    /**
     * A source of a given damage type, from an entity. {@code directEntity} defaults to {@code entity};
     * {@code position} is only used with {@code usePosition} on, since an unwired vector reads as the origin.
     * The type resolves through {@code level}, else the entity's world.
     */
    @NodeAttribute(name = "mc_make_damage_source", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Make extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_make_damage_source.tooltip");
        }

        @InputPort public Level level;
        @InputPort public Identifier type = Identifier.withDefaultNamespace("generic");
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
            Identifier id = ctx.getInput("type", Identifier.class, null);
            Holder<DamageType> holder = world == null || id == null ? null
                    : world.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(id).orElse(null);
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
