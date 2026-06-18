package com.lowdragmc.kilagraph.rendertype.gui;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.format.KGVertexElement;
import com.lowdragmc.kilagraph.rendertype.format.KGVertexElements;
import com.lowdragmc.kilagraph.rendertype.format.VertexFormatPresets;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.IGraphTool;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class RenderTypeSettingsTool extends UIElement implements IGraphTool {
    /** Position is mandatory: {@code VertexConsumer.addVertex} always writes it, so it can't be removed. */
    private static final String POSITION = "position";

    private final RenderTypeGraphView graphView;

    /** Live working set of included vertex-format element keys (order is irrelevant — see Settings). */
    private final List<String> elements = new ArrayList<>();
    private final Selector<String> presetSelector;

    private final Selector<RenderTypeGraph.Settings.VertexFormatMode> vertexFormatMode;
    private final Selector<RenderTypeGraph.Settings.BlendMode> blend;
    private final Selector<RenderTypeGraph.Settings.DepthTest> depthTest;
    private final Selector<RenderTypeGraph.Settings.OutputTarget> outputTarget;
    private final Toggle depthWrite;
    private final Toggle cull;
    private final Toggle affectsOutline;
    private final Toggle sortOnUpload;

    public RenderTypeSettingsTool(RenderTypeGraphView graphView) {
        this.graphView = graphView;
        addClass("__rendertype-settings-tool__");
        Style.defaultPipeline(getLayout(), l -> l.widthPercent(100).heightPercent(100));
        Style.defaultPipeline(getStyle(), s -> s.backgroundTexture(IGuiTexture.EMPTY));

        var graph = graphView.getRenderTypeGraph();
        elements.addAll((graph == null ? RenderTypeGraph.Settings.defaults() : graph.getSettings()).vertexFormatElements());

        // Vertex format: a preset quick-fill + a collapsible checklist (one toggle per registered element).
        presetSelector = new Selector<String>()
                .setCandidates(new ArrayList<>(VertexFormatPresets.ALL.keySet()))
                .setCandidateUIProvider(UIElementProvider.text(value ->
                        value == null ? Component.translatable("rendertypegraph.settings.preset.placeholder") : Component.literal(value)))
                .selectorStyle(style -> style.closeAfterSelect(true).maxItemCount(12).showOverlay(true));
        Style.defaultPipeline(presetSelector.getLayout(), l -> l.height(14).flex(1));
        presetSelector.setOnValueChanged(name -> {
            if (name == null) return;
            var preset = VertexFormatPresets.ALL.get(name);
            if (preset == null) return;
            presetSelector.setValue(null, false); // let the same preset be re-applied later
            elements.clear();
            elements.addAll(preset);
            applyControlsToGraph(); // the checklist toggles re-read `elements` on their next tick (forceUpdate)
        });

        // One on/off toggle per registered element; checked = included. Position is shown but locked on.
        var formatGroup = new ConfiguratorGroup("rendertypegraph.settings.vertex_format", false);
        for (KGVertexElement element : KGVertexElements.all()) {
            String key = element.key();
            var configurator = new BooleanConfigurator(
                    element.attribName(),
                    () -> elements.contains(key),
                    on -> setElement(key, on),
                    elements.contains(key) || key.equals(POSITION),
                    true); // forceUpdate: reflect preset fills / graph reloads live
            formatGroup.addConfigurator(configurator);
        }

        vertexFormatMode = enumSelector(RenderTypeGraph.Settings.VertexFormatMode.class);
        blend = enumSelector(RenderTypeGraph.Settings.BlendMode.class);
        depthTest = enumSelector(RenderTypeGraph.Settings.DepthTest.class);
        outputTarget = enumSelector(RenderTypeGraph.Settings.OutputTarget.class);
        depthWrite = toggle();
        cull = toggle();
        affectsOutline = toggle();
        sortOnUpload = toggle();

        // Rows live in a content column inside a scroller so the panel never overflows its dock.
        var content = new UIElement();
        Style.defaultPipeline(content.getLayout(), l -> l
                .widthPercent(100)
                .paddingAll(4)
                .gapAll(3)
                .flexDirection(FlexDirection.COLUMN));
        content.addChildren(
                row("rendertypegraph.settings.preset", presetSelector),
                formatGroup,
                row("rendertypegraph.settings.primitive", vertexFormatMode),
                row("rendertypegraph.settings.blend", blend),
                row("rendertypegraph.settings.depth_test", depthTest),
                row("rendertypegraph.settings.target", outputTarget),
                row("rendertypegraph.settings.depth_write", depthWrite),
                row("rendertypegraph.settings.cull", cull),
                row("rendertypegraph.settings.outline", affectsOutline),
                row("rendertypegraph.settings.sort_upload", sortOnUpload)
        );

        // Scroller styled like Blackboard's: fill the panel, no viewport padding, empty background.
        var scrollerView = new ScrollerView();
        scrollerView.addClass("__rendertype-settings-scroller__");
        Style.defaultPipeline(scrollerView.getLayout(), l -> l.widthPercent(100).heightPercent(100));
        Style.defaultPipeline(scrollerView.viewPort.getLayout(), l -> l.paddingAll(0));
        scrollerView.viewPort.getStyle().background(IGuiTexture.EMPTY);
        scrollerView.addScrollViewChild(content);
        addChild(scrollerView);

        refreshFromGraph();
    }

    @Override
    public Component getTitle() {
        return Component.translatable("rendertypegraph.settings.title");
    }

    public void refreshFromGraph() {
        var graph = graphView.getRenderTypeGraph();
        var settings = graph == null ? RenderTypeGraph.Settings.defaults() : graph.getSettings();
        elements.clear();
        elements.addAll(settings.vertexFormatElements()); // the checklist re-reads it via forceUpdate
        vertexFormatMode.setSelected(settings.vertexFormatMode(), false);
        blend.setSelected(settings.blend(), false);
        depthTest.setSelected(settings.depthTest(), false);
        outputTarget.setSelected(settings.outputTarget(), false);
        depthWrite.setOn(settings.depthWrite(), false);
        cull.setOn(settings.cull(), false);
        affectsOutline.setOn(settings.affectsOutline(), false);
        sortOnUpload.setOn(settings.sortOnUpload(), false);
    }

    public void applySettings(RenderTypeGraph.Settings settings) {
        var graph = graphView.getRenderTypeGraph();
        if (graph == null || settings == null) return;
        graph.setSettings(settings);
        refreshFromGraph();
    }

    /** Include/exclude an element; Position can't be excluded (addVertex always writes it). */
    private void setElement(String key, boolean on) {
        if (!on && key.equals(POSITION)) return;
        boolean changed = on ? (!elements.contains(key) && elements.add(key)) : elements.remove(key);
        if (changed) applyControlsToGraph();
    }

    private void applyControlsToGraph() {
        var graph = graphView.getRenderTypeGraph();
        if (graph == null) return;
        var defaults = RenderTypeGraph.Settings.defaults();
        graph.setSettings(new RenderTypeGraph.Settings(
                elements.isEmpty() ? defaults.vertexFormatElements() : List.copyOf(elements),
                valueOr(vertexFormatMode, defaults.vertexFormatMode()),
                valueOr(blend, defaults.blend()),
                valueOr(depthTest, defaults.depthTest()),
                depthWrite.isOn(),
                cull.isOn(),
                valueOr(outputTarget, defaults.outputTarget()),
                affectsOutline.isOn(),
                sortOnUpload.isOn()
        ));
        // Settings edits don't route through the model changeset, so re-run the editor's diagnostic hook
        // (validateVertexFormat) to refresh the footer — e.g. warn when removing an element a default uses.
        graphView.refreshGraphLogger();
    }

    // ---- shared widgets ----------------------------------------------------------------------

    private <E extends Enum<E>> Selector<E> enumSelector(Class<E> enumClass) {
        var selector = new Selector<E>()
                .setCandidates(Arrays.asList(enumClass.getEnumConstants()))
                .setCandidateUIProvider(UIElementProvider.text(value ->
                        value == null
                                ? Component.translatable("rendertypegraph.settings.none")
                                : Component.translatable(enumTranslationKey(value))))
                .selectorStyle(style -> style
                        .closeAfterSelect(true)
                        .maxItemCount(12)
                        .showOverlay(true));
        Style.defaultPipeline(selector.getLayout(), l -> l.height(14).flex(1));
        selector.setOnValueChanged(value -> applyControlsToGraph());
        return selector;
    }

    private Toggle toggle() {
        var toggle = new Toggle().noText().setOnToggleChanged(value -> applyControlsToGraph());
        Style.defaultPipeline(toggle.getLayout(), l -> l.width(14).height(14));
        Style.defaultPipeline(toggle.getToggleStyle(), s -> s
                .baseTexture(Sprites.BORDER1_RT1_DARK)
                .hoverTexture(Sprites.BORDER1_RT1));
        return toggle;
    }

    private UIElement row(String labelKey, UIElement control) {
        var row = new UIElement();
        row.addClass("__rendertype-settings-row__");
        Style.defaultPipeline(row.getLayout(), l -> l
                .widthPercent(100)
                .height(16)
                .gapAll(3)
                .alignItems(AlignItems.CENTER)
                .flexDirection(FlexDirection.ROW));

        var title = new Label();
        title.setValue(Component.translatable(labelKey));
        Style.defaultPipeline(title.getLayout(), l -> l.width(62).heightPercent(100));
        row.addChildren(title, control);
        return row;
    }

    private static <E extends Enum<E>> E valueOr(Selector<E> selector, E fallback) {
        var value = selector.getValue();
        return value == null ? fallback : value;
    }

    private static String enumTranslationKey(Enum<?> value) {
        Class<?> enumClass = value.getDeclaringClass();
        String group;
        if (enumClass == RenderTypeGraph.Settings.VertexFormatMode.class) {
            group = "vertex_format_mode";
        } else if (enumClass == RenderTypeGraph.Settings.BlendMode.class) {
            group = "blend_mode";
        } else if (enumClass == RenderTypeGraph.Settings.DepthTest.class) {
            group = "depth_test";
        } else if (enumClass == RenderTypeGraph.Settings.OutputTarget.class) {
            group = "output_target";
        } else {
            group = enumClass.getSimpleName().toLowerCase(Locale.ROOT);
        }
        return "rendertypegraph.settings." + group + "." + value.name().toLowerCase(Locale.ROOT);
    }
}
