package com.lowdragmc.kilagraph.blueprint.nodes.ui.style;

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
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Making, loading and attaching stylesheets.
 *
 * <h2>Three scopes, and the one that is not obvious</h2>
 * <ul>
 *   <li><b>The UI's sheets</b> — given to {@code ldlib2_ui_create} and fixed thereafter, because
 *       {@code UI} is immutable.</li>
 *   <li><b>A local sheet</b> — attached to one element and applying to it and its descendants. The
 *       right scope for a panel that brings its own look.</li>
 *   <li><b>A global sheet</b> — added to a live {@code ModularUI}'s style engine. This is the
 *       <em>only</em> way to add a sheet after the UI was constructed, and it is what runtime
 *       theming has to use.</li>
 * </ul>
 *
 * <h2>Merged versus exact</h2>
 * {@code StylesheetManager} resolves a location ending in {@code .lss} to that one file, and a
 * location without the extension to the <em>merge</em> of every built-in and pack stylesheet at that
 * path. The merged form is what makes a theme extensible: another mod dropping
 * {@code assets/theirmod/lss/mc.lss} into the pack contributes rules to {@code ldlib2:lss/mc}
 * without touching anything. Prefer merged unless you specifically want one file's rules alone.
 *
 * <h2>Client-side reality</h2>
 * Parsing a stylesheet works anywhere. <em>Applying</em> one does not: {@code StyleEngine} only runs
 * on a client {@code ModularUI}, so on the server a local or global sheet is recorded and never
 * resolved. {@code ldlib2_ui_apply_stylesheet} is the escape hatch — it performs the match-and-apply
 * by hand, on either side.
 */
public final class UIStylesheetNodes {

    private static final String GROUP = "ui/style";

    private UIStylesheetNodes() {
    }

    /**
     * Parses LSS text into a stylesheet. Comments and multiple rules are all supported.
     *
     * <p>An exec node, unlike the other stylesheet sources here, because it <em>makes</em> a sheet
     * rather than fetching a shared one — and a {@code Stylesheet} has no {@code equals}, so removing
     * one later means removing that exact instance. See {@link UIActions#produce}.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_stylesheet_parse", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Parse extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_stylesheet_parse.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public String lss = "";
        @InputPort public String name = "";
        @OutputPort public Stylesheet stylesheet;
        @OutputPort public int rules;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            String lss = ctx.getInput("lss", String.class, "");
            Stylesheet parsed = lss == null || lss.isBlank() ? Stylesheet.EMPTY : Stylesheet.parse(lss);
            String name = ctx.getInput("name", String.class, "");
            // Never rename EMPTY: it is a shared singleton and a name on it would leak everywhere.
            if (parsed != Stylesheet.EMPTY && name != null && !name.isBlank()) parsed.setName(name);
            UIActions.produce(ctx, "stylesheet", parsed);
            UIActions.produce(ctx, "rules", parsed.rules.size());
            UIActions.done(ctx, !parsed.rules.isEmpty());
        }

        @Override
        public void evaluate(EvalContext ctx) {
            UIActions.republish(ctx, "stylesheet", "rules");
        }
    }

    /**
     * A stylesheet from a resource location.
     *
     * <p>{@code ldlib2:lss/mc.lss} is that one file; {@code ldlib2:lss/mc} is every {@code lss/mc.lss}
     * in every loaded pack, merged.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_stylesheet_load", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Load extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_stylesheet_load.tooltip");
        }

        @InputPort public ResourceLocation location;
        @OutputPort public Stylesheet stylesheet;
        @OutputPort public boolean ok;

        @Override
        public void evaluate(EvalContext ctx) {
            var location = ctx.getInput("location", ResourceLocation.class, null);
            Stylesheet found = location == null ? null : StylesheetManager.INSTANCE.getStylesheet(location);
            ctx.setOutput("stylesheet", found == null ? Stylesheet.EMPTY : found);
            ctx.setOutput("ok", found != null);
        }
    }

    /** The stylesheets LDLib2 ships. */
    public enum BuiltinSheet implements StringRepresentable {
        /** The graph/editor look, this one file. */
        GDP("gdp", StylesheetManager.GDP),
        /** The graph/editor look, merged across packs. */
        GDP_MERGED("gdp_merged", StylesheetManager.GDP_MERGED),
        ORE("ore", StylesheetManager.ORE),
        ORE_MERGED("ore_merged", StylesheetManager.ORE_MERGED),
        /** The vanilla-Minecraft look, this one file. */
        MC("mc", StylesheetManager.MC),
        /** The vanilla-Minecraft look, merged across packs. Usually the one you want. */
        MC_MERGED("mc_merged", StylesheetManager.MC_MERGED),
        MODERN("modern", StylesheetManager.MODERN),
        MODERN_MERGED("modern_merged", StylesheetManager.MODERN_MERGED);

