package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.doc.UIDocNodes;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.test.gametest.KGGraphBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.UITemplate;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;

/**
 * Getting a UI into a graph: the xml forms, and the round trip through a template.
 *
 * <p>The interesting property here is the <b>minimal-xml tolerance</b>. Three spellings are meant to
 * be equivalent, and "equivalent" has to mean structurally identical rather than merely
 * non-crashing — a wrapper that quietly nested the tree one level deeper would still parse, still
 * build a UI, and break every selector written against it.</p>
 */
@GameTestHolder(Kilagraph.MODID)
public final class Ldlib2UiDocGameTest {

    private Ldlib2UiDocGameTest() {
    }

    /** The full document, the {@code <root>}-only form and bare elements all produce the same tree. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void allThreeXmlFormsAreEquivalent(GameTestHelper helper) {
        String[] forms = {
                "<ui><root><button id=\"ok\"/><label id=\"caption\"/></root></ui>",
                "<root><button id=\"ok\"/><label id=\"caption\"/></root>",
                "<button id=\"ok\"/><label id=\"caption\"/>",
        };
        for (String xml : forms) {
            UIElement root = parse(xml);
            assertEq(helper, xml + ": two children", 2, root.getChildren().size());
            assertTrue(helper, xml + ": first is a Button", root.getChildren().getFirst() instanceof Button);
            assertTrue(helper, xml + ": second is a Label", root.getChildren().get(1) instanceof Label);
            assertEq(helper, xml + ": ids survived", "ok", root.getChildren().getFirst().getId());
        }
        helper.succeed();
    }

    /** An XML declaration in front of a fragment must not stop the wrapper from working. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void anXmlDeclarationIsToleratedOnAFragment(GameTestHelper helper) {
        UIElement root = parse("<?xml version=\"1.0\" encoding=\"UTF-8\"?><button id=\"ok\"/>");
        assertEq(helper, "the button parsed", 1, root.getChildren().size());
        assertTrue(helper, "and it is a Button", root.getChildren().getFirst() instanceof Button);
        helper.succeed();
    }

    /** Attributes are applied by the element itself, not by the wrapper. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void xmlAttributesReachTheElement(GameTestHelper helper) {
        UIElement root = parse("<button id=\"save\" class=\"primary wide\" visible=\"false\"/>");
        UIElement button = root.getChildren().getFirst();
        assertEq(helper, "id", "save", button.getId());
        assertTrue(helper, "first class", button.hasClass("primary"));
        assertTrue(helper, "second class", button.hasClass("wide"));
        assertFalse(helper, "visible attribute", button.isVisible());
        helper.succeed();
    }

    /** Malformed xml yields an empty UI rather than throwing — a graph mid-edit is normal. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void brokenXmlDegradesToAnEmptyUi(GameTestHelper helper) {
        var g = parseGraph("<button id=\"unclosed\"");
        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));
        UI ui = exec.evaluate(g.outputOf("parse.ui"), UI.class);
        assertTrue(helper, "a UI was still produced", ui != null);
        assertEq(helper, "but it is empty", 0, ui.rootElement.getChildren().size());
        helper.succeed();
    }

    /**
     * A tree survives the trip out to a template and back.
     *
     * <p>Templates are how a UI crosses a save file or a packet, so a structure that did not survive
     * would only be noticed once something had already been stored.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aTemplateRoundTripPreservesTheTree(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("parse", UIDocNodes.ParseXml.class)
                .add("toTemplate", UIDocNodes.ToTemplate.class)
                .add("rebuild", UIDocNodes.TemplateCreateUI.class);
        g.constant("parse.xml", "<button id=\"ok\"/><label id=\"caption\"/>")
                .wire("toTemplate.root", "parse.root")
                .wire("rebuild.template", "toTemplate.template")
                .then("entry", "parse", "toTemplate", "rebuild");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));

        UIElement rebuilt = exec.evaluate(g.outputOf("rebuild.root"), UIElement.class);
        assertEq(helper, "child count survived", 2, rebuilt.getChildren().size());
        assertTrue(helper, "the button is still a Button",
                rebuilt.getChildren().getFirst() instanceof Button);
        assertEq(helper, "the id survived", "caption", rebuilt.getChildren().get(1).getId());

        // A distinct tree, not the original one handed back — that is what "stamped out" means.
        UIElement original = exec.evaluate(g.outputOf("parse.root"), UIElement.class);
        assertFalse(helper, "the rebuilt tree is independent",
                rebuilt.getChildren().getFirst() == original.getChildren().getFirst());
        helper.succeed();
    }

    /** An unresolvable template path yields the Missing placeholder, not null. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aMissingTemplatePathDegradesToMissing(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint().add("load", UIDocNodes.TemplateLoad.class);
        g.constant("load.path", "builtin(kilagraph:nothing_is_here)");
        var exec = new GraphExecutor(g.graph());

        assertFalse(helper, "it reports failure", exec.evaluate(g.outputOf("load.ok"), Boolean.class));
        UITemplate template = exec.evaluate(g.outputOf("load.template"), UITemplate.class);
        assertTrue(helper, "but still produces a template", template != null);
        helper.succeed();
    }

    /** A file that is not there gives an empty UI and says so, rather than failing the build. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aMissingXmlFileDegrades(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("load", UIDocNodes.LoadXml.class);
        g.constant("load.location", ResourceLocation.fromNamespaceAndPath(Kilagraph.MODID, "ui/absent.xml"))
                .then("entry", "load");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));
        assertFalse(helper, "it reports failure", exec.evaluate(g.outputOf("load.ok"), Boolean.class));
        assertTrue(helper, "a UI is still produced",
                exec.evaluate(g.outputOf("load.ui"), UI.class) != null);
        helper.succeed();
    }

    /**
     * A constructor re-pulled after the flow has ended gives back the same object.
     *
     * <p>The property that makes handlers work at all — {@code UIActions.produce} republishing from
     * node state rather than rebuilding.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aConstructorKeepsItsIdentityAcrossPulls(GameTestHelper helper) {
        var g = parseGraph("<button id=\"ok\"/>");
        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));

        UIElement first = exec.evaluate(g.outputOf("parse.root"), UIElement.class);
        exec.clearCache();
        UIElement second = exec.evaluate(g.outputOf("parse.root"), UIElement.class);
        assertTrue(helper, "the same root came back after a cache clear", first == second);
        helper.succeed();
    }

    // ---- fixtures ----------------------------------------------------------------------------

    private static KGGraphBuilder parseGraph(String xml) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("parse", UIDocNodes.ParseXml.class);
        g.constant("parse.xml", xml).then("entry", "parse");
        return g;
    }

    /** Runs {@code ldlib2_ui_parse_xml} over {@code xml} and returns the root it built. */
    private static UIElement parse(String xml) {
        var g = parseGraph(xml);
        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));
        return exec.evaluate(g.outputOf("parse.root"), UIElement.class);
    }
}
