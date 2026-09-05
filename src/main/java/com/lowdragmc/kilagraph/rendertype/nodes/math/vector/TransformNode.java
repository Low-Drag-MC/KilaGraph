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
 * available matrices. Spaces: <b>object</b> (model space — per draw, or per instance on a GPU-instancing
 * pipeline; see the seams below), <b>view</b> (eye space), <b>world</b>
 * (absolute world), <b>clip</b>. Reachable because MC exposes {@code ModelViewMat} (object→view) and
 * {@code ProjMat} (view→clip), and KilaGraph precomputes the inverses + camera view matrix into the
 * {@code KG_Transforms} block (+ the camera world position from {@code globals.glsl}). View is the hub:
 * {@code from → view → to}. The object↔view leg goes through the compiler's
 * {@link ShaderCompileContext#objectToViewMatrix()} / {@link ShaderCompileContext#viewToObjectMatrix()}
 * seams rather than reading {@code ModelViewMat} directly, so on a GPU-instancing pipeline (no per-draw
 * model matrix — each instance carries its own transform) <b>object</b> still means the mesh's own local
 * space, the same thing the Position/Normal nodes' "Object" outputs mean.
 *
 * <p>The whole transform runs in homogeneous {@code vec4} and the node <b>outputs a vec4</b>: the
 * {@code w} is set from {@code type} — <b>position</b> → {@code w=1} (affine, includes translation),
 * <b>direction</b>/<b>normal</b> → {@code w=0} (linear, the {@code mat4} multiply drops translation
 * automatically); <b>normal</b> additionally {@code normalize}s the result and, on the object↔view leg,
 * transforms by the <b>inverse-transpose</b> — which here is just the transposed opposite seam, no
 * {@code inverse()} call. Everywhere else the matrices are MC's own pure rotations, where the
 * inverse-transpose IS the matrix, so direction and normal share a path (and the inverse-transpose is a
 * no-op on the vanilla object leg too); it only bites when an overriding pipeline puts a SCALE in the
 * object matrix, e.g. a particle's per-instance scale or a billboard's non-uniform width/height.
 * The <b>clip</b> target keeps the real perspective {@code w}, so {@code object → clip} equals exactly
 * {@code ProjMat * ModelViewMat * vec4(pos, 1)} and can drive {@code gl_Position} directly. As a
 * <b>source</b> space, {@code clip → view/world/object} is an inverse projection: for a <b>position</b> it
 * does the perspective divide ({@code (IProjMat*vec4(ndc,1)).xyz / .w}) to recover the true view point
 * (exact per-fragment), so e.g. {@code clip → world} reconstructs a world position from a depth/NDC sample;
 * direction/normal carry no position and keep the linear inverse (no divide). The <b>screen</b> target does
 * the perspective divide → {@code xy} in {@code [0,1]} + {@code z} = NDC depth (target-only; the divide is
 * exact only when this runs per-fragment). Downstream vec3 consumers just read {@code .xyz}.</p>
 *
 * <p><b>tangent</b> is available as both source and target, routed through object space (the basis is derived
 * there — see {@link ShaderCompileContext#tangentBasis(String)}, since MC has no per-vertex tangent). It is a
 * rotation about the surface point and carries no translation, so {@code w} is untouched in either direction.
 * {@code tangent → world} with {@code type = normal} is the standard "apply a normal map" step: feed it an
 * unpacked normal-map sample and it comes out as a world-space normal. Unity's Absolute-World isn't offered —
 * "world" here is already absolute.</p>
 */
@NodeAttribute(name = "rt_transform", group = "rendertype_math/vector", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class TransformNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_transform.tooltip");
    }


    private static final List<String> SPACES = List.of("object", "view", "world", "clip", "tangent");
    private static final List<String> TARGETS = List.of("object", "view", "world", "clip", "screen", "tangent");
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
        ShaderExpr view = ctx.temp(GlslType.VEC4, toView(ctx, from, src.code(), noTranslate, normal));

        if ("screen".equals(to)) {
            // clip, then perspective divide → screen: xy in [0,1], z = NDC depth.
            ShaderExpr clip = ctx.temp(GlslType.VEC4, projMat(ctx) + " * " + view.code());
            String c = clip.code();
            ctx.output("out", new ShaderExpr(
                    "vec4(" + c + ".xy / " + c + ".w * 0.5 + 0.5, " + c + ".z / " + c + ".w, 1.0)", GlslType.VEC4));
            return;
        }
        String result = fromView(ctx, to, view.code(), noTranslate, normal);
        ctx.output("out", new ShaderExpr(normal ? "vec4(normalize((" + result + ").xyz), 0.0)" : result, GlslType.VEC4));
    }

    /** {@code space → view}, as a vec4 (preserves the homogeneous w). {@code noTranslate} = direction/normal. */
    private String toView(ShaderCompileContext ctx, String space, String v4, boolean noTranslate, boolean normal) {
        return switch (space) {
            case "view" -> v4;
            // The tangent basis is derived in object space (ctx.tangentBasis), so tangent→view goes through
            // object. Tangent space is a rotation about the surface point — it carries no translation, so w
            // rides along untouched whatever the type is.
            case "tangent" -> {
                ShaderExpr obj = ctx.tangentToSpace("object", new ShaderExpr(v4 + ".xyz", GlslType.VEC3));
                yield objectToView(ctx, normal) + " * vec4(" + obj.code() + ", " + v4 + ".w)";
            }
            // world is absolute; camera-relative = world - cameraPos (positions only), then rotate to view.
            case "world" -> noTranslate
                    ? viewMat(ctx) + " * " + v4
                    : viewMat(ctx) + " * (" + v4 + " - vec4(" + cameraPos(ctx) + ", 0.0))";
            // clip(NDC)→view is an inverse projection: IProjMat * vec4(ndc, 1) yields homogeneous view
            // coords whose w must be divided out to recover the true view-space POSITION (perspective).
            // direction/normal carry no position (w=0) → keep the linear inverse, never divide (by ~0).
            case "clip" -> {
                if (noTranslate) yield iProjMat(ctx) + " * " + v4;
                ShaderExpr h = ctx.temp(GlslType.VEC4, iProjMat(ctx) + " * " + v4);
                yield "vec4(" + h.code() + ".xyz / " + h.code() + ".w, 1.0)";
            }
            default /* object */ -> objectToView(ctx, normal) + " * " + v4;
        };
    }

    /** {@code view → space}, as a vec4. The clip target keeps the real perspective w (for gl_Position). */
    private String fromView(ShaderCompileContext ctx, String space, String view4, boolean noTranslate, boolean normal) {
        return switch (space) {
            case "view" -> view4;
            // un-rotate to camera-relative world, then add cameraPos back to get absolute world (positions only).
            case "world" -> noTranslate
                    ? iViewMat(ctx) + " * " + view4
                    : "(" + iViewMat(ctx) + " * " + view4 + " + vec4(" + cameraPos(ctx) + ", 0.0))";
            case "clip" -> projMat(ctx) + " * " + view4;
            // view→object, then project onto the object-space tangent basis (see toView).
            case "tangent" -> {
                String obj = "(" + viewToObject(ctx, normal) + " * " + view4 + ").xyz";
                ShaderExpr t = ctx.spaceToTangent("object", new ShaderExpr(obj, GlslType.VEC3));
                yield "vec4(" + t.code() + ", " + (noTranslate ? "0.0" : "1.0") + ")";
            }
            default /* object */ -> viewToObject(ctx, normal) + " * " + view4;
        };
    }

    // ---- matrix accessors (register the owning UBO) -----------------------------------------

    /**
     * {@code object → view}. The object↔view leg is the only one that can carry a non-rotation, so it is the
     * only one that reads a SEAM rather than a raw uniform. The seam's default IS Minecraft's per-draw
     * {@code ModelViewMat}. But a pipeline that GPU-instances its geometry has no per-draw model matrix: each
     * instance carries its own rotate/scale/translate and the vertices arrive already in (camera-relative)
     * world space, so {@code ModelViewMat} there is the VIEW matrix and "object" would silently mean world.
     * Such a pipeline overrides the seam (see Photon's {@code PhotonShaderCompiler}) so this node's object
     * endpoint agrees with the Position/Normal nodes' "Object" outputs. The <b>tangent</b> endpoint rides
     * along: its basis is derived in object space, so it reaches view through this same matrix.
     * <p>
     * <b>normal</b> transforms by the inverse-transpose instead, which for this leg is just the transposed
     * <i>other</i> seam — no {@code inverse()} call. That matters precisely because an overriding pipeline can
     * put a SCALE in here (a particle's per-instance {@code iScale}, and a billboard's non-uniform width/height),
     * under which {@code M · n} is not perpendicular to the transformed surface. Everywhere else in this node
     * the matrices are Minecraft's own pure rotations, where the inverse-transpose equals the matrix — which is
     * why direction and normal share a path there, and why this stays a no-op on the vanilla default.
     */
    private String objectToView(ShaderCompileContext ctx, boolean normal) {
        // mat4(mat3) puts the rotation in the upper-left with a zero translation column; the vector's w is 0
        // for a normal, so the vec4 chain the rest of compile() runs in is unaffected.
        return normal ? "mat4(transpose(mat3(" + ctx.viewToObjectMatrix().code() + ")))"
                : ctx.objectToViewMatrix().code();
    }

    private String projMat(ShaderCompileContext ctx) {
        return ctx.useBuiltinUniform("ProjMat", GlslType.MAT4);
    }

    /** {@code view → object}: the inverse seam of {@link #objectToView} (default {@code IModelViewMat}), and
     *  symmetrically the transposed forward seam for a normal. */
    private String viewToObject(ShaderCompileContext ctx, boolean normal) {
        return normal ? "mat4(transpose(mat3(" + ctx.objectToViewMatrix().code() + ")))"
                : ctx.viewToObjectMatrix().code();
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

    /** Absolute world camera position in the precision-split form {@code kg_CameraBlockPos - kg_CameraOffset}
     *  (bound from the double camera position by KGBuiltinUniforms), so world translation stays jitter-free. */
    private String cameraPos(ShaderCompileContext ctx) {
        return ctx.cameraWorldPos().code();
    }

    @Override
    public List<String> optionChoices(String optionId) {
        return switch (optionId) {
            case "from" -> SPACES;
            case "to" -> TARGETS;
            case "type" -> TYPES;
            default -> List.of();
        };
    }

    @Override
    public String glslExample() {
        return """
                // object -> world, type = position
                vec4 v = ModelViewMat * vec4(in, 1.0);
                out = mat3(IViewMat) * v.xyz
                    + kg_CameraWorldPos;""";
    }
}