        private final String name;
        public final ResourceLocation location;

        BuiltinSheet(String name, ResourceLocation location) {
            this.name = name;
            this.location = location;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** One of LDLib2's own stylesheets, without having to remember its resource location. */
    @NodeAttribute(name = "ldlib2_ui_stylesheet_builtin", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Builtin extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_stylesheet_builtin.tooltip");
        }

        @Option public BuiltinSheet sheet = BuiltinSheet.MC_MERGED;
        @OutputPort public Stylesheet stylesheet;
        @OutputPort public ResourceLocation location;

        @Override
        public void evaluate(EvalContext ctx) {
            BuiltinSheet sheet = ctx.getOption("sheet", BuiltinSheet.class, BuiltinSheet.MC_MERGED);
            if (sheet == null) sheet = BuiltinSheet.MC_MERGED;
            ctx.setOutput("location", sheet.location);
            ctx.setOutput("stylesheet", StylesheetManager.INSTANCE.getStylesheetSafe(sheet.location));
        }
    }

    /**
     * Merges one stylesheet's rules into another, in place.
     *
     * <p>Mutates {@code target}, which is what makes it worth a warning: merging into a sheet the
     * manager owns edits it for every UI in the game. Merge into one this graph parsed.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_stylesheet_merge", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Merge extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_stylesheet_merge.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Stylesheet target;
        @InputPort public Stylesheet other;
        @OutputPort public Stylesheet out;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            Stylesheet target = ctx.getInput("target", Stylesheet.class, null);
            Stylesheet other = ctx.getInput("other", Stylesheet.class, null);
            ctx.setOutput("out", target);
            if (target == null || other == null || target == Stylesheet.EMPTY) {
                UIActions.done(ctx, false);
                return;
            }
            target.merge(other);
            UIActions.done(ctx, true);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", ctx.getInput("target", Stylesheet.class, null));
        }
    }

    /**
     * Registers a stylesheet under a resource location, so anything can then load it by name and it
     * takes part in merging.
     *
     * <p>Global and permanent for the session. A graph that runs more than once should register the
     * same location each time rather than generating names, or the merged sheets grow without
     * bound.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_stylesheet_register", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Register extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_stylesheet_register.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public ResourceLocation location;
        @InputPort public Stylesheet stylesheet;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            var location = ctx.getInput("location", ResourceLocation.class, null);
            Stylesheet stylesheet = ctx.getInput("stylesheet", Stylesheet.class, null);
            if (location == null || stylesheet == null) {
                UIActions.done(ctx, false);
                return;
            }
            StylesheetManager.INSTANCE.registerBuiltinStylesheet(location, stylesheet);
            UIActions.done(ctx, true);
        }
    }

    /** What {@code ldlib2_ui_local_stylesheet} does to an element's local sheets. */
    public enum LocalOp implements StringRepresentable {
        ADD("add"),
        REMOVE("remove"),
        /** Removes every local sheet. Ignores the stylesheet and lss inputs. */
        CLEAR("clear");

        private final String name;

