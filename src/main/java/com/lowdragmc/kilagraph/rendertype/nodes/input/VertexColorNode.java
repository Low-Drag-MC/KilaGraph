package com.lowdragmc.kilagraph.rendertype.nodes.input;

import com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips;
import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.kilagraph.rendertype.gui.ChoiceConfigurator;
import com.lowdragmc.kilagraph.graph.util.NodeTooltipHelper;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.List;

/**
 * Outputs the mesh's vertex colour (vec4). Stage-agnostic (default {@link StageAffinity#ANY}) — computed from
 * the raw vertex attributes in the vertex shader, or read as an interpolated varying in the fragment shader.
 * The {@code mode} dropdown picks how the colour is lit:
 * <ul>
 *   <li>{@code mix_light} — vanilla per-vertex diffuse ({@code minecraft_mix_light}); needs
 *       {@code Light0/1_Direction}, {@code Normal}, {@code Color} (the entity default).</li>
 *   <li>{@code color} — the raw, unlit {@code Color} attribute.</li>
 *   <li>{@code block} — {@code Color * sample_lightmap(Sampler2, UV2)} like vanilla {@code block.vsh};
 *       baked lightmap lighting, no {@code Normal} needed.</li>
 * </ul>
 */
@NodeAttribute(name = "rt_vertex_color", group = "rendertype_input", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class VertexColorNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_vertex_color.tooltip");
    }


    public static final String MODE_MIX_LIGHT = "mix_light";
    public static final String MODE_COLOR = "color";
    public static final String MODE_BLOCK = "block";
    private static final List<String> MODES = List.of(MODE_MIX_LIGHT, MODE_COLOR, MODE_BLOCK);

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("mode", TypeHandles.STRING).withDefaultValue(MODE_MIX_LIGHT)
                .withTooltips(Tooltips.of(
                        "kg.node.rt_vertex_color.option.mode.tooltip.mix_light",
                        "kg.node.rt_vertex_color.option.mode.tooltip.color",
                        "kg.node.rt_vertex_color.option.mode.tooltip.block"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, MODES, VertexColorNode::label)).build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("out", RenderTypeGraphTypes.VEC4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.output("out", switch (mode()) {
            case MODE_COLOR -> ctx.meshColor();
            case MODE_BLOCK -> ctx.blockVertexColor();
            default -> ctx.litVertexColor();
        });
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    @Override
    public List<String> optionChoices(String optionId) {
        return "mode".equals(optionId) ? MODES : List.of();
    }

    @Override
    public String glslExample() {
        return """
                // color
                out = Color;
                // block
                out = Color * minecraft_sample_lightmap(
                    Sampler2, UV2);
                // mix_light
                out = minecraft_mix_light(Light0_Direction,
                    Light1_Direction, Normal, Color);""";
    }

    private static String label(String mode) {
        return switch (mode) {
            case MODE_COLOR -> "Color";
            case MODE_BLOCK -> "Block";
            default -> "Mix Light";
        };
    }

    private String mode() {
        INodeOption opt = getNodeOptionById("mode");
        Object raw = opt == null ? null : opt.tryGetValue(Object.class).result().orElse(null);
        return raw instanceof String s && MODES.contains(s) ? s : MODE_MIX_LIGHT;
    }
}
