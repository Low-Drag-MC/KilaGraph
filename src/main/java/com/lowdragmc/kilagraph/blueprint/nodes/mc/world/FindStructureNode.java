package com.lowdragmc.kilagraph.blueprint.nodes.mc.world;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * The nearest generated structure of a given kind, as a position.
 *
 * <h2>Why this is a node when /locate exists</h2>
 * {@code /locate} answers in chat. {@code mc_run_command} would hand the graph that sentence and leave it
 * to pull three numbers back out of prose that is translated per language. This returns the position.
 *
 * <h2>This one is expensive, and unusually so</h2>
 * The search walks candidate chunk positions outward and asks the world generator about each — the same
 * work {@code /locate} does, on the server thread, and a wide radius on a slow generator is measured in
 * seconds. Nothing else in this mod costs anything like it. Call it in response to something a player did,
 * not on a tick, and keep the radius small when the structure is common.
 *
 * <p>{@code radius} is in chunks, not blocks, matching the command. It is capped rather than trusted: a
 * radius wired from a runaway counter would otherwise search the world.
 *
 * <p>Existing chunks are not skipped, so a structure that has already generated nearby is found rather than
 * passed over — the same choice {@code /locate} makes, and the much cheaper one, since skipping requires
 * loading each candidate chunk to look at it.
 *
 * <p>Server side only, and false when the world was generated with structures turned off.
 */
@NodeAttribute(name = "mc_find_structure", group = "mc_world", graphTypes = BlueprintGraph.class)
public class FindStructureNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_find_structure.tooltip");
    }

    /** Chunks. {@code /locate structure} defaults to 100; past a few hundred the search stops being usable. */
    private static final int MAX_RADIUS = 200;

    @InputPort public Level level;
    @InputPort public BlockPos pos = BlockPos.ZERO;
    @InputPort public ResourceLocation structure;
    @InputPort public int radius = 100;
    @OutputPort public BlockPos out;
    @OutputPort public double distance;
    @OutputPort public boolean found;

    @Override
    public void evaluate(EvalContext ctx) {
        BlockPos from = ctx.getInput("pos", BlockPos.class, BlockPos.ZERO);
        BlockPos at = from == null ? null : search(ctx, from);
        ctx.setOutput("out", at);
        ctx.setOutput("distance", at == null ? 0d : Math.sqrt(at.distSqr(from)));
        ctx.setOutput("found", at != null);
    }

    /** The nearest position, or null for any of the several ways this can come up empty. */
    private static BlockPos search(EvalContext ctx, BlockPos from) {
        if (!(ctx.getInput("level", Level.class, null) instanceof ServerLevel world)) return null;
        if (!world.getServer().getWorldData().worldGenOptions().generateStructures()) return null;
        ResourceLocation id = ctx.getInput("structure", ResourceLocation.class, null);
        if (id == null) return null;

        // Structures are a datapack registry, so this goes through the world rather than a static table.
        Holder<Structure> holder = world.registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .getHolder(ResourceKey.create(Registries.STRUCTURE, id))
                .orElse(null);
        if (holder == null) return null;

        int radius = Math.clamp(ctx.getInt("radius", 100), 1, MAX_RADIUS);
        // ServerLevel's own helper takes a tag; a set of exactly one holder is how a single structure is
        // asked for through the same generator call.
        var hit = world.getChunkSource().getGenerator()
                .findNearestMapStructure(world, HolderSet.direct(holder), from, radius, false);
        return hit == null ? null : hit.getFirst();
    }
}
