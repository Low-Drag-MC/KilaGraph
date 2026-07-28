package com.lowdragmc.kilagraph.graph.util;

import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphView;

/**
 * The editor-wide tweaks every KilaGraph graph view wants, whichever graph it is showing.
 *
 * <p>Right now that is just the item library's description panel: KilaGraph documents its nodes with
 * multi-paragraph articles, port tables and (in the shader graph) GLSL cards, and the library's default
 * 150px panel wraps those into unreadable ribbons. See {@link NodeDescriptions}.</p>
 */
public class KGGraphView extends GraphView {

    /** Wide enough for a ~48-character code line at the description panel's font size. */
    public static final float DESCRIPTION_WIDTH = 220;

    public KGGraphView() {
        super();
        itemLibrary.setDescriptionWidth(DESCRIPTION_WIDTH);
    }
}
