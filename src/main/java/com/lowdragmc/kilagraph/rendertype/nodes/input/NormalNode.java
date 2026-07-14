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
 * Unity's Normal Vector node: the mesh's surface normal (vec3) in the space chosen by the dropdown. Stage-agnostic
 * ({@link com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity#ANY}) — the object-space normal source is the
 * raw {@code Normal} attribute in the vertex shader and the interpolated {@code kg_objectNormal} varying in the
 * fragment shader (a missing {@code Normal} element degrades to object +Y). All spaces share that single source
 * and {@code normalize} the result (interpolation isn't unit-length).
 *
 * <p>Spaces: <b>object</b> (model space), <b>view</b> ({@code mat3(ModelViewMat) · N}), <b>world</b>
 * ({@code mat3(IViewMat) · mat3(ModelViewMat) · N}, identical to {@link ShaderCompileContext#meshNormal()}).
 * Because MC's matrices are pure rotations, normals transform with the plain (non-inverse-transpose) rotation.
 * Unity's Tangent space isn't offered — Minecraft meshes carry no per-vertex tangent basis. The per-node preview
 * shows the preview mesh's own interpolated normal ({@code vNormal}) for every space, like {@code meshNormal}.</p>
 */
@NodeAttribute(name = "rt_normal", group = "rendertype_input", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class NormalNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_normal.tooltip");
    }

    private static final List<String> SPACES = List.of("object", "world", "view");

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("space", TypeHandles.STRING).withDefaultValue("world")
                .withTooltips(Tooltips.of("kg.node.rt_normal.option.space.tooltip"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, SPACES, NormalNode::label)).build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String space = choice("space", "world", SPACES);
        // Injection FIRST — injection implies isPreview(), and the preview branch's vNormal is a
        // preview-quad varying that does NOT exist in an injected shaderpack fragment. meshNormal()
        // reconstructs the WORLD normal from the pack's own view-space varying (kg_recon_normal);
        // spaces convert with the KG_Transforms rotations (pure rotations, so plain mat3 is exact).
        if (ctx.isInjection()) {
            ShaderExpr world = ctx.temp(GlslType.VEC3, "normalize(" + ctx.meshNormal().code() + ")");
            String iView = ctx.transformField("IViewMat", GlslType.MAT4).code();
            String out = switch (space) {
                case "object" -> "mat3(" + ctx.transformField("IModelViewMat", GlslType.MAT4).code()
                        + ") * (transpose(mat3(" + iView + ")) * " + world.code() + ")";
                case "view" -> "transpose(mat3(" + iView + ")) * " + world.code();
                default /* world */ -> world.code();
            };
            ctx.output("out", new ShaderExpr(out, GlslType.VEC3));
            return;
        }
        // No real vertex stage in the per-node preview: the preview mesh carries a real Normal (vNormal).
        if (ctx.isPreview()) {
            ctx.output("out", new ShaderExpr("normalize(vNormal)", GlslType.VEC3));
            return;
        }
        // The render pipeline owns the coordinate spaces (see ShaderGraphCompiler's *SpaceNormal seams);
        // each seam returns the already-normalized normal for its space. This node just dispatches.
        ShaderExpr out = switch (space) {
            case "object" -> ctx.objectSpaceNormal();
            case "view" -> ctx.viewSpaceNormal();
            default /* world */ -> ctx.worldSpaceNormal();
        };
        ctx.output("out", out);
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
