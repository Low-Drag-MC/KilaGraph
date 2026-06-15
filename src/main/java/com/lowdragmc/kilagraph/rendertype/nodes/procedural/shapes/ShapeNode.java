package com.lowdragmc.kilagraph.rendertype.nodes.procedural.shapes;

import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.kilagraph.rendertype.nodes.procedural.ProceduralNode;

/**
 * Base for the procedural shape masks (Ellipse / Polygon / Rectangle / Rounded …). Each computes a
 * signed/normalised distance field over the uv and antialiases its edge with {@code fwidth} — a
 * screen-space derivative that only exists in the fragment stage, so these are
 * {@link StageAffinity#FRAGMENT_ONLY}.
 */
public abstract class ShapeNode extends ProceduralNode {
    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.FRAGMENT_ONLY;
    }
}
