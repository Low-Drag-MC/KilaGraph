package com.lowdragmc.kilagraph.rendertype.nodes.math.vector;

import com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips;
import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.gui.ChoiceConfigurator;
import com.lowdragmc.kilagraph.graph.util.NodeTooltipHelper;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.List;

/**
 * Converts a {@code vec3} between coordinate spaces — Unity's Transform node, adapted to Minecraft's
 * available matrices. Spaces: <b>object</b> (per-draw model space), <b>view</b> (eye space), <b>world</b>
 * (absolute world), <b>clip</b>. Reachable because MC exposes {@code ModelViewMat} (object→view) and
 * {@code ProjMat} (view→clip), and KilaGraph precomputes the inverses + camera view matrix into the
 * {@code KG_Transforms} block (+ the camera world position from {@code globals.glsl}). View is the hub:
 * {@code from → view → to}.
 *
 * <p>The whole transform runs in homogeneous {@code vec4} and the node <b>outputs a vec4</b>: the
 * {@code w} is set from {@code type} — <b>position</b> → {@code w=1} (affine, includes translation),
 * <b>direction</b>/<b>normal</b> → {@code w=0} (linear, the {@code mat4} multiply drops translation
 * automatically); <b>normal</b> additionally {@code normalize}s the result. (MC's matrices are pure
 * rotations, so a normal's correct inverse-transpose equals the matrix itself — Direction math + a
 * normalize is exact here; non-uniform scale, which MC doesn't have, would need the inverse-transpose.)
 * The <b>clip</b> target keeps the real perspective {@code w}, so {@code object → clip} equals exactly
 * {@code ProjMat * ModelViewMat * vec4(pos, 1)} and can drive {@code gl_Position} directly. The
 * <b>screen</b> target does the perspective divide → {@code xy} in {@code [0,1]} + {@code z} = NDC depth
 * (target-only; the divide is exact only when this runs per-fragment). Downstream vec3 consumers just
 * read {@code .xyz}. Unity's Tangent / Absolute-World aren't offered — MC has no per-vertex tangent basis.</p>
 */
