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
 * Unity's Bitangent Vector node: the surface bitangent (vec3, unit length) in the space chosen by the
 * dropdown — the direction the texture's <b>+v</b> axis runs across the surface, i.e. the third leg of the
 * TBN frame alongside {@link TangentNode} and {@link NormalNode}. Derived, like the tangent, by
 * {@link ShaderCompileContext#tangentBasis(String)} — Minecraft's vertex formats carry no tangent attribute,
 * so the fragment stage recovers the frame from the mesh uv's screen-space derivatives and the vertex stage
 * falls back to a basis built from the normal alone.
 *
 * <p>Spaces: <b>object</b>/<b>view</b>/<b>world</b> (the same rotations the Normal node uses) and
 * <b>tangent</b> — the constant {@code (0,1,0)}, the basis' own second axis. The handedness is already baked
 * in: with a real per-vertex tangent it comes from that attribute's {@code w}, and with the derived frame it
 * comes from the uv layout itself, so mirrored uv islands come out correctly signed.</p>
 */
@NodeAttribute(name = "rt_bitangent", group = "rendertype_input", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class BitangentNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_bitangent.tooltip");
    }

    private static final List<String> SPACES = List.of("object", "world", "view", "tangent");

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("space", TypeHandles.STRING).withDefaultValue("world")
                .withTooltips(Tooltips.of("kg.node.rt_bitangent.option.space.tooltip"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, SPACES, BitangentNode::label)).build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String space = choice("space", "world", SPACES);
        // In its own space the bitangent is the basis' second axis, by construction — no basis needed.
        if ("tangent".equals(space)) {
            ctx.output("out", new ShaderExpr("vec3(0.0, 1.0, 0.0)", GlslType.VEC3));
            return;
        }
        ctx.output("out", ctx.tangentBasis(space).bitangent());
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    @Override
    public List<String> optionChoices(String optionId) {
        return "space".equals(optionId) ? SPACES : List.of();
    }

    @Override
    public String glslExample() {
        return """
                // fragment: the uv's cotangent frame
                vec3 T, B;
                kg_tangentFrameFromUv(N, pos, uv, T, B);
                // world
                out = mat3(IViewMat)
                    * mat3(ModelViewMat) * B;""";
    }

    private static String label(String space) {
        return switch (space) {
            case "object" -> "Object";
            case "view" -> "View";
            case "tangent" -> "Tangent";
            default -> "World";
        };
    }

    private String choice(String id, String def, List<String> valid) {
        INodeOption opt = getNodeOptionById(id);
        Object raw = opt == null ? null : opt.tryGetValue(Object.class).result().orElse(null);
        return raw instanceof String s && valid.contains(s) ? s : def;
    }
}
