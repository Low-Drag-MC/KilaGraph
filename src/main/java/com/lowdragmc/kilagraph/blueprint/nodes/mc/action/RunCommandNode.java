package com.lowdragmc.kilagraph.blueprint.nodes.mc.action;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;

/**
 * Runs a Minecraft command.
 *
 * <h2>Why this node exists at all</h2>
 * It is the escape hatch. This mod has a few hundred nodes and the game has more verbs than that — a
 * command covers whatever has no node yet, and covers it with syntax users already know. A scripting
 * layer without one forces every gap to wait for a new node.
 *
 * <h2>Permission level 2, like a command block</h2>
 * Not 4. Level 2 is what a command block runs at, which makes this node's reach exactly the reach of a
 * contraption a player could already build: {@code summon}, {@code setblock}, {@code tp} and most of the
 * useful set work, while {@code op}, {@code stop} and {@code ban} do not. Level 4 would mean any graph
 * anyone imports can take over the server, and the permission is not exposed as an input because a port
 * that escalates privileges is a port someone will wire to a constant without reading this paragraph.
 *
 * <h2>Feedback is captured, not broadcast</h2>
 * A command run from a graph should not spam chat, and its output is often the reason for running it —
 * {@code data get} and {@code scoreboard players get} answer through their feedback text. So the source
 * collects messages instead of forwarding them, and hands them back on {@code output}. Nothing reaches
 * chat and the {@code sendCommandFeedback} gamerule is not involved.
 *
 * <h2>What the command sees as "here" and "me"</h2>
 * Relative coordinates and selectors need an origin. Given an {@code entity}, that entity is the executor
 * — {@code @s} resolves to it, {@code ~ ~ ~} is its position, and its rotation orients {@code ^ ^ ^}.
 * Given only a {@code pos}, the position is that block's centre with no executor, so {@code @s} matches
 * nothing. This is the same distinction as {@code /execute as} versus {@code /execute positioned}.
 */
@NodeAttribute(name = "mc_run_command", group = "mc/action", graphTypes = BlueprintGraph.class)
public class RunCommandNode extends AnnotatedNode {

    /**
     * Command-block parity, and deliberately not configurable — see the class javadoc. 26.1 replaced
     * the numeric permission level with a {@link PermissionSet}; GAMEMASTERS is the old level 2, which
     * is the tier command blocks run at.
     */
    private static final PermissionSet PERMISSION_SET = LevelBasedPermissionSet.GAMEMASTER;

    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_run_command.tooltip");
    }

    @ExecInputPort public ExecutionFlow trigger;
    @ExecOutputPort public ExecutionFlow next;

    @InputPort public Level level;
    @InputPort public String command = "";
    @InputPort public Entity entity;
    @InputPort public BlockPos pos = BlockPos.ZERO;
    @OutputPort public boolean ok;
    @OutputPort public int result;
    @OutputPort public String output = "";

    @Override
    public void execute(ExecContext ctx) {
        var world = McActions.writableLevel(ctx);
        String command = ctx.getInput("command", String.class, "");
        if (world == null || command == null || command.isBlank()) {
            fail(ctx);
            return;
        }
        var server = world.getServer();
        if (server == null) {
            fail(ctx);
            return;
        }

        Entity executor = ctx.getInput("entity", Entity.class, null);
        BlockPos at = ctx.getInput("pos", BlockPos.class, null);
        Vec3 origin = executor != null ? executor.position()
                : at != null ? Vec3.atCenterOf(at)
                : Vec3.ZERO;
        Vec2 rotation = executor != null ? executor.getRotationVector() : Vec2.ZERO;

        var collector = new Collector();
        // The result callback is the only way to learn what the command returned: 1.21 runs commands
        // through an execution queue and performPrefixedCommand discards the result on its own.
        var outcome = new Outcome();
        CommandSourceStack source = new CommandSourceStack(
                collector, origin, rotation, world, PERMISSION_SET,
                "KilaGraph", Component.literal("KilaGraph"), server, executor)
                .withCallback((success, value) -> {
                    outcome.success = success;
                    outcome.result = value;
                });

        // performPrefixedCommand rather than the dispatcher directly: it strips a leading slash, fires
        // NeoForge's CommandEvent so other mods can see and veto the call, and reports parse errors
        // through the source — all of which a graph should not reimplement.
        server.getCommands().performPrefixedCommand(source, command);

        ctx.setOutput("result", outcome.result);
        ctx.setOutput("output", collector.text());
        McActions.done(ctx, outcome.success);
    }

    private void fail(ExecContext ctx) {
        ctx.setOutput("result", 0);
        ctx.setOutput("output", "");
        McActions.done(ctx, false);
    }

    /** Mutable holder for the callback, which cannot assign to a local. */
    private static final class Outcome {
        private boolean success;
        private int result;
    }

    /**
     * A command source that keeps every message instead of sending it anywhere.
     *
     * <p>Both success and failure feedback are accepted, because a graph wants the text either way — a
     * failing {@code data get} explains why in its failure message, and dropping that would leave the
     * node reporting {@code ok = false} with nothing to say about it.</p>
     */
    private static final class Collector implements CommandSource {
        private final List<String> lines = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component component) {
            lines.add(component.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            // Admin notifications are for players typing commands; a graph running one is not news.
            return false;
        }

        String text() {
            return String.join("\n", lines);
        }
    }
}
