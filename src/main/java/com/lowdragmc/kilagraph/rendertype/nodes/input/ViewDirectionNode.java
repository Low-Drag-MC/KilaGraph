package com.lowdragmc.kilagraph.rendertype.nodes.input;

import com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips;
import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GeometrySpaces;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.gui.ChoiceConfigurator;
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
 * ({@code mat3(IViewMat)}) or <b>object</b> ({@code mat3(IModelViewMat)}) space preserves its length.
 * <b>tangent</b> projects it onto the surface's tangent basis ({@link ShaderCompileContext#tangentBasis(String)}
 * derives that basis — Minecraft carries no per-vertex tangent); that is the form parallax/relief mapping and
 * tangent-space lighting want, since the uv offset they need is just the direction's {@code xy}. The surface position
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

    private static final List<String> SPACES = GeometrySpaces.SURFACE;

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption(GeometrySpaces.OPTION, TypeHandles.STRING).withDefaultValue(GeometrySpaces.WORLD)
                .withTooltips(Tooltips.of("kg.node.rt_view_direction.option.space.tooltip"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, SPACES, GeometrySpaces::label)).build();
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
        String space = choice(GeometrySpaces.OPTION, GeometrySpaces.WORLD, SPACES);
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
                case GeometrySpaces.OBJECT ->
                        "mat3(" + ctx.transformField("IModelViewMat", GlslType.MAT4).code() + ") * " + vd.code();
                case GeometrySpaces.VIEW -> vd.code();
                // The seam reconstructs its own object-space direction under injection.
                case GeometrySpaces.TANGENT -> ctx.tangentSpaceViewDir().code();
                default /* world */ -> "mat3(" + ctx.transformField("IViewMat", GlslType.MAT4).code() + ") * " + vd.code();
            };
            dir = new ShaderExpr(out, GlslType.VEC3);
        } else {
            // The render pipeline owns the coordinate spaces (see ShaderGraphCompiler's *SpaceViewDir seams):
            // each seam rotates -viewPos into the chosen space. This node just dispatches.
            dir = switch (space) {
                case GeometrySpaces.OBJECT -> ctx.objectSpaceViewDir();
                case GeometrySpaces.VIEW -> ctx.viewSpaceViewDir();
                // The basis is orthonormal, so tangent keeps the length (= camera distance) like the
                // rotations do.
                case GeometrySpaces.TANGENT -> ctx.tangentSpaceViewDir();
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

    @Override
    public List<String> optionChoices(String optionId) {
        return GeometrySpaces.optionChoices(optionId);
    }

    @Override
    public String glslExample() {
        return """
                // view: the camera is at the origin
                vec3 dir = -viewPos;
                // world
                out = mat3(IViewMat) * dir;
                // tangent
                out = vec3(dot(dir, T), dot(dir, B),
                           dot(dir, N));
                // with normalize enabled
                out = normalize(out);""";
    }
}
