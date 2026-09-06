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
 * Unity's Tangent Vector node: the surface tangent (vec3, unit length) in the space chosen by the dropdown —
 * the direction the texture's <b>+u</b> axis runs across the surface. Stage-agnostic, but the quality of the
 * result depends on the stage: see {@link ShaderCompileContext#tangentBasis(String)}, which derives the basis
 * because Minecraft's vertex formats carry no tangent attribute. In the fragment stage it comes from the mesh
 * uv's own cotangent frame (so it follows rotated/mirrored uv islands correctly); in the vertex stage there
 * are no screen-space derivatives, so it degrades to an arbitrary-but-stable basis built from the normal.
 *
 * <p>Spaces: <b>object</b> (the space the basis is derived in), <b>view</b>, <b>world</b> (the same rotations
 * the {@link NormalNode} uses, so tangent and normal always agree), and <b>tangent</b> — the constant
 * {@code (1,0,0)}, since the tangent is the basis' own first axis. Pair with {@link BitangentNode} and the
 * Normal node to build a full TBN, or just use {@code Transform(tangent → world, normal)} to apply a normal
 * map.</p>
 */
@NodeAttribute(name = "rt_tangent", group = "rendertype_input", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class TangentNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_tangent.tooltip");
    }

    private static final List<String> SPACES = GeometrySpaces.SURFACE;

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption(GeometrySpaces.OPTION, TypeHandles.STRING).withDefaultValue(GeometrySpaces.WORLD)
                .withTooltips(Tooltips.of("kg.node.rt_tangent.option.space.tooltip"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, SPACES, GeometrySpaces::label)).build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String space = choice(GeometrySpaces.OPTION, GeometrySpaces.WORLD, SPACES);
        // In its own space the tangent is the basis' first axis, by construction — no basis needed.
        if (GeometrySpaces.TANGENT.equals(space)) {
            ctx.output("out", new ShaderExpr("vec3(1.0, 0.0, 0.0)", GlslType.VEC3));
            return;
        }
        ctx.output("out", ctx.tangentBasis(space).tangent());
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    /**
     * A flat quad shows one constant colour for a basis vector — the uv there has no rotation to follow, so
     * the thumbnail says nothing. Preview on the sphere instead, where the derived frame actually turns,
     * for the same reason {@code FresnelNode} does.
     */
    @Override
    protected String defaultPreviewContentKey() {
        return "sphere";
    }

    @Override
    public List<String> optionChoices(String optionId) {
        return GeometrySpaces.optionChoices(optionId);
    }

    @Override
    public String glslExample() {
        return """
                // fragment: the uv's cotangent frame
                vec3 T, B;
                kg_tangentFrameFromUv(N, pos, uv, T, B);
                // world
                out = mat3(IViewMat)
                    * mat3(ModelViewMat) * T;""";
    }
}
