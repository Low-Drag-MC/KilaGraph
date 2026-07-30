package com.lowdragmc.kilagraph.rendertype.nodes.math.round;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicBinaryFuncNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/**
 * {@code step(edge, x)}: 0 where {@code x < edge} else 1, component-wise. The operands are labelled
 * {@code edge}/{@code x} — {@code a}/{@code b} gives no hint which side of the threshold is which — but
 * their port ids stay {@code a}/{@code b} so existing graphs keep their wires.
 */
@NodeAttribute(name = "rt_step", group = "rendertype_math/round", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class StepNode extends DynamicBinaryFuncNode {
    @Override
    protected String glslFunc() {
        return "step";
    }

    @Override
    protected String[] portLabels() {
        return new String[]{"edge", "x"};
    }
}
