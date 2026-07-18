package com.lowdragmc.kilagraph.editor;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.resource.IResourceProvider;
import com.lowdragmc.lowdraglib2.editor.ui.resource.ResourceProviderContainer;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.gui.RenderTypeGraphView;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.IGraphReferenceResolver;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.GraphResource;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphView;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class RenderTypeGraphResource extends GraphResource<RenderTypeGraph> {
    private static final String GRAPH_TAG = "graph";
    private static final String SETTINGS_TAG = "settings";

    public static final RenderTypeGraphResource INSTANCE = new RenderTypeGraphResource();

    protected RenderTypeGraphResource() {}

    @Override
    public RenderTypeGraph createGraph() {
        return new RenderTypeGraph();
    }

    /** The stored form is the wrapped {@code {graph, settings}} tag — every consumer (editor
     *  container, cross-library resolvers, runtimes) goes through this pair. */
    @Override
    public CompoundTag serializeGraphResource(RenderTypeGraph graph) {
        return serializeGraph(graph);
    }

    @Override
    public RenderTypeGraph deserializeGraphResource(CompoundTag tag,
            @Nullable com.lowdragmc.lowdraglib2.nodegraphtookit.editor.IGraphReferenceResolver resolver) {
        return deserializeGraph(tag, resolver);
    }

    @Override
    public Supplier<? extends GraphView> getGraphViewFactory() {
        return RenderTypeGraphView::new;
    }

    public CompoundTag serializeGraph(RenderTypeGraph graph) {
        var root = new CompoundTag();
        // 1.21.1: MC has no TagValueInput/Output (added in 1.21.5); LDLib2-1.21's graph model serializes
        // directly to NBT via serializeNBT(HolderLookup.Provider) — the canonical GraphView/undo pattern.
        root.put(GRAPH_TAG, graph.graphModel.serializeNBT(Platform.getFrozenRegistry()));
        root.put(SETTINGS_TAG, serializeSettings(graph.getSettings()));
        return root;
    }

    public RenderTypeGraph deserializeGraph(CompoundTag tag) {
        return deserializeGraph(tag, null);
    }

    /**
     * The empty (uninitialized) graph instance {@link #deserializeGraph} loads into. Subclass resources
     * override this so a load produces their graph type.
     */
    protected RenderTypeGraph newGraphForLoad() {
        return new RenderTypeGraph(false);
    }

    public RenderTypeGraph deserializeGraph(CompoundTag tag, @Nullable IGraphReferenceResolver resolver) {
        // Start from an empty graph (no default node network): deserialize below clears and reloads
        // nodeModels, so building the default graph here would be wasted work and would leave the model's
        // getNodes() cache primed with stale default nodes. restoreFixedStagesAfterDeserialize() re-ensures
        // the fixed stages after the load.
        var graph = newGraphForLoad();
        graph.graphModel.setReferenceResolver(resolver);
        var graphTag = tag.get(GRAPH_TAG) instanceof CompoundTag compound ? compound : tag;
        graph.graphModel.deserializeNBT(Platform.getFrozenRegistry(), graphTag);
        graph.graphModel.setReferenceResolver(resolver);
        if (tag.get(SETTINGS_TAG) instanceof CompoundTag settingsTag) {
            graph.setSettings(deserializeSettings(settingsTag));
        }
        graph.restoreFixedStagesAfterDeserialize();
        return graph;
    }

    private CompoundTag serializeSettings(RenderTypeGraph.Settings settings) {
        var tag = new CompoundTag();
        // Comma-joined element keys (keys are simple identifiers, never contain commas).
        tag.putString("vertexFormatElements", String.join(",", settings.vertexFormatElements()));
        tag.putString("vertexFormatMode", settings.vertexFormatMode().name());
        tag.putString("blend", settings.blend().name());
        tag.putString("depthTest", settings.depthTest().name());
        tag.putBoolean("depthWrite", settings.depthWrite());
        tag.putBoolean("cull", settings.cull());
        tag.putString("outputTarget", settings.outputTarget().name());
        tag.putBoolean("affectsOutline", settings.affectsOutline());
        tag.putBoolean("sortOnUpload", settings.sortOnUpload());
        return tag;
    }

    private RenderTypeGraph.Settings deserializeSettings(CompoundTag tag) {
        var defaults = RenderTypeGraph.Settings.defaults();
        return new RenderTypeGraph.Settings(
                readVertexFormatElements(tag, defaults.vertexFormatElements()),
                readEnum(tag, "vertexFormatMode", RenderTypeGraph.Settings.VertexFormatMode.class,
                        defaults.vertexFormatMode()),
                readEnum(tag, "blend", RenderTypeGraph.Settings.BlendMode.class, defaults.blend()),
                readEnum(tag, "depthTest", RenderTypeGraph.Settings.DepthTest.class, defaults.depthTest()),
                readBool(tag, "depthWrite", defaults.depthWrite()),
                readBool(tag, "cull", defaults.cull()),
                readEnum(tag, "outputTarget", RenderTypeGraph.Settings.OutputTarget.class, defaults.outputTarget()),
                readBool(tag, "affectsOutline", defaults.affectsOutline()),
                readBool(tag, "sortOnUpload", defaults.sortOnUpload())
        );
    }

    /**
     * Read the composed vertex-format element keys, upgrading saves from the old single
     * {@code vertexFormatPreset} enum string to the equivalent preset key list when needed.
     */
    private static java.util.List<String> readVertexFormatElements(CompoundTag tag, java.util.List<String> fallback) {
        var joined = tag.getString("vertexFormatElements");
        if (!joined.isBlank()) {
            var keys = new java.util.ArrayList<String>();
            for (var part : joined.split(",")) {
                if (!part.isBlank()) keys.add(part.trim());
            }
            if (!keys.isEmpty()) return keys;
        }
        // Legacy: map the pre-refactor VertexFormatPreset enum name to its element list.
        var legacy = tag.contains("vertexFormatPreset") ? tag.getString("vertexFormatPreset") : null;
        if (legacy != null) {
            return switch (legacy) {
                case "BLOCK" -> com.lowdragmc.kilagraph.rendertype.format.VertexFormatPresets.BLOCK;
                case "POSITION_COLOR_TEX" -> com.lowdragmc.kilagraph.rendertype.format.VertexFormatPresets.POSITION_COLOR_TEX;
                default -> com.lowdragmc.kilagraph.rendertype.format.VertexFormatPresets.ENTITY; // ENTITY, CUSTOM, unknown
            };
        }
        return fallback;
    }

    private static boolean readBool(CompoundTag tag, String key, boolean fallback) {
        return tag.contains(key) ? tag.getBoolean(key) : fallback;
    }

    private static <E extends Enum<E>> E readEnum(CompoundTag tag, String key, Class<E> enumClass, E fallback) {
        var name = tag.contains(key) ? tag.getString(key) : fallback.name();
        try {
            return Enum.valueOf(enumClass, name);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    @Override
    public ResourceProviderContainer<CompoundTag> createResourceProviderContainer(IResourceProvider<CompoundTag> provider) {
        return new RenderTypeGraphResourceProviderContainer(this, provider);
    }

    @Override
    public IGuiTexture getIcon() {
        return Icons.WIDGET_CUSTOM;
    }

    @Override
    public String getName() {
        return "rendertype";
    }
}
