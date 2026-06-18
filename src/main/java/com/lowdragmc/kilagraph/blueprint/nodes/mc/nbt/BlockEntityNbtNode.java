package com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Serialises a {@link BlockEntity} to its NBT compound (without block/position metadata). */
@NodeAttribute(name = "mc_nbt_block_entity", group = "mc_nbt", graphTypes = BlueprintGraph.class)
public class BlockEntityNbtNode extends AnnotatedNode {
    @InputPort public BlockEntity blockEntity;
    @OutputPort public CompoundTag out;
@Override public void evaluate(EvalContext ctx) {
        BlockEntity be = ctx.getInput("blockEntity", BlockEntity.class, null);
        if (be == null || be.getLevel() == null) { ctx.setOutput("out", new CompoundTag()); return; }
        ctx.setOutput("out", be.saveWithoutMetadata(be.getLevel().registryAccess()));
    }
}
