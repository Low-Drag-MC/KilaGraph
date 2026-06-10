package com.lowdragmc.kilagraph.rendertype.nodes.fragment;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.NodeDisplayNames;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.BlockNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.ContextNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;

import java.util.List;

@NodeAttribute(name = "rt_fragment_stage", group = "rendertype_fragment", graphTypes = RenderTypeGraph.class)
public class FragmentStageNode extends ContextNode {
    @Override
    public Component getDisplayName() {
        return NodeDisplayNames.fromAttribute(this);
    }

    @Override
    public List<Class<? extends BlockNode>> getSupportBlocks() {
        return List.of(
                FragmentBaseColorBlock.class,
                FragmentAlphaBlock.class,
                FragmentEmissionBlock.class,
                FragmentAlphaDiscardBlock.class
        );
    }
}
