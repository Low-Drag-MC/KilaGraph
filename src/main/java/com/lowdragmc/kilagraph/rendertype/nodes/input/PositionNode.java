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
 * Unity's Position node: the mesh's surface position (vec3) in the space chosen by the dropdown. Stage-agnostic
 * ({@link com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity#ANY}) — in the vertex shader it reads the
 * raw {@code Position + ModelOffset}; in the fragment shader the interpolated {@code kg_modelPos} varying (both
 * via {@link ShaderCompileContext#meshPosition()}).
 *
 * <p>Spaces: <b>object</b> (per-draw model space), <b>view</b> (eye space, {@code ModelViewMat · pos}),
 * <b>world</b> (absolute world — the view point un-rotated by {@code IViewMat} plus the camera world position,
 * matching {@link com.lowdragmc.kilagraph.rendertype.nodes.math.vector.TransformNode}'s object→world). Unity's
 * Tangent / Absolute-World aren't offered — Minecraft meshes carry no per-vertex tangent basis, and "world"
 * here is already absolute. The per-node preview has no real vertex stage, so it shows the preview mesh's
 * forwarded object position ({@code vPos}) for every space (the preview camera's degenerate matrices make the
 * world/view transforms meaningless — like {@code meshNormal}'s preview).</p>
 */
@NodeAttribute(name = "rt_position", group = "rendertype_input", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class PositionNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_position.tooltip");
    }

    private static final List<String> SPACES = List.of("object", "world", "view");

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("space", TypeHandles.STRING).withDefaultValue("world")
                .withTooltips(Tooltips.of("kg.node.rt_position.option.space.tooltip"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, SPACES, PositionNode::label)).build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String space = choice("space", "world", SPACES);
        // No real vertex stage in the per-node preview: show the forwarded object position for every space.
        if (ctx.isPreview()) {
            ctx.output("out", new ShaderExpr("vPos", GlslType.VEC3));
            return;
        }
        // Object/model-space source: raw (Position + ModelOffset) in the vsh, kg_modelPos varying in the fsh.
        ShaderExpr pos = ctx.meshPosition();
        String out = switch (space) {
            case "object" -> pos.code();
            case "view" -> "(" + ctx.useBuiltinUniform("ModelViewMat", GlslType.MAT4)
                    + " * vec4(" + pos.code() + ", 1.0)).xyz";
            default /* world */ -> {
                // object -> view (ModelViewMat), un-rotate view -> camera-relative world (IViewMat), then add
                // the camera's absolute world position back — MC renders camera-relative, so this recovers
                // absolute world exactly like TransformNode's object->world for a position.
                String modelView = ctx.useBuiltinUniform("ModelViewMat", GlslType.MAT4);
                String iView = ctx.transformField("IViewMat", GlslType.MAT4).code();
                String cameraPos = ctx.useBuiltinUniform("kg_CameraPos", GlslType.VEC3);
                yield "((" + iView + " * " + modelView + " * vec4(" + pos.code() + ", 1.0)).xyz + " + cameraPos + ")";
            }
        };
        ctx.output("out", new ShaderExpr(out, GlslType.VEC3));
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
}
