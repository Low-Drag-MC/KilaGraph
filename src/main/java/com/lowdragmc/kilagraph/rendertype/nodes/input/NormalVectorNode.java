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
 * Unity's Normal Vector node: the mesh's interpolated surface normal, normalized, in the space chosen by
 * the dropdown. Built on {@link ShaderCompileContext#meshNormal()} (world space), which works in all three
 * compile modes: the normal vertex pipeline (real vertex normal), node previews (the preview mesh's
 * normal — a sphere shows real curvature), and Iris injection (reconstructed from the shaderpack's own
 * view-space {@code normal} varying; packs without one — e.g. photon — fall back to a constant, so the
 * graph degrades but never breaks the pack).
 *
 * <p>Minecraft's matrices are pure rotation + translation, so a normal converts between spaces with the
 * plain {@code mat3} rotations (no inverse-transpose needed): world&rarr;view is
 * {@code transpose(mat3(IViewMat))} (the inverse of the view&rarr;world rotation), then view&rarr;object is
 * {@code mat3(IModelViewMat)} — the same {@code KG_Transforms} fields {@code ViewDirectionNode} uses.
 * <b>Tangent</b> space isn't offered (Minecraft meshes carry no per-vertex tangent basis).</p>
 */
@NodeAttribute(name = "rt_normal_vector", group = "rendertype_input", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class NormalVectorNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_normal_vector.tooltip");
    }

    private static final List<String> SPACES = List.of("world", "object", "view");

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("space", TypeHandles.STRING).withDefaultValue("world")
                .withTooltips(Tooltips.of("kg.node.rt_normal_vector.option.space.tooltip"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, SPACES, NormalVectorNode::label)).build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String space = choice("space", "world", SPACES);
        // meshNormal() is world-space but not unit-length after interpolation — normalize once here
        // (Unity's Normal Vector outputs a normalized normal).
        ShaderExpr world = ctx.temp(GlslType.VEC3, "normalize(" + ctx.meshNormal().code() + ")");
        String out = switch (space) {
            case "view" -> "transpose(mat3(" + ctx.transformField("IViewMat", GlslType.MAT4).code() + ")) * " + world.code();
            case "object" -> "mat3(" + ctx.transformField("IModelViewMat", GlslType.MAT4).code() + ") * (transpose(mat3("
                    + ctx.transformField("IViewMat", GlslType.MAT4).code() + ")) * " + world.code() + ")";
            default /* world */ -> world.code();
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
