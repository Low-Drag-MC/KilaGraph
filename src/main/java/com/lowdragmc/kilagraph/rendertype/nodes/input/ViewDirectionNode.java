package com.lowdragmc.kilagraph.rendertype.nodes.input;

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
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.List;

/**
 * Unity's View Direction node: the <b>unnormalized</b> vector from the surface (vertex/fragment) to the
 * camera, in the space chosen by the dropdown. Its length is the distance to the camera.
 *
 * <p>The camera sits at the view-space origin, so the raw direction is simply {@code -viewPos} (view space);
 * because Minecraft's matrices are pure rotation + translation, rotating that vector into <b>world</b>
 * ({@code mat3(IViewMat)}) or <b>object</b> ({@code mat3(IModelViewMat)}) space preserves its length. Unity's
 * <b>Tangent</b> space isn't offered — Minecraft meshes carry no per-vertex tangent basis (the same reason
 * {@link com.lowdragmc.kilagraph.rendertype.nodes.math.vector.TransformNode} omits it). The surface position
 * is the interpolated model-space vertex position ({@link ShaderCompileContext#meshPosition()}), so the node
 * is fragment-safe and stage-agnostic. It is <b>unnormalized by default</b> (its length is the distance to the
 * camera); enable the {@code normalize} option for a unit-length direction.</p>
 */
@NodeAttribute(name = "rt_view_direction", group = "rendertype_input", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ViewDirectionNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_view_direction.tooltip");
    }

    private static final List<String> SPACES = List.of("world", "object", "view");

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("space", TypeHandles.STRING).withDefaultValue("world")
                .withTooltips(Tooltips.of("kg.node.rt_view_direction.option.space.tooltip"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, SPACES, ViewDirectionNode::label)).build();
        // Off by default: the raw vector carries the surface->camera distance in its length; turn on only
        // when a unit-length direction is wanted.
        context.addOption("normalize", TypeHandles.BOOL).withDefaultValue(false)
                .withTooltips(Tooltips.of("kg.node.rt_view_direction.option.normalize.tooltip")).build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String space = choice("space", "world", SPACES);
        // Camera is at the view-space origin, so the surface->camera direction is -viewPos; its length is the
        // distance to the camera. MC's matrices are pure rotation (mat3) + translation, so the view->world /
        // view->object rotations preserve that length across spaces.
        ShaderExpr dir;
        if (ctx.isInjection()) {
            // Under a shaderpack the surface position isn't a varying we control; reconstruct the view-space
            // position from gl_FragCoord + our own UBOs, then rotate that -viewPos into the chosen space
            // (reconstruction needs gl_FragCoord, so this can't go through the vanilla *SpaceViewDir seams).
            ShaderExpr vd = ctx.temp(GlslType.VEC3, "-" + ctx.reconstructedViewPos().code());
            String out = switch (space) {
                case "object" -> "mat3(" + ctx.transformField("IModelViewMat", GlslType.MAT4).code() + ") * " + vd.code();
                case "view" -> vd.code();
                default /* world */ -> "mat3(" + ctx.transformField("IViewMat", GlslType.MAT4).code() + ") * " + vd.code();
            };
            dir = new ShaderExpr(out, GlslType.VEC3);
        } else {
            // The render pipeline owns the coordinate spaces (see ShaderGraphCompiler's *SpaceViewDir seams):
            // each seam rotates -viewPos into the chosen space. This node just dispatches.
            dir = switch (space) {
                case "object" -> ctx.objectSpaceViewDir();
                case "view" -> ctx.viewSpaceViewDir();
                default /* world */ -> ctx.worldSpaceViewDir();
            };
        }
        // Normalize only when asked (default off) — the unnormalized vector's length is the camera distance.
        ctx.output("out", flag("normalize")
                ? new ShaderExpr("normalize(" + dir.code() + ")", GlslType.VEC3)
                : dir);
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    private static String label(String space) {
        return switch (space) {
            case "object" -> "Object";
            case "view" -> "View";
            default -> "World";
        };
    }

    private String choice(String id, String def, List<String> valid) {
        INodeOption opt = getNodeOptionById(id);
        Object raw = opt == null ? null : opt.tryGetValue(Object.class).result().orElse(null);
        return raw instanceof String s && valid.contains(s) ? s : def;
    }

    private boolean flag(String id) {
        INodeOption opt = getNodeOptionById(id);
        Object raw = opt == null ? null : opt.tryGetValue(Object.class).result().orElse(null);
        return raw instanceof Boolean b && b;
    }
}
