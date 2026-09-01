package com.lowdragmc.kilagraph.rendertype.nodes.uv;

import com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips;
import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.kilagraph.rendertype.gui.ChoiceConfigurator;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.List;

/**
 * Unity's Parallax Occlusion Mapping: instead of {@link ParallaxMappingNode}'s single guess, it marches
 * along the tangent-space view ray until it crosses the height field, then interpolates the crossing point.
 * That actually finds where the eye meets the surface, so deep relief and grazing angles hold their shape
 * where plain parallax smears. Outputs the shifted uv — feed it to everything that samples the surface.
 *
 * <p>{@code steps} is a dropdown rather than an input port on purpose: as a compile-time constant the march
 * unrolls, and it keeps the cost visible — this node fetches up to {@code steps + 2} texels per fragment, so
 * 64 steps on a full-screen surface is a real bill. {@link StageAffinity#FRAGMENT_ONLY}.</p>
 */
@NodeAttribute(name = "rt_parallax_occlusion_mapping", group = "rendertype_uv", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ParallaxOcclusionMappingNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_parallax_occlusion_mapping.tooltip");
    }

    private static final List<String> STEPS = List.of("8", "16", "32", "64");

    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.FRAGMENT_ONLY;
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("steps", TypeHandles.STRING).withDefaultValue("16")
                .withTooltips(Tooltips.of("kg.node.rt_parallax_occlusion_mapping.option.steps.tooltip"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, STEPS)).build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("heightmap", RenderTypeGraphTypes.SAMPLER2D).withoutConfigurator();
        context.addInputPort("uv", RenderTypeGraphTypes.UV);
        context.addInputPort("amplitude", TypeHandles.FLOAT).withDefaultValue(0.05f);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC2);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr sampler = ctx.isConnected("heightmap") ? ctx.input("heightmap") : ctx.missingSampler();
        ShaderExpr viewTS = ctx.temp(GlslType.VEC3,
                "normalize(" + ctx.spaceToTangent("object", ctx.objectSpaceViewDir()).code() + ")");
        ctx.function(ParallaxGlsl.OCCLUSION_NAME, ParallaxGlsl.OCCLUSION);
        ctx.output("out", new ShaderExpr(ParallaxGlsl.OCCLUSION_NAME + "(" + sampler.code() + ", "
                + ctx.input("uv").code() + ", " + viewTS.code() + ", "
                + ctx.input("amplitude").code() + ", " + steps() + ")", GlslType.VEC2));
    }

    /** The march length as a GLSL int literal — a constant, so the loop unrolls. */
    private String steps() {
        INodeOption opt = getNodeOptionById("steps");
        Object raw = opt == null ? null : opt.tryGetValue(Object.class).result().orElse(null);
        return raw instanceof String s && STEPS.contains(s) ? s : "16";
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    @Override
    public List<String> optionChoices(String optionId) {
        return "steps".equals(optionId) ? STEPS : List.of();
    }

    @Override
    public String glslExample() {
        return """
                // march until the ray drops below the height
                for (int i = 0; i < steps; i++) {
                    if (layer >= depth) break;
                    uv -= delta;
                    depth = 1.0 - texture(hm, uv).r;
                    layer += 1.0 / float(steps);
                }
                // then interpolate the crossing""";
    }
}
