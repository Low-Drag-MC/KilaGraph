package com.lowdragmc.kilagraph.rendertype.nodes.fragment;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.ColorTarget;
import com.lowdragmc.kilagraph.rendertype.compiler.FragmentOutputs;
import com.lowdragmc.kilagraph.rendertype.compiler.IFragmentOutputBlock;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderBlockNode;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.gui.ChoiceConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.UseWithContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;
import org.joml.Vector4f;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Writes an extra colour target (MRT) at location 1..7. The material binds its texture with
 * {@code RenderTypeGraphMaterial#setColorTarget}; without one the output is discarded.
 */
@UseWithContext(FragmentStageNode.class)
@NodeAttribute(name = "rt_fragment_color_target", group = "rendertype_fragment", graphTypes = RenderTypeGraph.class)
public class FragmentColorTargetBlock extends ShaderBlockNode implements IFragmentOutputBlock {

    public static final String OPTION_TARGET = "target";
    public static final String OPTION_FORMAT = "format";
    private static final List<String> TARGETS = IntStream.rangeClosed(1, ColorTarget.MAX_LOCATION)
            .mapToObj(String::valueOf).toList();
    private static final List<String> FORMATS = Arrays.stream(RenderTypeGraph.Settings.ColorFormat.values())
            .map(Enum::name).toList();

    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_fragment_color_target.tooltip");
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption(OPTION_TARGET, TypeHandles.STRING).withDefaultValue(TARGETS.getFirst())
                .withTooltips(Tooltips.of("kg.node.rt_fragment_color_target.option.target.tooltip"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, TARGETS))
                .build();
        context.addOption(OPTION_FORMAT, TypeHandles.STRING).withDefaultValue(FORMATS.getFirst())
                .withTooltips(Tooltips.of("kg.node.rt_fragment_color_target.option.format.tooltip"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, FORMATS))
                .build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);
        context.addInputPort("color", RenderTypeGraphTypes.VEC4).withDefaultValue(new Vector4f(0, 0, 0, 1));
    }

    @Override
    public void emitFragment(ShaderCompileContext ctx, FragmentOutputs out) {
        int location = parseTarget(ctx.option(OPTION_TARGET, String.class, TARGETS.getFirst()));
        if (out.colorTargets.containsKey(location)) {
            Kilagraph.LOGGER.warn("[KilaGraph] two Color Target blocks write target {}; the first wins", location);
            return;
        }
        var target = new ColorTarget(location, parseFormat(ctx.option(OPTION_FORMAT, String.class, FORMATS.getFirst())));
        out.colorTargets.put(location, new FragmentOutputs.ColorTargetWrite(target, ctx.input("color")));
    }

    @Override
    public List<String> optionChoices(String optionId) {
        return switch (optionId) {
            case OPTION_TARGET -> TARGETS;
            case OPTION_FORMAT -> FORMATS;
            default -> List.of();
        };
    }

    @Override
    public String glslExample() {
        return "layout(location = 0) out vec4 kg_outputs[2];\nkg_outputs[1] = color;";
    }

    private static int parseTarget(String value) {
        try {
            return Math.clamp(Integer.parseInt(value), 1, ColorTarget.MAX_LOCATION);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static RenderTypeGraph.Settings.ColorFormat parseFormat(String value) {
        try {
            return RenderTypeGraph.Settings.ColorFormat.valueOf(value);
        } catch (IllegalArgumentException e) {
            return RenderTypeGraph.Settings.ColorFormat.RGBA8;
        }
    }
}
