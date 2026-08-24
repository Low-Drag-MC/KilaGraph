package com.lowdragmc.kilagraph.blueprint.nodes.ui.doc;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.UIActions;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.kilagraph.graph.ui.UIXml;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.editor.resource.UIResource;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.UITemplate;
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.math.Size;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Getting a {@link UI} in and out of a graph: from a file, from XML text, from parts, from a
 * template — and back to a template again.
 *
 * <h2>The three things that are not the same</h2>
 * <ul>
 *   <li>A {@link UIElement} is one live node of the tree. It has a parent, children, and (once it is
 *       in a {@link ModularUI}) a layout node.</li>
 *   <li>A {@link UI} is a root element plus the stylesheets it was built with. It is <b>immutable</b>
 *       — every field is final — so a stylesheet cannot be added to one after the fact. That is why
 *       there is no {@code set_stylesheets} node here: adding a sheet at runtime goes through
 *       {@code ldlib2_ui_global_stylesheet} on the {@code ModularUI}, and adding one at construction
 *       goes through {@code ldlib2_ui_create}.</li>
 *   <li>A {@link UITemplate} is a <b>snapshot</b>: the tree as NBT plus style references. It is what
 *       survives a save file or a network packet, and the only one of the three that can be stamped
 *       out many times.</li>
 * </ul>
 *
 * <h2>Constructors are exec nodes</h2>
 * Everything here that <em>makes</em> something has {@code trigger} / {@code next} pins, while
 * everything that only <em>looks something up</em> ({@code ldlib2_ui_template_load}) is pure data.
 * That split is deliberate and is explained in {@link UIActions#produce}: a constructed UI has
 * identity, and a pure-data constructor would silently hand an event handler a different tree from
 * the one on screen.
 *
 * <h2>Where XML is read from</h2>
 * {@code ldlib2_ui_load_xml} resolves through Minecraft's active resource manager, which is
 * <em>assets</em> on the client and <em>datapacks</em> on the server. A UI that both sides build —
 * which is every UI with sync values in it — therefore needs its xml under {@code data/}, or the
 * server will find nothing and build an empty tree while the client builds the real one. The nodes
 * here degrade rather than throw when that happens, but the sync mismatch it causes is exactly the
 * kind of bug worth avoiding by construction.
 */
public final class UIDocNodes {

    private static final String GROUP = "ui/doc";

    private UIDocNodes() {
    }

    /** A UI xml file, loaded through the resource manager. */
    @NodeAttribute(name = "ldlib2_ui_load_xml", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class LoadXml extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_load_xml.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Identifier location;
        @OutputPort public UI ui;
        @OutputPort public UIElement root;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            var location = ctx.getInput("location", Identifier.class, null);
            UI loaded = UIXml.loadUI(location);
            UIActions.produce(ctx, "ui", loaded);
            UIActions.produce(ctx, "root", loaded.rootElement);
            // An empty UI and a UI whose file happened to be empty are indistinguishable downstream,
            // so say which one this is rather than making the graph guess from the child count.
            UIActions.done(ctx, location != null && !loaded.rootElement.getChildren().isEmpty());
        }

        @Override
        public void evaluate(EvalContext ctx) {
            UIActions.republish(ctx, "ui", "root");
        }
    }

    /**
     * UI xml written inline, in as little of it as you like.
     *
     * <p>All three of these mean the same thing:</p>
     * <pre>{@code
     * <ui><root><button id="ok"/></root></ui>
     * <root><button id="ok"/></root>
     * <button id="ok"/>
     * }</pre>
     */
    @NodeAttribute(name = "ldlib2_ui_parse_xml", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ParseXml extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_parse_xml.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public String xml = "";
        @OutputPort public UI ui;
        @OutputPort public UIElement root;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            String xml = ctx.getInput("xml", String.class, "");
            UI parsed = UIXml.parseUI(xml);
            UIActions.produce(ctx, "ui", parsed);
            UIActions.produce(ctx, "root", parsed.rootElement);
            UIActions.done(ctx, xml != null && !xml.isBlank());
        }

        @Override
        public void evaluate(EvalContext ctx) {
            UIActions.republish(ctx, "ui", "root");
        }
    }

    /** A UI from a root element and the stylesheets it should be styled with. */
    @NodeAttribute(name = "ldlib2_ui_create", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Create extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_create.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UIElement root;
        @InputPort public List<?> stylesheets = List.of();
        @OutputPort public UI ui;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            UIElement element = UIActions.element(ctx, "root");
            boolean hadRoot = element != null;
            if (element == null) element = new UIElement();
            UIActions.produce(ctx, "ui",
                    UI.of(element, UIActions.list(ctx, "stylesheets", Stylesheet.class)));
            UIActions.done(ctx, hadRoot);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            UIActions.republish(ctx, "ui");
        }
    }

    /**
     * How {@code ldlib2_ui_create_sized} turns its width/height into a size.
     *
     * <p>{@code getSerializedName()} returns a <b>stored</b> string rather than
     * {@code name().toLowerCase()}. LDLib2's {@code EnumAccessor} caches the name → constant lookup
     * per class, and a name computed fresh on every call leaves nothing holding a strong reference to
     * the key — the option then saves correctly and silently reverts to its default on reload. Every
     * enum option in these nodes is written this way for that reason.</p>
     */
    public enum SizeMode implements StringRepresentable {
        /** The numbers are pixels, ignoring the screen. */
        FIXED("fixed"),
        /** The numbers are percentages of the screen, 0-100. */
        PERCENT_OF_SCREEN("percent_of_screen"),
        /** The numbers are pixels subtracted from the screen — a margin on each axis. */
        SCREEN_MINUS("screen_minus");

        private final String name;

        SizeMode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /**
     * A UI that sizes itself against the screen.
     *
     * <p>{@link UI.DynamicSizeProvider} is consulted on every screen init, so this is what a UI meant
     * to fill the window uses instead of a fixed layout width — the latter would be wrong the moment
     * the player resizes.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_create_sized", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class CreateSized extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_create_sized.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @Option public SizeMode mode = SizeMode.PERCENT_OF_SCREEN;
        @InputPort public UIElement root;
        @InputPort public List<?> stylesheets = List.of();
        @InputPort public float width = 100;
        @InputPort public float height = 100;
        @OutputPort public UI ui;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            UIElement element = UIActions.element(ctx, "root");
            boolean hadRoot = element != null;
            if (element == null) element = new UIElement();
            SizeMode picked = ctx.getOption("mode", SizeMode.class, SizeMode.PERCENT_OF_SCREEN);
            final SizeMode mode = picked == null ? SizeMode.PERCENT_OF_SCREEN : picked;
            final float w = ctx.getFloat("width", 100);
            final float h = ctx.getFloat("height", 100);
            UI.DynamicSizeProvider provider = screen -> switch (mode) {
                case FIXED -> Size.of(Math.round(w), Math.round(h));
                case PERCENT_OF_SCREEN -> Size.of(Math.round(screen.width * w / 100f),
                        Math.round(screen.height * h / 100f));
                case SCREEN_MINUS -> Size.of(Math.max(0, screen.width - Math.round(w)),
                        Math.max(0, screen.height - Math.round(h)));
            };
            UIActions.produce(ctx, "ui", UI.of(element,
                    UIActions.list(ctx, "stylesheets", Stylesheet.class), provider));
            UIActions.done(ctx, hadRoot);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            UIActions.republish(ctx, "ui");
        }
    }

    /**
     * The root element of a UI, and the stylesheets it carries.
     *
     * <p>Pure data: it takes a UI apart rather than making anything, so re-pulling it always yields
     * the same root.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_unpack", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Unpack extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_unpack.tooltip");
        }

        @InputPort public UI ui;
        @OutputPort public UIElement root;
        @OutputPort public List<?> stylesheets;

        @Override
        public void evaluate(EvalContext ctx) {
            UI value = ctx.getInput("ui", UI.class, null);
            ctx.setOutput("root", value == null ? null : value.rootElement);
            ctx.setOutput("stylesheets", value == null ? List.of() : value.stylesheets);
        }
    }

    /** A snapshot of a UI, as a template. */
    @NodeAttribute(name = "ldlib2_ui_to_template", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ToTemplate extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_to_template.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UI ui;
        @InputPort public UIElement root;
        @OutputPort public UITemplate template;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            // Either source is fine, and taking both saves a node: a graph that has assembled a
            // subtree has an element, a graph that loaded a file has a UI.
            UI ui = ctx.getInput("ui", UI.class, null);
            if (ui != null) {
                UIActions.produce(ctx, "template", ui.toTemplate());
                UIActions.done(ctx, true);
                return;
            }
            UIElement element = UIActions.element(ctx, "root");
            UIActions.produce(ctx, "template",
                    element == null ? UITemplate.missing() : UITemplate.of(element));
            UIActions.done(ctx, element != null);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            UIActions.republish(ctx, "template");
        }
    }

    /**
     * A template stored in LDLib2's UI resource library.
     *
     * <p>The path is the {@code type(path)} form the editor writes — {@code builtin(ldlib2:missing)},
     * {@code file(/my_ui.ui)}, {@code pack(mymod:ui/machine)}. An unresolvable path yields
     * {@link UITemplate#missing()}, which renders a red "Missing" label rather than a hole.</p>
     *
     * <p>Pure data: the resource library caches, so this is a lookup returning the same instance
     * rather than a construction.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_template_load", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class TemplateLoad extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_template_load.tooltip");
        }

        @InputPort public String path = "";
        @OutputPort public UITemplate template;
        @OutputPort public boolean ok;

        @Override
        public void evaluate(EvalContext ctx) {
            IResourcePath resourcePath = IResourcePath.parse(ctx.getInput("path", String.class, ""));
            UITemplate loaded = resourcePath == null ? null
                    : UIResource.INSTANCE.getResourceInstance().getResource(resourcePath);
            ctx.setOutput("template", loaded == null ? UITemplate.missing() : loaded);
            ctx.setOutput("ok", loaded != null);
        }
    }

    /** A fresh UI stamped out of a template. Each pass through builds a new tree. */
    @NodeAttribute(name = "ldlib2_ui_template_create_ui", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class TemplateCreateUI extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_template_create_ui.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UITemplate template;
        @OutputPort public UI ui;
        @OutputPort public UIElement root;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            UITemplate template = ctx.getInput("template", UITemplate.class, null);
            UI created = template == null ? UI.of() : template.createUI();
            UIActions.produce(ctx, "ui", created);
            UIActions.produce(ctx, "root", created.rootElement);
            UIActions.done(ctx, template != null);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            UIActions.republish(ctx, "ui", "root");
        }
    }

    /**
     * Stamps a template into an element that already exists, in place.
     *
     * <p>Distinct from {@code ldlib2_ui_template_create_ui}: that one makes a new root, this one fills
     * a root the graph already has a handle on — which is what you want when the element is already
     * parented into a bigger tree.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_template_init", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class TemplateInit extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_template_init.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UITemplate template;
        @InputPort public UIElement root;
        @OutputPort public UIElement out;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            UITemplate template = ctx.getInput("template", UITemplate.class, null);
            UIElement element = UIActions.element(ctx, "root");
            ctx.setOutput("out", element);
            if (template == null || element == null) {
                UIActions.done(ctx, false);
                return;
            }
            template.initUI(element);
            UIActions.done(ctx, true);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", UIActions.element(ctx, "root"));
        }
    }

    /** Replaces a template's styles: an inline LSS block plus a list of stylesheet ids. */
    @NodeAttribute(name = "ldlib2_ui_template_styles", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class TemplateStyles extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_template_styles.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UITemplate template;
        @InputPort public String builtinStyles = "";
        @InputPort public List<?> stylesheets = List.of();
        @OutputPort public UITemplate out;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            UITemplate template = ctx.getInput("template", UITemplate.class, null);
            ctx.setOutput("out", template);
            if (template == null) {
                UIActions.done(ctx, false);
                return;
            }
            String inline = ctx.getInput("builtinStyles", String.class, "");
            template.setBuiltinStyles(inline == null || inline.isBlank() ? null : inline);
            template.getStylesheets().clear();
            template.getStylesheets().addAll(UIActions.list(ctx, "stylesheets", Identifier.class));
            UIActions.done(ctx, true);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", ctx.getInput("template", UITemplate.class, null));
        }
    }

    /**
     * An independent copy of a template.
     *
     * <p>Worth having because a template's NBT is mutable and {@code createUI} does not defend against
     * a caller editing it — two UIs stamped from one template that a graph then modified would
     * disagree about what they were stamped from.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_template_copy", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class TemplateCopy extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_template_copy.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UITemplate template;
        @OutputPort public UITemplate out;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            UITemplate template = ctx.getInput("template", UITemplate.class, null);
            UIActions.produce(ctx, "out", template == null ? UITemplate.missing() : template.copy());
            UIActions.done(ctx, template != null);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            UIActions.republish(ctx, "out");
        }
    }

    /**
     * A live UI instance for a player.
     *
     * <p><b>This does not mount the tree.</b> Making the {@code ModularUI} sets up the sync manager,
     * the style engine and the layout tree, but the root is only attached to it when the <em>host</em>
     * says so: {@code setMenu} on the server, screen init on the client. Until then no element has a
     * {@code ModularUI}, and no sync value or RPC has reached the sync manager.</p>
     *
     * <p>That ordering is worth knowing because it is the opposite of what the node's position in a
     * build suggests. It also means the order sync values reach the manager is the order the
     * <em>tree</em> is walked when it mounts, not the order the graph declared them in — which is
     * still deterministic, and still requires both sides to build the same tree.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_modular_create", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ModularCreate extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_modular_create.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UI ui;
        @InputPort public Player player;
        @OutputPort public ModularUI mui;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            UI ui = ctx.getInput("ui", UI.class, null);
            UIActions.produce(ctx, "mui", ui == null ? null
                    : ModularUI.of(ui, ctx.getInput("player", Player.class, null)));
            UIActions.done(ctx, ui != null);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            UIActions.republish(ctx, "mui");
        }
    }
}
