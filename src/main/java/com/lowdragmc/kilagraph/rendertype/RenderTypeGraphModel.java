package com.lowdragmc.kilagraph.rendertype;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.ChangeHint;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.GraphElementModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * The {@link RenderTypeGraph}'s graph model. Inherits the shader vec-assign rule from
 * {@link ShaderGraphModelBase}, and redirects inline subgraph creation to {@link ShaderFunctionGraph}
 * so "create subgraph from selection" yields a pure shader-function graph (no fixed stages / entity
 * shader init) instead of cloning the whole RenderTypeGraph.
 */
public class RenderTypeGraphModel extends ShaderGraphModelBase {
    public RenderTypeGraphModel(Graph graph) {
        super(graph);
    }

    /**
     * No-arg subgraph creation (used by {@code extractSelectionToLocalSubgraph} and the editor's
     * "create subgraph from selection") produces a {@link ShaderFunctionGraph}. Accepted because
     * {@link RenderTypeGraph#acceptsSubgraphGraph} opts into it.
     */
    @Override
    public CustomGraphModelImpl createLocalSubgraphInstance() {
        return createLocalSubgraphInstance(ShaderFunctionGraph.class);
    }

    /**
     * Every {@code deserialize} rebuilds the node models into fresh instances — including undo/redo,
     * which round-trip the whole model through NBT ({@code UndoableGraphCommand}) directly on this model
     * (not via the resource load that calls {@link RenderTypeGraph#restoreFixedStagesAfterDeserialize()}).
     * Re-resolve the graph's cached fixed vertex/fragment stage references here, the single point all
     * deserialize paths funnel through, so the whole-graph compiler walks the live stages. Otherwise it
     * keeps the old, now-orphaned stage refs and emits an empty (fully transparent) shader — e.g. the
     * graph-tool preview going blank after deleting a node and undoing.
     */
    @Override
    public void afterDeserialize() {
        super.afterDeserialize();
        if (getGraph() instanceof RenderTypeGraph renderTypeGraph) {
            renderTypeGraph.restoreFixedStagesAfterDeserialize();
        }
    }

    // --- Preview geometry persistence -------------------------------------------------------------
    // The chosen preview shape (KGPreviewContents key) for each node thumbnail and for the graph-tool
    // preview panel lives in this model's NBT so it survives both reopen and undo/redo (which round-trip
    // exactly this model — see the afterDeserialize note above). The runtime UI elements (NodeShaderPreview,
    // ShaderPreviewTool) are the ephemeral mirror; this map/string is the persisted source of truth,
    // mirroring how AbstractNodeModel.previewExpanded backs the runtime NodePreviewModel.

    /** Node UID → chosen {@link com.lowdragmc.kilagraph.rendertype.preview.KGPreviewContent} key. */
    private final Map<UUID, String> nodePreviewContentKeys = new HashMap<>();
    /** Chosen preview-content key for the graph-tool preview panel (null = its default). */
    @Nullable
    private String previewToolContentKey;

    @Nullable
    public String getNodePreviewContentKey(UUID nodeUid) {
        return nodePreviewContentKeys.get(nodeUid);
    }

    /**
     * Records a node thumbnail's chosen preview geometry. Registers a {@link ChangeHint#DATA} change for
     * the owning node so the choice is part of undo history and marks the graph dirty (mirrors
     * {@code AbstractNodeModel.setPreviewExpanded}). Entries are never pruned on node delete — undo
     * restores the node with the same UID and a surviving entry brings its shape back.
     */
    public void setNodePreviewContentKey(UUID nodeUid, @Nullable String key) {
        if (key == null) {
            if (nodePreviewContentKeys.remove(nodeUid) == null) return;
        } else if (key.equals(nodePreviewContentKeys.put(nodeUid, key))) {
            return; // unchanged
        }
        GraphElementModel node = getModel(nodeUid);
        if (node != null) {
            getCurrentGraphChangeDescription().addChangedModel(node, ChangeHint.DATA);
        }
    }

    @Nullable
    public String getPreviewToolContentKey() {
        return previewToolContentKey;
    }

    public void setPreviewToolContentKey(@Nullable String key) {
        if (Objects.equals(previewToolContentKey, key)) return;
        previewToolContentKey = key;
        setGraphObjectDirty();
    }

    @Override
    public Tag serializeAdditionalNBT(HolderLookup.Provider provider) {
        var tag = (CompoundTag) super.serializeAdditionalNBT(provider);
        if (!nodePreviewContentKeys.isEmpty()) {
            var previews = new CompoundTag();
            for (var entry : nodePreviewContentKeys.entrySet()) {
                if (entry.getValue() != null) {
                    previews.putString(entry.getKey().toString(), entry.getValue());
                }
            }
            if (!previews.isEmpty()) tag.put("nodePreviewContents", previews);
        }
        if (previewToolContentKey != null) {
            tag.putString("previewToolContent", previewToolContentKey);
        }
        return tag;
    }

    @Override
    public void deserializeAdditionalNBT(Tag tag, HolderLookup.Provider provider) {
        super.deserializeAdditionalNBT(tag, provider);
        // Clear first so a deserialize replaces (never merges) the persisted shapes.
        nodePreviewContentKeys.clear();
        previewToolContentKey = null;
        if (!(tag instanceof CompoundTag compound)) return;
        if (compound.contains("nodePreviewContents")) {
            var previews = compound.getCompound("nodePreviewContents");
            for (var key : previews.getAllKeys()) {
                var value = previews.getString(key);
                if (value.isEmpty()) continue;
                try {
                    nodePreviewContentKeys.put(UUID.fromString(key), value);
                } catch (IllegalArgumentException ignored) {
                    // skip a malformed/legacy key rather than failing the whole deserialize
                }
            }
        }
        if (compound.contains("previewToolContent")) {
            var value = compound.getString("previewToolContent");
            previewToolContentKey = value.isEmpty() ? null : value;
        }
    }
}
