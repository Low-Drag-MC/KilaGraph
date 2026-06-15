package com.lowdragmc.kilagraph.rendertype.nodes.math.wave;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicUnaryNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** Sawtooth wave: {@code 2 * (a - floor(0.5 + a))}, a ramp in [-1, 1] with period 1, component-wise. */
@NodeAttribute(name = "rt_sawtooth_wave", group = "rendertype_math/wave", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class SawtoothWaveNode extends DynamicUnaryNode {
    @Override
    protected String emit(String a) {
        return "(2.0 * ((" + a + ") - floor(0.5 + (" + a + "))))";
    }
}
