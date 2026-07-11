package com.lowdragmc.kilagraph.rendertype.iris;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import static net.minecraft.commands.Commands.literal;

/**
 * Client-only {@code /kgiris} debug command for the Iris-compatibility work:
 * <ul>
 *   <li>{@code /kgiris status} — prints whether Iris is loaded, a shaderpack is active, and how many
 *       KilaGraph pipelines have been registered with Iris.</li>
 *   <li>{@code /kgiris tint} — toggles {@link IrisShaderInjector#DEBUG_FORCE_TINT} (seam test: all our
 *       geometry tinted red, ignoring the per-draw discriminator). Takes effect on the next shader reload
 *       (press <b>F3+T</b>), since the injection happens when Iris compiles the shaderpack programs.</li>
 * </ul>
 */
@EventBusSubscriber(modid = "kilagraph", value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class IrisDebugCommand {

    private IrisDebugCommand() {}

    /**
     * Drive the shaderpack reload that injects newly-registered KilaGraph surfaces, from the client tick —
     * the safe out-of-frame point (mirrors Iris's own reload keybind). A material created lazily after the
     * pack compiled isn't in the programs yet (its geometry draws the dispatch fallback / black) until this
     * reloads. No-op without Iris / once the programs are current. (Reloading mid-frame crashes the world
     * pipeline, so it must not be done from the render path.)
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (IrisCompat.LOADED) IrisCompat.clientTick();
    }

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal("kgiris")
                .then(literal("status").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "[KilaGraph/Iris] loaded=" + IrisCompat.LOADED
                                    + ", shaderpack=" + IrisCompat.isShaderPackInUse()
                                    + ", assignedPipelines=" + IrisCompat.assignedCount()
                                    + ", forceTint=" + IrisShaderInjector.DEBUG_FORCE_TINT), false);
                    return 1;
                }))
                .then(literal("tint").executes(ctx -> {
                    IrisShaderInjector.DEBUG_FORCE_TINT = !IrisShaderInjector.DEBUG_FORCE_TINT;
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "[KilaGraph/Iris] forceTint=" + IrisShaderInjector.DEBUG_FORCE_TINT
                                    + " — reload shaders to apply (toggle shaders off/on in Iris settings; F3+T does NOT recompile Iris)."), false);
                    return 1;
                }))
                .then(literal("surfaces").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "[KilaGraph/Iris] " + IrisSurfaceRegistry.debugSummary()
                                    + "\n  lastInjectedGeneration=" + IrisShaderInjector.lastInjectedGeneration()), false);
                    return 1;
                }))
                .then(literal("draw").executes(ctx -> drawCube(ctx.getSource(), false)))
                .then(literal("drawvanilla").executes(ctx -> drawCube(ctx.getSource(), true)));
        event.getDispatcher().register(root);
    }

    private static int drawCube(CommandSourceStack source, boolean vanilla) {
        BlockPos at = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.blockPosition() : BlockPos.ZERO;
        boolean on = IrisTestRenderer.toggle(at, vanilla);
        source.sendSuccess(() -> Component.literal(
                "[KilaGraph/Iris] test cube " + (on ? "ON @ " + at : "OFF")
                        + (vanilla ? " (VANILLA RenderType.entitySolid/dirt)" : " (KilaGraph material)")), false);
        return 1;
    }
}
