package com.lowdragmc.kilagraph.blueprint.nodes.mc.world;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.InfoPropertyBlock;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.UseWithContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * The properties of a {@link Level}, one block each, usable only inside {@link LevelInfoNode}.
 *
 * <p>Grouped in one file because they are one subject and each is a handful of lines; the framework sees
 * them individually through {@code @NodeAttribute}, so the file layout is purely for reading.
 *
 * <h2>Weather is interpolated, and these blocks pass 1.0</h2>
 * {@code getRainLevel} and {@code getThunderLevel} take a partial tick so the client can smooth a
 * transition mid-frame. A blueprint is asking "how hard is it raining", not "how hard was it raining
 * 40% of the way through this tick", so both blocks pass {@code 1.0f} — the fully-current value, and the
 * same thing the server sees.
 */
public final class LevelInfoBlocks {

    private static final String GROUP = "mc/world";

    private LevelInfoBlocks() {
    }

    // ---- weather -----------------------------------------------------------------------------

    /** How hard it is raining, 0 to 1. */
    @NodeAttribute(name = "mc_level_rain_level", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(LevelInfoNode.class)
    public static class RainLevel extends InfoPropertyBlock<Level> {
        @OutputPort public float value;

        @Override
        protected Class<Level> targetClass() {
            return Level.class;
        }

        @Override
        protected void read(Level level, EvalContext ctx) {
            ctx.setOutput("value", level.getRainLevel(1.0f));
        }
    }

    /** How hard it is thundering, 0 to 1. */
    @NodeAttribute(name = "mc_level_thunder_level", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(LevelInfoNode.class)
    public static class ThunderLevel extends InfoPropertyBlock<Level> {
        @OutputPort public float value;

        @Override
        protected Class<Level> targetClass() {
            return Level.class;
        }

        @Override
        protected void read(Level level, EvalContext ctx) {
            ctx.setOutput("value", level.getThunderLevel(1.0f));
        }
    }

    /**
     * Whether it is raining and whether it is storming.
     *
     * <p>Both in one block because they are one question with two answers, and because they are not
     * independent — a thunderstorm rains. Testing {@code raining} alone is the usual mistake.</p>
     */
    @NodeAttribute(name = "mc_level_weather", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(LevelInfoNode.class)
    public static class Weather extends InfoPropertyBlock<Level> {
        @OutputPort public boolean raining;
        @OutputPort public boolean thundering;

        @Override
        protected Class<Level> targetClass() {
            return Level.class;
        }

        @Override
        protected void read(Level level, EvalContext ctx) {
            ctx.setOutput("raining", level.isRaining());
            ctx.setOutput("thundering", level.isThundering());
        }
    }

    // ---- time --------------------------------------------------------------------------------

    /**
     * The world clock.
     *
     * <p>{@code dayTime} is the time of day, which commands and the day/night cycle use and which
     * wraps every 24000 ticks. {@code gameTime} is the total age of the world and never wraps — it is
     * the one to use for anything that has to keep counting.</p>
     */
    @NodeAttribute(name = "mc_level_time", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(LevelInfoNode.class)
    public static class Time extends InfoPropertyBlock<Level> {
        @OutputPort public long dayTime;
        @OutputPort public long gameTime;
        @OutputPort public boolean day;
        @OutputPort public boolean night;

        @Override
        protected Class<Level> targetClass() {
            return Level.class;
        }

        @Override
        protected void read(Level level, EvalContext ctx) {
            ctx.setOutput("dayTime", level.getDayTime());
            ctx.setOutput("gameTime", level.getGameTime());
            ctx.setOutput("day", level.isDay());
            ctx.setOutput("night", level.isNight());
        }
    }

    // ---- identity and shape ------------------------------------------------------------------

    /** Which dimension this is, as an identifier — {@code minecraft:overworld} and friends. */
    @NodeAttribute(name = "mc_level_dimension", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(LevelInfoNode.class)
    public static class Dimension extends InfoPropertyBlock<Level> {
        @OutputPort public ResourceLocation value;

        @Override
        protected Class<Level> targetClass() {
            return Level.class;
        }

        @Override
        protected void read(Level level, EvalContext ctx) {
            ctx.setOutput("value", level.dimension().location());
        }
    }

    /**
     * The vertical extent of the world, and its sea level.
     *
     * <p>Not constants: a datapack can move them, and the Nether and End already differ from the
     * Overworld. A graph that iterates a column should read them rather than assume −64 to 320.</p>
     */
    @NodeAttribute(name = "mc_level_bounds", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(LevelInfoNode.class)
    public static class Bounds extends InfoPropertyBlock<Level> {
        @OutputPort public int minBuildHeight;
        @OutputPort public int maxBuildHeight;
        @OutputPort public int seaLevel;

        @Override
        protected Class<Level> targetClass() {
            return Level.class;
        }

        @Override
        protected void read(Level level, EvalContext ctx) {
            ctx.setOutput("minBuildHeight", level.getMinBuildHeight());
            ctx.setOutput("maxBuildHeight", level.getMaxBuildHeight());
            ctx.setOutput("seaLevel", level.getSeaLevel());
        }
    }

    /** The world's difficulty, as its lower-case name — {@code peaceful} to {@code hard}. */
    @NodeAttribute(name = "mc_level_difficulty", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(LevelInfoNode.class)
    public static class Difficulty extends InfoPropertyBlock<Level> {
        @OutputPort public String value;
        @OutputPort public boolean hardcore;

        @Override
        protected Class<Level> targetClass() {
            return Level.class;
        }

        @Override
        protected void read(Level level, EvalContext ctx) {
            // The serialized name, not the enum constant: it is the form commands and datapacks use.
            ctx.setOutput("value", level.getDifficulty().getSerializedName());
            ctx.setOutput("hardcore", level.getLevelData().isHardcore());
        }
    }

    /**
     * Whether this level belongs to the client.
     *
     * <p>The check a graph needs before doing anything authoritative. Client levels hold an
     * approximation — entity lists are incomplete and block entities may be stubs — so writing game
     * state from one is how desyncs are made.</p>
     */
    @NodeAttribute(name = "mc_level_is_client", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(LevelInfoNode.class)
    public static class IsClient extends InfoPropertyBlock<Level> {
        @OutputPort public boolean value;

        @Override
        protected Class<Level> targetClass() {
            return Level.class;
        }

        @Override
        protected void read(Level level, EvalContext ctx) {
            ctx.setOutput("value", level.isClientSide());
        }
    }
}
