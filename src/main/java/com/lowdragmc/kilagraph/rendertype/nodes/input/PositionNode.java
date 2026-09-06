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
 * Unity's Position node: the mesh's surface position (vec3) in the space chosen by the dropdown. Stage-agnostic
 * ({@link com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity#ANY}) — in the vertex shader it reads the
 * raw {@code Position + ModelOffset}; in the fragment shader the interpolated {@code kg_modelPos} varying (both
 * via {@link ShaderCompileContext#meshPosition()}).
 *
 * <p>Spaces: <b>object</b> (per-draw model space), <b>view</b> (eye space, {@code ModelViewMat · pos}),
 * <b>world</b> (absolute world — the view point un-rotated by {@code IViewMat} plus the camera world position
 * from {@code globals.glsl}, matching {@link com.lowdragmc.kilagraph.rendertype.nodes.math.vector.TransformNode}'s
 * object→world), <b>tangent</b> (the object position projected onto the surface's tangent basis — see
 * {@link ShaderCompileContext#tangentBasis(String)}, which derives that basis because Minecraft carries no
 * per-vertex tangent). Unity's Absolute-World isn't offered — "world" here is already absolute. The per-node
 * preview has no real vertex stage, so it shows the preview mesh's forwarded object position ({@code vPos})
 * for every space (the preview camera's degenerate matrices make the world/view transforms meaningless —
 * like {@code meshNormal}'s preview).</p>
 */
@NodeAttribute(name = "rt_position", group = "rendertype_input", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class PositionNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_position.tooltip");
    }

    private static final List<String> SPACES = GeometrySpaces.SURFACE;

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption(GeometrySpaces.OPTION, TypeHandles.STRING).withDefaultValue(GeometrySpaces.WORLD)
                .withTooltips(Tooltips.of("kg.node.rt_position.option.space.tooltip"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, SPACES, GeometrySpaces::label)).build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String space = choice(GeometrySpaces.OPTION, GeometrySpaces.WORLD, SPACES);
        // Injection FIRST — injection implies isPreview(), and the preview branch's vPos is a preview-quad
        // varying that does NOT exist in an injected shaderpack fragment. The view-space position is
        // reconstructed from gl_FragCoord (reconstructedViewPos); object/world derive via KG_Transforms —
        // all three spaces are exact under a shaderpack.
        if (ctx.isInjection()) {
            ShaderExpr view = ctx.temp(GlslType.VEC3, ctx.reconstructedViewPos().code());
            String out = switch (space) {
                case GeometrySpaces.VIEW -> view.code();
                case GeometrySpaces.OBJECT -> "(" + ctx.transformField("IModelViewMat", GlslType.MAT4).code()
                        + " * vec4(" + view.code() + ", 1.0)).xyz";
                // The seam reconstructs its own object position under injection, so it needs no help here.
                case GeometrySpaces.TANGENT -> ctx.tangentSpacePosition().code();
                default /* world */ -> "(mat3(" + ctx.transformField("IViewMat", GlslType.MAT4).code()
                        + ") * " + view.code() + " + (vec3("
                        + ctx.transformField("CameraBlockPos", GlslType.VEC3).code() + ") - "
                        + ctx.transformField("CameraOffset", GlslType.VEC3).code() + "))";
            };
            ctx.output("out", new ShaderExpr(out, GlslType.VEC3));
            return;
        }
        // No real vertex stage in the per-node preview: show the forwarded object position for every space.
        if (ctx.isPreview()) {
            ctx.output("out", new ShaderExpr("vPos", GlslType.VEC3));
            return;
        }
        // The render pipeline owns the coordinate spaces (see ShaderGraphCompiler's *SpacePosition seams):
        // the vertex input is not necessarily object space (e.g. a subclass whose vertices are already world,
        // and whose object->world is not a matrix). This node just dispatches to the chosen space.
        ShaderExpr out = switch (space) {
            case GeometrySpaces.OBJECT -> ctx.objectSpacePosition();
            case GeometrySpaces.VIEW -> ctx.viewSpacePosition();
            case GeometrySpaces.TANGENT -> ctx.tangentSpacePosition();
            default /* world */ -> ctx.worldSpacePosition();
        };
        ctx.output("out", out);
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
                // object
                out = Position;
                // view
                out = (ModelViewMat * vec4(pos, 1.0)).xyz;
                // world
                out = mat3(IViewMat) * viewPos
                    + cameraWorldPos;
                // tangent
                out = vec3(dot(pos, T), dot(pos, B),
                           dot(pos, N));""";
    }
}
