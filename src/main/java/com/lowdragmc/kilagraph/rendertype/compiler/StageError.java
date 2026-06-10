package com.lowdragmc.kilagraph.rendertype.compiler;

import java.util.UUID;

/**
 * A stage-affinity violation found during compilation: a node was pulled into a stage its
 * {@link StageAffinity} forbids. Surfaced to the editor (preview panel / log) so the user can see
 * which node is misused, and used by the runtime to refuse building an invalid pipeline.
 */
public record StageError(UUID nodeUid, String nodeName, ShaderStage usedIn, StageAffinity affinity) {

    /** Human-readable description, e.g. "Normal: VERTEX_ONLY node used in FRAGMENT stage". */
    public String message() {
        return nodeName + ": " + affinity + " node used in " + usedIn + " stage";
    }
}
