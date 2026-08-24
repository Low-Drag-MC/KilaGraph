package com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * Serialises an {@link Entity} to its NBT compound.
 *
 * <p>The entity counterpart of {@code mc_nbt_block_entity}, and the way to reach state that has no
 * dedicated block: a horse's jump strength, a villager's trades, a modded mob's custom fields. Those live
 * on subclasses that no shared property block could reach, so NBT is the general escape hatch.
 *
 * <p>Without the id, matching {@code saveWithoutId} — the tag describes what the entity <em>is</em>, not
 * which entity it is, so it can be written onto another entity of the same type.
 */
@NodeAttribute(name = "mc_nbt_entity", group = "mc_nbt", graphTypes = BlueprintGraph.class)
public class EntityNbtNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_nbt_entity.tooltip");
    }

    @InputPort public Entity entity;
    @OutputPort public CompoundTag out;

    @Override
    public void evaluate(EvalContext ctx) {
        Entity e = ctx.getInput("entity", Entity.class, null);
        if (e == null) {
            ctx.setOutput("out", new CompoundTag());
            return;
        }
        // 26.1 writes entity NBT through ValueOutput; TagValueOutput is the CompoundTag-backed one.
        var out = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, e.level().registryAccess());
        e.saveWithoutId(out);
        ctx.setOutput("out", out.buildResult());
    }
}
