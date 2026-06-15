package com.lowdragmc.kilagraph.rendertype.nodes.math.derivative;

import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicUnaryFuncNode;

/**
 * Base for a screen-space derivative node ({@code dFdx}/{@code dFdy}/{@code fwidth}) over the dynamic
 * float-vector type. Derivatives only exist in the fragment stage, so these are {@link
 * StageAffinity#FRAGMENT_ONLY} — using one in the vertex stage is flagged by the compiler.
 */
public abstract class DerivativeNode extends DynamicUnaryFuncNode {
    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.FRAGMENT_ONLY;
    }
}
