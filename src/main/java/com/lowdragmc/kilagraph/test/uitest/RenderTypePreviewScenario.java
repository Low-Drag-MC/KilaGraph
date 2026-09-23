package com.lowdragmc.kilagraph.test.uitest;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.format.KGVertexFormat;
import com.lowdragmc.kilagraph.rendertype.gui.RenderTypeGraphView;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeFactory;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphPanel;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import com.mojang.blaze3d.PrimitiveTopology;

/** The preview panel compiles the default graph into a live pipeline and draws it without a backend error,
 *  then again after an edit. Run it on both backends ({@code -PgraphicsBackend=vulkan}, headless). */
@LDLRegisterClient(name = "kg_rendertype_preview", group = "kilagraph", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public class RenderTypePreviewScenario implements UIScenario {

    @Override
    public void configure(ScenarioOptions options) {
        options.defaultSettleMs(60).tags("kilagraph", "rendertype", "visual").guiScale(2);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openModularUI("a RenderType graph view over the default graph", RenderTypePreviewScenario::buildUI)
                .awaitScreen(ModularUIScreen.class)
                .awaitModularUI()
                .awaitElement("#graph")
                .checkExists(".__rendertype-preview-tool__")

                // Panels remember their collapsed state across sessions.
                .step("expand the preview panel", ctx -> panel(ctx).setCollapsed(false))
                .settleMs(200)
                .check("the preview panel is open", ctx -> !panel(ctx).isCollapsed())
                .check("the preview has a drawable rect", ctx -> {
                    var bounds = ctx.el(".__rendertype-preview-tool__").bounds();
                    return bounds.width() > 8 && bounds.height() > 8;
                })

                .group("the default graph compiles into a live pipeline", g -> g
                        // The panel compiles on its first render, so this is a wait, not an assertion.
                        .waitUntil("the preview has a material", ctx -> material(ctx) != null)
                        .step("its RenderType carries the graph's own vertex format and topology", ctx -> {
                            RenderTypeGraphMaterial mat = material(ctx);
                            var settings = graph(ctx).getSettings();
                            ctx.check("vertex format matches Settings",
                                    mat.renderType().format().equals(
                                            KGVertexFormat.of(settings.vertexFormatElements())),
                                    KGVertexFormat.of(settings.vertexFormatElements()),
                                    mat.renderType().format());
                            PrimitiveTopology topology = RenderTypeFactory.primitiveTopology(settings.vertexFormatMode());
                            ctx.check("topology matches Settings",
                                    mat.renderType().primitiveTopology() == topology,
                                    topology, mat.renderType().primitiveTopology());
                        })
                        .step("remember the pipeline we are drawing with",
                                ctx -> ctx.put("hash", material(ctx).contentHash()))
                        // A rejected binding would throw during these frames.
                        .frames(20)
                        .step("the material survived being drawn",
                                ctx -> ctx.check("still live", material(ctx) != null))
                        .screenshot("01_preview")
                        .screenshotElement("02_preview_panel", ".__rendertype-preview-tool__"))

                .group("editing the graph swaps in a different pipeline", g -> g
                        .step("switch the material to additive blending", ctx -> {
                            var graph = graph(ctx);
                            var old = graph.getSettings();
                            graph.setSettings(new RenderTypeGraph.Settings(
                                    old.vertexFormatElements(), old.vertexFormatMode(),
                                    RenderTypeGraph.Settings.BlendMode.ADDITIVE,
                                    old.depthTest(), old.depthWrite(), old.cull(),
                                    old.outputTarget(), old.affectsOutline(), old.sortOnUpload()));
                        })
                        .waitUntil("the preview rebuilt onto a new pipeline", ctx -> {
                            RenderTypeGraphMaterial mat = material(ctx);
                            return mat != null && !mat.contentHash().equals(ctx.get("hash"));
                        })
                        .frames(20)
                        .step("the rebuilt material survived being drawn",
                                ctx -> ctx.check("still live", material(ctx) != null))
                        .screenshot("03_additive"))

                .closeScreen();
    }

    private static RenderTypeGraphView view(TestContext ctx) {
        return ctx.el("#graph").as(RenderTypeGraphView.class);
    }

    private static RenderTypeGraph graph(TestContext ctx) {
        RenderTypeGraph graph = view(ctx).getRenderTypeGraph();
        if (graph == null) throw new IllegalStateException("no RenderTypeGraph loaded");
        return graph;
    }

    private static RenderTypeGraphMaterial material(TestContext ctx) {
        return view(ctx).getPreviewTool().getMaterial();
    }

    /** The dock panel hosting the preview tool. */
    private static GraphPanel panel(TestContext ctx) {
        UIElement element = ctx.el(".__rendertype-preview-tool__").element();
        while (element != null && !(element instanceof GraphPanel)) {
            element = element.getParent();
        }
        if (element == null) throw new IllegalStateException("the preview tool is not in a GraphPanel");
        return (GraphPanel) element;
    }

    private static ModularUI buildUI(TestContext ctx) {
        var root = new UIElement().setId("root");
        root.layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
        });
        var view = new RenderTypeGraphView();
        view.setId("graph");
        view.layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
        });
        root.addChildren(view);
        view.loadGraph(new RenderTypeGraph());
        return new ModularUI(UI.of(root));
    }
}
