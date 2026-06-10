package com.lowdragmc.kilagraph.rendertype.gui;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
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

import java.util.Arrays;
import java.util.Locale;

public class RenderTypeSettingsTool extends UIElement implements IGraphTool {
    private final RenderTypeGraphView graphView;

    private final Selector<RenderTypeGraph.Settings.VertexFormatPreset> vertexFormatPreset;
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

        vertexFormatPreset = enumSelector(RenderTypeGraph.Settings.VertexFormatPreset.class);
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
                row("Vertex Format", vertexFormatPreset),
                row("Primitive", vertexFormatMode),
                row("Blend", blend),
                row("Depth Test", depthTest),
                row("Target", outputTarget),
                row("Depth Write", depthWrite),
                row("Cull", cull),
                row("Outline", affectsOutline),
                row("Sort Upload", sortOnUpload)
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
        return Component.literal("RenderType");
    }

    public void refreshFromGraph() {
        var graph = graphView.getRenderTypeGraph();
        var settings = graph == null ? RenderTypeGraph.Settings.defaults() : graph.getSettings();
        vertexFormatPreset.setSelected(settings.vertexFormatPreset(), false);
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

    private void applyControlsToGraph() {
        var graph = graphView.getRenderTypeGraph();
        if (graph == null) return;
        var defaults = RenderTypeGraph.Settings.defaults();
        graph.setSettings(new RenderTypeGraph.Settings(
                valueOr(vertexFormatPreset, defaults.vertexFormatPreset()),
                valueOr(vertexFormatMode, defaults.vertexFormatMode()),
                valueOr(blend, defaults.blend()),
                valueOr(depthTest, defaults.depthTest()),
                depthWrite.isOn(),
                cull.isOn(),
                valueOr(outputTarget, defaults.outputTarget()),
                affectsOutline.isOn(),
                sortOnUpload.isOn()
        ));
    }

    private <E extends Enum<E>> Selector<E> enumSelector(Class<E> enumClass) {
        var selector = new Selector<E>()
                .setCandidates(Arrays.asList(enumClass.getEnumConstants()))
                .setCandidateUIProvider(UIElementProvider.text(value ->
                        Component.literal(value == null ? "---" : enumTitle(value))))
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

    private UIElement row(String label, UIElement control) {
        var row = new UIElement();
        row.addClass("__rendertype-settings-row__");
        Style.defaultPipeline(row.getLayout(), l -> l
                .widthPercent(100)
                .height(16)
                .gapAll(3)
                .alignItems(AlignItems.CENTER)
                .flexDirection(FlexDirection.ROW));

        var title = new Label();
        title.setText(label);
        Style.defaultPipeline(title.getLayout(), l -> l.width(62).heightPercent(100));
        row.addChildren(title, control);
        return row;
    }

    private static <E extends Enum<E>> E valueOr(Selector<E> selector, E fallback) {
        var value = selector.getValue();
        return value == null ? fallback : value;
    }

    private static String enumTitle(Enum<?> value) {
        var words = value.name().toLowerCase(Locale.ROOT).split("_");
        var builder = new StringBuilder();
        for (var word : words) {
            if (word.isEmpty()) continue;
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }
}
