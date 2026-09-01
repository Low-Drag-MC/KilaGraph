package com.lowdragmc.kilagraph.rendertype.nodes.artistic.normal;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.kilagraph.rendertype.gui.ChoiceConfigurator;
import com.lowdragmc.kilagraph.rendertype.nodes.artistic.ArtisticNode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.List;

/**
 * Unity's Normal From Height: builds a normal from a scalar height field via its screen-space derivatives —
 * {@code normalize(vec3(-strength * dFdx(in), -strength * dFdy(in), 1))}. Uses derivatives, so
 * {@link StageAffinity#FRAGMENT_ONLY}.
 *
 * <p>Unity's <b>Output Space</b> option is offered: <b>tangent</b> (the raw result above) or <b>world</b>,
 * which runs it through the surface's tangent basis ({@link ShaderCompileContext#tangentBasis(String)}) so it
 * can be lit directly instead of needing a Transform node wired on. The height slopes are measured in pixels
 * while the basis is anchored to the uv, so world mode inherits the same screen-space approximation the
 * tangent output already is — the bumpiness still varies with distance and zoom.</p>
 */
@NodeAttribute(name = "rt_normal_from_height", group = "rendertype_artistic/normal", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class NormalFromHeightNode extends ArtisticNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_normal_from_height.tooltip");
    }

    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.FRAGMENT_ONLY; // uses dFdx/dFdy
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("space", TypeHandles.STRING).withDefaultValue("tangent")
                .withTooltips(Tooltips.of("kg.node.rt_normal_from_height.option.space.tooltip"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, NormalSpaces.SPACES, NormalSpaces::label))
                .build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", TypeHandles.FLOAT);
        context.addInputPort("strength", TypeHandles.FLOAT).withDefaultValue(1f);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr in = ctx.temp(GlslType.FLOAT, ctx.input("in").code());
        String s = ctx.input("strength").code();
        ShaderExpr tangentSpace = new ShaderExpr("normalize(vec3(-" + s + " * dFdx(" + in.code() + "), -"
                + s + " * dFdy(" + in.code() + "), 1.0))", GlslType.VEC3);
        ctx.output("out", NormalSpaces.toChosenSpace(this, ctx, tangentSpace));
    }

    @Override
    public List<String> optionChoices(String optionId) {
        return NormalSpaces.optionChoices(optionId);
    }

    @Override
    public String glslExample() {
        return """
                vec3 n = normalize(vec3(
                    -strength * dFdx(in),
                    -strength * dFdy(in), 1.0));
                // space = world
                out = T * n.x + B * n.y + N * n.z;""";
    }
}
