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
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class RenderTypeGraphResource extends GraphResource<RenderTypeGraph> {
    private static final String GRAPH_TAG = "graph";
    private static final String SETTINGS_TAG = "settings";

    public static final RenderTypeGraphResource INSTANCE = new RenderTypeGraphResource();

    private RenderTypeGraphResource() {}

    @Override
    public RenderTypeGraph createGraph() {
        return new RenderTypeGraph();
    }

    @Override
    public Supplier<? extends GraphView> getGraphViewFactory() {
        return RenderTypeGraphView::new;
    }

    public CompoundTag serializeGraph(RenderTypeGraph graph) {
        var root = new CompoundTag();
        var output = TagValueOutput.createWithContext(ProblemReporter.Collector.DISCARDING, Platform.getFrozenRegistry());
        graph.graphModel.serialize(output);
        root.put(GRAPH_TAG, output.buildResult());
        root.put(SETTINGS_TAG, serializeSettings(graph.getSettings()));
        return root;
    }

    public RenderTypeGraph deserializeGraph(CompoundTag tag) {
        return deserializeGraph(tag, null);
    }

    public RenderTypeGraph deserializeGraph(CompoundTag tag, @Nullable IGraphReferenceResolver resolver) {
        var graph = createGraph();
        graph.graphModel.setReferenceResolver(resolver);
        var graphTag = tag.get(GRAPH_TAG) instanceof CompoundTag compound ? compound : tag;
        graph.graphModel.deserialize(TagValueInput.create(ProblemReporter.Collector.DISCARDING,
                Platform.getFrozenRegistry(), graphTag));
        graph.graphModel.setReferenceResolver(resolver);
        if (tag.get(SETTINGS_TAG) instanceof CompoundTag settingsTag) {
            graph.setSettings(deserializeSettings(settingsTag));
        }
        graph.restoreFixedStagesAfterDeserialize();
        return graph;
    }

    private CompoundTag serializeSettings(RenderTypeGraph.Settings settings) {
        var tag = new CompoundTag();
        tag.putString("vertexFormatPreset", settings.vertexFormatPreset().name());
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
                readEnum(tag, "vertexFormatPreset", RenderTypeGraph.Settings.VertexFormatPreset.class,
                        defaults.vertexFormatPreset()),
                readEnum(tag, "vertexFormatMode", RenderTypeGraph.Settings.VertexFormatMode.class,
                        defaults.vertexFormatMode()),
                readEnum(tag, "blend", RenderTypeGraph.Settings.BlendMode.class, defaults.blend()),
                readEnum(tag, "depthTest", RenderTypeGraph.Settings.DepthTest.class, defaults.depthTest()),
                tag.getBoolean("depthWrite").orElse(defaults.depthWrite()),
                tag.getBoolean("cull").orElse(defaults.cull()),
                readEnum(tag, "outputTarget", RenderTypeGraph.Settings.OutputTarget.class, defaults.outputTarget()),
                tag.getBoolean("affectsOutline").orElse(defaults.affectsOutline()),
                tag.getBoolean("sortOnUpload").orElse(defaults.sortOnUpload())
        );
    }

    private static <E extends Enum<E>> E readEnum(CompoundTag tag, String key, Class<E> enumClass, E fallback) {
        var name = tag.getString(key).orElse(fallback.name());
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
