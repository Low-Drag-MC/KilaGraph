package com.lowdragmc.kilagraph.rendertype.nodes.input;

import com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips;
import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.UvChannel;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.gui.ChoiceConfigurator;
import com.lowdragmc.kilagraph.graph.util.NodeTooltipHelper;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.List;

/**
 * Unity's UV node: outputs the mesh's uv for the channel chosen in the dropdown (UV0/UV1/UV2). UV0 is the
 * texture uv; UV1/UV2 are Minecraft's overlay/lightmap coords (cast to vec2). Stage-agnostic (default
 * {@link com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity#ANY}) — the raw vertex attribute in the
 * vertex shader, the interpolated varying in the fragment shader (via {@link ShaderCompileContext#meshUv}).
 * The per-node preview shows UV0 as the quad's uv gradient and UV1/UV2 as a flat colour (they're constant
 * per draw).
 */
@NodeAttribute(name = "rt_uv", group = "rendertype_input", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class UVNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_uv.tooltip");
    }


    private static final List<String> CHANNELS = List.of("uv0", "uv1", "uv2");

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("channel", TypeHandles.STRING).withDefaultValue("uv0")
                .withTooltips(Tooltips.of("kg.node.rt_uv.option.channel.tooltip"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, CHANNELS, String::toUpperCase)).build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("out", RenderTypeGraphTypes.VEC2);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.output("out", ctx.meshUv(channel()));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    private UvChannel channel() {
        INodeOption opt = getNodeOptionById("channel");
        Object raw = opt == null ? null : opt.tryGetValue(Object.class).result().orElse(null);
        if (raw instanceof String s) {
            for (UvChannel c : UvChannel.values()) {
                if (c.getSerializedName().equals(s)) return c;
            }
        }
        return UvChannel.UV0;
    }
}
