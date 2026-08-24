package com.lowdragmc.kilagraph.blueprint.nodes.mc.entity;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.InfoPropertyBlock;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.UseWithContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The properties a {@link Player} has that a plain entity does not.
 *
 * <p>Only inside {@link PlayerInfoNode}. Everything an entity has — position, health, state — comes from
 * {@link EntityInfoBlocks}, whose blocks are scoped to both contexts, so a Player Info node offers this
 * file and that one together. That is the whole reason to have a separate player context: it is an entity
 * context plus these.
 *
 * <p>Getting a {@code Player} out of an {@code Entity} in the first place is {@code mc_entity_as_player}.
 */
public final class PlayerInfoBlocks {

    private static final String GROUP = "mc/entity";

    private PlayerInfoBlocks() {
    }

    /** Base so each concrete block is only its ports and its read. */
    private abstract static class PlayerBlock extends InfoPropertyBlock<Player> {
        @Override
        protected final Class<Player> targetClass() {
            return Player.class;
        }
    }

    /**
     * Hunger.
     *
     * <p>{@code food} is the 0-to-20 bar. {@code saturation} is the hidden reserve that drains before
     * the bar does, which is why a player who just ate looks unchanged for a while. {@code exhaustion}
     * is the counter that consumes saturation as the player acts.</p>
     */
    @NodeAttribute(name = "mc_player_food", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(PlayerInfoNode.class)
    public static class Food extends PlayerBlock {
        @OutputPort public int food;
        @OutputPort public float saturation;
        @OutputPort public float exhaustion;

        @Override
        protected void read(Player player, EvalContext ctx) {
            var data = player.getFoodData();
            ctx.setOutput("food", data.getFoodLevel());
            ctx.setOutput("saturation", data.getSaturationLevel());
            ctx.setOutput("exhaustion", data.getExhaustionLevel());
        }
    }

    /**
     * Experience.
     *
     * <p>{@code level} is the number shown above the bar; {@code progress} is how full the bar is, 0 to
     * 1; {@code total} is the lifetime points, which is what the score screen shows and is not the sum of
     * the other two.</p>
     */
    @NodeAttribute(name = "mc_player_experience", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(PlayerInfoNode.class)
    public static class Experience extends PlayerBlock {
        @OutputPort public int level;
        @OutputPort public float progress;
        @OutputPort public int total;

        @Override
        protected void read(Player player, EvalContext ctx) {
            ctx.setOutput("level", player.experienceLevel);
            ctx.setOutput("progress", player.experienceProgress);
            ctx.setOutput("total", player.totalExperience);
        }
    }

    /** What the player is holding, in each hand. */
    @NodeAttribute(name = "mc_player_held_items", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(PlayerInfoNode.class)
    public static class HeldItems extends PlayerBlock {
        @OutputPort public ItemStack mainHand = ItemStack.EMPTY;
        @OutputPort public ItemStack offHand = ItemStack.EMPTY;

        @Override
        protected void read(Player player, EvalContext ctx) {
            ctx.setOutput("mainHand", player.getItemInHand(InteractionHand.MAIN_HAND));
            ctx.setOutput("offHand", player.getItemInHand(InteractionHand.OFF_HAND));
        }
    }

    /**
     * Which mode the player is in.
     *
     * <p>As three booleans rather than a mode name, because that is how a graph uses it — the question is
     * always "may this player be affected by what I am about to do", and creative and spectator are the
     * two answers that change it. Adventure reads as none of the three.</p>
     */
    @NodeAttribute(name = "mc_player_game_mode", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(PlayerInfoNode.class)
    public static class GameMode extends PlayerBlock {
        @OutputPort public boolean creative;
        @OutputPort public boolean spectator;
        @OutputPort public boolean canBuild;

        @Override
        protected void read(Player player, EvalContext ctx) {
            ctx.setOutput("creative", player.isCreative());
            ctx.setOutput("spectator", player.isSpectator());
            ctx.setOutput("canBuild", player.mayBuild());
        }
    }

    /** Whether the player is asleep, and whether they are crouching. */
    @NodeAttribute(name = "mc_player_posture", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(PlayerInfoNode.class)
    public static class Posture extends PlayerBlock {
        @OutputPort public boolean sleeping;
        @OutputPort public boolean crouching;

        @Override
        protected void read(Player player, EvalContext ctx) {
            ctx.setOutput("sleeping", player.isSleeping());
            ctx.setOutput("crouching", player.isCrouching());
        }
    }
}
