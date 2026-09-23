package com.lowdragmc.kilagraph.rendertype.nodes.input.vertex;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.InstanceAttribute;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.gui.ChoiceConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Reads a named per-instance value (vertex binding 1, advancing once per instance) — the data an instanced
 * draw ({@code RenderTypeGraphMaterial#drawInstanced}) supplies per copy. Usable in both stages; the fragment
 * stage reads it through a flat varying. A non-instanced draw sees zero (identity for {@code mat4}).
 */
@NodeAttribute(name = "rt_instance_data", group = "rendertype_input/vertex", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class InstanceDataNode extends ShaderNode {

    public static final String OPTION_NAME = "name";
    public static final String OPTION_TYPE = "type";
    private static final List<String> TYPES = Arrays.stream(InstanceAttribute.Type.values()).map(Enum::name).toList();

    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_instance_data.tooltip");
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption(OPTION_NAME, TypeHandles.STRING).withDefaultValue("Data")
                .withTooltips(Tooltips.of("kg.node.rt_instance_data.option.name.tooltip"))
                .build();
        context.addOption(OPTION_TYPE, TypeHandles.STRING).withDefaultValue(InstanceAttribute.Type.VEC4.name())
                .withTooltips(Tooltips.of("kg.node.rt_instance_data.option.type.tooltip"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, TYPES, s -> s.toLowerCase(Locale.ROOT)))
                .build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("out", portType(type()));
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.output("out", ctx.instanceData(ctx.option(OPTION_NAME, String.class, "Data"), type()));
    }

    @Override
    public List<String> optionChoices(String optionId) {
        return OPTION_TYPE.equals(optionId) ? TYPES : List.of();
    }

    @Override
    public String glslExample() {
        return """
                // vertex binding 1, one value per instance
                in vec4 kg_inst_Data;
                out = kg_inst_Data;""";
    }

    private InstanceAttribute.Type type() {
        INodeOption option = getNodeOptionById(OPTION_TYPE);
        Object raw = option == null ? null : option.tryGetValue(Object.class).result().orElse(null);
        try {
            return raw instanceof String s ? InstanceAttribute.Type.valueOf(s) : InstanceAttribute.Type.VEC4;
        } catch (IllegalArgumentException e) {
            return InstanceAttribute.Type.VEC4;
        }
    }

    private static TypeHandle portType(InstanceAttribute.Type type) {
        return switch (type) {
            case FLOAT -> TypeHandles.FLOAT;
            case VEC2 -> RenderTypeGraphTypes.VEC2;
            case VEC3 -> RenderTypeGraphTypes.VEC3;
            case VEC4 -> RenderTypeGraphTypes.VEC4;
            case INT -> TypeHandles.INT;
            case MAT4 -> RenderTypeGraphTypes.MAT4;
        };
    }
}