@NodeAttribute(name = "rt_transform", group = "rendertype_math/vector", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class TransformNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_transform.tooltip");
    }


    private static final List<String> SPACES = List.of("object", "view", "world", "clip");
    private static final List<String> TARGETS = List.of("object", "view", "world", "clip", "screen");
    private static final List<String> TYPES = List.of("position", "direction", "normal");

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("from", TypeHandles.STRING).withDefaultValue("object")
                .withTooltips(Tooltips.of("kg.node.rt_transform.option.from.tooltip"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, SPACES)).build();
        context.addOption("to", TypeHandles.STRING).withDefaultValue("world")
                .withTooltips(Tooltips.of("kg.node.rt_transform.option.to.tooltip"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, TARGETS)).build();
        context.addOption("type", TypeHandles.STRING).withDefaultValue("position")
                .withTooltips(Tooltips.of("kg.node.rt_transform.option.type.tooltip"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, TYPES)).build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", RenderTypeGraphTypes.VEC3);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC4);
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    private String choice(String id, String def, List<String> valid) {
        INodeOption opt = getNodeOptionById(id);
        Object raw = opt == null ? null : opt.tryGetValue(Object.class).result().orElse(null);
        return raw instanceof String s && valid.contains(s) ? s : def;
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String from = choice("from", "object", SPACES);
        String to = choice("to", "world", TARGETS);
        String type = choice("type", "position", TYPES);
        boolean noTranslate = !"position".equals(type); // direction & normal carry no translation
        boolean normal = "normal".equals(type);
        String in = ctx.input("in").code();
        String w = noTranslate ? "0.0" : "1.0";

        if (from.equals(to)) {
            ctx.output("out", new ShaderExpr(normal ? "vec4(normalize(" + in + "), 0.0)"
                    : "vec4(" + in + ", " + w + ")", GlslType.VEC4));
            return;
        }
        ShaderExpr src = ctx.temp(GlslType.VEC4, "vec4(" + in + ", " + w + ")");
        ShaderExpr view = ctx.temp(GlslType.VEC4, toView(ctx, from, src.code(), noTranslate));

        if ("screen".equals(to)) {
            // clip, then perspective divide → screen: xy in [0,1], z = NDC depth.
            ShaderExpr clip = ctx.temp(GlslType.VEC4, projMat(ctx) + " * " + view.code());
            String c = clip.code();
            ctx.output("out", new ShaderExpr(
                    "vec4(" + c + ".xy / " + c + ".w * 0.5 + 0.5, " + c + ".z / " + c + ".w, 1.0)", GlslType.VEC4));
            return;
        }
        String result = fromView(ctx, to, view.code(), noTranslate);
        ctx.output("out", new ShaderExpr(normal ? "vec4(normalize((" + result + ").xyz), 0.0)" : result, GlslType.VEC4));
    }

    /** {@code space → view}, as a vec4 (preserves the homogeneous w). {@code noTranslate} = direction/normal. */
    private String toView(ShaderCompileContext ctx, String space, String v4, boolean noTranslate) {
        return switch (space) {
            case "view" -> v4;
            // world is absolute; camera-relative = world - cameraPos (positions only), then rotate to view.
            case "world" -> noTranslate
                    ? viewMat(ctx) + " * " + v4
                    : viewMat(ctx) + " * (" + v4 + " - vec4(" + cameraPos(ctx) + ", 0.0))";
            case "clip" -> iProjMat(ctx) + " * " + v4;
            default /* object */ -> modelViewMat(ctx) + " * " + v4;
        };
    }

    /** {@code view → space}, as a vec4. The clip target keeps the real perspective w (for gl_Position). */
    private String fromView(ShaderCompileContext ctx, String space, String view4, boolean noTranslate) {
        return switch (space) {
            case "view" -> view4;
            // un-rotate to camera-relative world, then add cameraPos back to get absolute world (positions only).
            case "world" -> noTranslate
                    ? iViewMat(ctx) + " * " + view4
                    : "(" + iViewMat(ctx) + " * " + view4 + " + vec4(" + cameraPos(ctx) + ", 0.0))";
            case "clip" -> projMat(ctx) + " * " + view4;
            default /* object */ -> iModelViewMat(ctx) + " * " + view4;
        };
    }

    // ---- matrix accessors (register the owning UBO) -----------------------------------------

    private String modelViewMat(ShaderCompileContext ctx) {
        ctx.useMinecraftUniform("DynamicTransforms", "minecraft:dynamictransforms.glsl");
        return "ModelViewMat";
    }

    private String projMat(ShaderCompileContext ctx) {
        ctx.useMinecraftUniform("Projection", "minecraft:projection.glsl");
        return "ProjMat";
    }

    private String iModelViewMat(ShaderCompileContext ctx) {
        return ctx.transformField("IModelViewMat", GlslType.MAT4).code();
    }

    private String viewMat(ShaderCompileContext ctx) {
        return ctx.transformField("ViewMat", GlslType.MAT4).code();
    }

    private String iViewMat(ShaderCompileContext ctx) {
        return ctx.transformField("IViewMat", GlslType.MAT4).code();
    }

    /** clip→view: the precomputed inverse projection from our KG_Transforms block (no per-pixel inverse). */
    private String iProjMat(ShaderCompileContext ctx) {
        return ctx.transformField("IProjMat", GlslType.MAT4).code();
    }

    /** Absolute world camera position from MC's {@code globals.glsl}. MC stores CameraBlockPos =
     *  floor(camPos) and CameraOffset = floor(camPos) - camPos, so camPos = block - offset. */
    private String cameraPos(ShaderCompileContext ctx) {
        ctx.useMinecraftUniform("Globals", "minecraft:globals.glsl");
        return "(vec3(CameraBlockPos) - CameraOffset)";
    }
}
