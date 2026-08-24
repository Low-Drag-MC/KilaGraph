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
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

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
public final class Ldlib2UiDocGameTest {
    private static final String ALL_THREE_XML_FORMS_ARE_EQUIVALENT = "ldlib2_ui_doc_all_three_xml_forms_are_equivalent";
    private static final String AN_XML_DECLARATION_IS_TOLERATED_ON_A_FRAGMENT = "ldlib2_ui_doc_an_xml_declaration_is_tolerated_on_a_fragment";
    private static final String XML_ATTRIBUTES_REACH_THE_ELEMENT = "ldlib2_ui_doc_xml_attributes_reach_the_element";
    private static final String BROKEN_XML_DEGRADES_TO_AN_EMPTY_UI = "ldlib2_ui_doc_broken_xml_degrades_to_an_empty_ui";
    private static final String A_TEMPLATE_ROUND_TRIP_PRESERVES_THE_TREE = "ldlib2_ui_doc_a_template_round_trip_preserves_the_tree";
    private static final String A_MISSING_TEMPLATE_PATH_DEGRADES_TO_MISSING = "ldlib2_ui_doc_a_missing_template_path_degrades_to_missing";
    private static final String A_MISSING_XML_FILE_DEGRADES = "ldlib2_ui_doc_a_missing_xml_file_degrades";
    private static final String A_CONSTRUCTOR_KEEPS_ITS_IDENTITY_ACROSS_PULLS = "ldlib2_ui_doc_a_constructor_keeps_its_identity_across_pulls";

    public static void registerFunctions() {
        KGGameTests.registerFunction(ALL_THREE_XML_FORMS_ARE_EQUIVALENT, Ldlib2UiDocGameTest::allThreeXmlFormsAreEquivalent);
        KGGameTests.registerFunction(AN_XML_DECLARATION_IS_TOLERATED_ON_A_FRAGMENT, Ldlib2UiDocGameTest::anXmlDeclarationIsToleratedOnAFragment);
        KGGameTests.registerFunction(XML_ATTRIBUTES_REACH_THE_ELEMENT, Ldlib2UiDocGameTest::xmlAttributesReachTheElement);
        KGGameTests.registerFunction(BROKEN_XML_DEGRADES_TO_AN_EMPTY_UI, Ldlib2UiDocGameTest::brokenXmlDegradesToAnEmptyUi);
        KGGameTests.registerFunction(A_TEMPLATE_ROUND_TRIP_PRESERVES_THE_TREE, Ldlib2UiDocGameTest::aTemplateRoundTripPreservesTheTree);
        KGGameTests.registerFunction(A_MISSING_TEMPLATE_PATH_DEGRADES_TO_MISSING, Ldlib2UiDocGameTest::aMissingTemplatePathDegradesToMissing);
        KGGameTests.registerFunction(A_MISSING_XML_FILE_DEGRADES, Ldlib2UiDocGameTest::aMissingXmlFileDegrades);
        KGGameTests.registerFunction(A_CONSTRUCTOR_KEEPS_ITS_IDENTITY_ACROSS_PULLS, Ldlib2UiDocGameTest::aConstructorKeepsItsIdentityAcrossPulls);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{
                ALL_THREE_XML_FORMS_ARE_EQUIVALENT, AN_XML_DECLARATION_IS_TOLERATED_ON_A_FRAGMENT, XML_ATTRIBUTES_REACH_THE_ELEMENT,
                BROKEN_XML_DEGRADES_TO_AN_EMPTY_UI, A_TEMPLATE_ROUND_TRIP_PRESERVES_THE_TREE, A_MISSING_TEMPLATE_PATH_DEGRADES_TO_MISSING,
                A_MISSING_XML_FILE_DEGRADES, A_CONSTRUCTOR_KEEPS_ITS_IDENTITY_ACROSS_PULLS
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    private Ldlib2UiDocGameTest() {
    }

    /** The full document, the {@code <root>}-only form and bare elements all produce the same tree. */
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
    public static void anXmlDeclarationIsToleratedOnAFragment(GameTestHelper helper) {
        UIElement root = parse("<?xml version=\"1.0\" encoding=\"UTF-8\"?><button id=\"ok\"/>");
        assertEq(helper, "the button parsed", 1, root.getChildren().size());
        assertTrue(helper, "and it is a Button", root.getChildren().getFirst() instanceof Button);
        helper.succeed();
    }

    /** Attributes are applied by the element itself, not by the wrapper. */
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
    public static void aMissingXmlFileDegrades(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("load", UIDocNodes.LoadXml.class);
        g.constant("load.location", Identifier.fromNamespaceAndPath(Kilagraph.MODID, "ui/absent.xml"))
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
