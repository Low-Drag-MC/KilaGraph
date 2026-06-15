package com.lowdragmc.kilagraph.rendertype.nodes.math.wave;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicUnaryNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** Square wave: {@code 1 - 2 * round(fract(a))}, alternating -1/1 with period 1, component-wise. */
@NodeAttribute(name = "rt_square_wave", group = "rendertype_math/wave", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class SquareWaveNode extends DynamicUnaryNode {
    @Override
    protected String emit(String a) {
        return "(1.0 - 2.0 * round(fract(" + a + ")))";
    }
}
