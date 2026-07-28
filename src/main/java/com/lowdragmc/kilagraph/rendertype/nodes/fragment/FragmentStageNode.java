package com.lowdragmc.kilagraph.rendertype.nodes.fragment;

import com.lowdragmc.kilagraph.graph.util.NodeTooltipHelper;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.IShaderNodeDescription;
import com.lowdragmc.kilagraph.rendertype.compiler.NodeDisplayNames;
import com.lowdragmc.kilagraph.graph.util.NodeDescriptions;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.ContextNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

@NodeAttribute(name = "rt_fragment_stage", group = "rendertype_fragment", graphTypes = RenderTypeGraph.class)
public class FragmentStageNode extends ContextNode implements IShaderNodeDescription {
    @Override
    public void setImplementation(NodeModel nodeModel) {
        super.setImplementation(nodeModel);
        NodeTooltipHelper.apply(nodeModel, getNodeTooltip());
    }

    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_fragment_stage.tooltip");
    }

    @Override
    public Component getDisplayName() {
        return NodeDisplayNames.fromAttribute(this);
    }

    @Override
    @Nullable
    public UIElement createDescriptionUI() {
        return NodeDescriptions.build(this);
    }

    /** The tail of the generated {@code main()} — exactly how the four block outputs become fragColor. */
    @Override
    public String glslExample() {
        return """
                vec3 kg_baseColor = <Base Color>;
                float kg_alpha = <Alpha>;
                kg_baseColor += <Emission>;
                if (kg_alpha < <Cutoff>) discard;
                fragColor = vec4(kg_baseColor, kg_alpha);""";
    }
}