        LocalOp(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /**
     * Attaches or detaches a stylesheet scoped to one element and its descendants.
     *
     * <p>Takes either a parsed {@code stylesheet} or raw {@code lss} text — whichever is wired; the
     * parsed one wins if both are. The text form saves a node for the common case of a panel with a
     * few rules of its own.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_local_stylesheet", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class LocalStylesheet extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_local_stylesheet.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @Option public LocalOp op = LocalOp.ADD;
        @InputPort public UIElement element;
        @InputPort public Stylesheet stylesheet;
        @InputPort public String lss = "";
        @OutputPort public UIElement out;
        @OutputPort public Stylesheet applied;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            ctx.setOutput("out", element);
            if (element == null) {
                UIActions.done(ctx, false);
                return;
            }
            LocalOp op = ctx.getOption("op", LocalOp.class, LocalOp.ADD);
            if (op == LocalOp.CLEAR) {
                element.clearLocalStylesheets();
                UIActions.done(ctx, true);
                return;
            }
            Stylesheet sheet = resolve(ctx);
            ctx.setOutput("applied", sheet);
            if (sheet == null) {
                UIActions.done(ctx, false);
                return;
            }
            if (op == LocalOp.REMOVE) {
                element.removeLocalStylesheet(sheet);
            } else {
                element.addLocalStylesheet(sheet);
            }
            UIActions.done(ctx, true);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", UIActions.element(ctx, "element"));
        }

        @Nullable
        private static Stylesheet resolve(ExecContext ctx) {
            Stylesheet sheet = ctx.getInput("stylesheet", Stylesheet.class, null);
            if (sheet != null) return sheet;
            String lss = ctx.getInput("lss", String.class, "");
            return lss == null || lss.isBlank() ? null : Stylesheet.parse(lss);
        }
    }

    /**
     * Adds or removes a stylesheet on a live {@code ModularUI}.
     *
     * <p>The only runtime path there is: {@code UI.stylesheets} is final, so a theme switch has to go
     * through the style engine. No-ops on the server, which has no style engine to speak of.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_global_stylesheet", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class GlobalStylesheet extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_global_stylesheet.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @Option public LocalOp op = LocalOp.ADD;
        @InputPort public ModularUI mui;
        @InputPort public Stylesheet stylesheet;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            ModularUI mui = ctx.getInput("mui", ModularUI.class, null);
            if (mui == null || LDLib2.isServer()) {
                UIActions.done(ctx, false);
                return;
            }
            LocalOp op = ctx.getOption("op", LocalOp.class, LocalOp.ADD);
            if (op == LocalOp.CLEAR) {
                mui.getStyleEngine().clearAllStylesheets();
                UIActions.done(ctx, true);
                return;
            }
            Stylesheet sheet = ctx.getInput("stylesheet", Stylesheet.class, null);
            if (sheet == null) {
                UIActions.done(ctx, false);
                return;
            }
            if (op == LocalOp.REMOVE) {
                mui.getStyleEngine().removeStylesheet(sheet);
            } else {
                mui.getStyleEngine().addStylesheet(sheet);
            }
            UIActions.done(ctx, true);
        }
    }

    /**
     * Matches a stylesheet against an element and writes the matching rules onto it, by hand.
     *
     * <p>The step {@code StyleEngine} performs automatically when an element joins a client UI. Doing
     * it explicitly is useful in two places: on the server, where there is no engine at all, and
     * before a UI is mounted, where there is not one yet.</p>
     *
     * <p>{@code recursive} is on by default because a stylesheet's whole point is that its selectors
     * find things throughout a subtree.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_apply_stylesheet", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ApplyStylesheet extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_apply_stylesheet.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UIElement element;
        @InputPort public Stylesheet stylesheet;
        @InputPort public boolean recursive = true;
        @OutputPort public UIElement out;
        @OutputPort public int matched;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            Stylesheet stylesheet = ctx.getInput("stylesheet", Stylesheet.class, null);
            ctx.setOutput("out", element);
            if (element == null || stylesheet == null) {
                ctx.setOutput("matched", 0);
                UIActions.done(ctx, false);
                return;
            }
            List<UIElement> targets = ctx.getBool("recursive", true)
                    ? element.selfAndAllChildren().toList()
                    : List.of(element);
            int matched = 0;
            for (UIElement target : targets) {
                var rules = stylesheet.calculateValues(target);
                if (rules.isEmpty()) continue;
                target.addStyleRules(rules);
                matched += rules.size();
            }
            ctx.setOutput("matched", matched);
            UIActions.done(ctx, true);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", UIActions.element(ctx, "element"));
        }
    }
}
