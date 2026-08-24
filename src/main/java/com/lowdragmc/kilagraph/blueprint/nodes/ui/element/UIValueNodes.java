package com.lowdragmc.kilagraph.blueprint.nodes.ui.element;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.UIActions;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.kilagraph.graph.ui.UIElements;
import com.lowdragmc.kilagraph.graph.ui.UIPropertyRegistry;
import com.lowdragmc.kilagraph.graph.ui.UISearchConfigurators;
import com.lowdragmc.kilagraph.graph.util.KGSearchConfigurators;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableUIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandleHelpers;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.lowdragmc.lowdraglib2.utils.ReflectionUtils;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Reading and writing what an element <em>contains</em>, as opposed to how it looks.
 *
 * <h2>The split this file exists to draw</h2>
 * Appearance is LSS ({@code ldlib2_ui_lss_*}) and class names. Content is here. The line matters
 * because LDLib2 draws it too: a {@code Button}'s background textures live in a {@code ButtonStyle}
 * reachable from a stylesheet, while its caption is a child {@code TextElement} — one is a style
 * property, the other is data.
 *
 * <h2>Three ways to reach content, narrowest first</h2>
 * <ol>
 *   <li>{@code ldlib2_ui_set_text} / {@code get_text}. Captions. Every element that shows a line of
 *       text does it through {@code TextElement}, directly or as a child, so one node covers
 *       {@code Label}, {@code TextElement}, {@code Button} and {@code TextField}.</li>
 *   <li>{@code ldlib2_ui_get_value} / {@code set_value}. The element's <em>one</em> value, for the
 *       elements that have one: a {@code Toggle}'s boolean, a {@code Slider}'s float, an
 *       {@code ItemSlot}'s stack. This is the {@code IDataSource} interface LDLib2 already uses to
 *       drive data bindings, so a graph and a binding are reading the same thing.</li>
 *   <li>{@code ldlib2_ui_set_property} / {@code get_property}. Anything else the element author
 *       marked {@code @Configurable} — a {@code ProgressBar}'s min and max, a {@code Selector}'s
 *       default. The property picker lists exactly what LDLib2's own Inspector shows.</li>
 * </ol>
 *
 * <p>Prefer the narrowest one that fits. {@code set_value} goes through the element's own
 * {@code setValue}, which notifies listeners and updates bound data; {@code set_property} on the
 * same field would go through its {@code @ConfigSetter} instead, which is a different (usually
 * quieter) code path.</p>
 *
 * <h2>Typed pins</h2>
 * {@code get_value} / {@code set_value} take a type from an option, because the graph cannot know
 * what an arbitrary element's value type is. {@code set_property} does <b>not</b> — it reads the
 * declared type off the field it is pointed at, so picking {@code progress-bar} → {@code value}
 * makes the pin a float without anyone saying so.
 */
public final class UIValueNodes {

    private static final String GROUP = "ui/element";

    private UIValueNodes() {
    }

    // ---- text --------------------------------------------------------------------------------

    /**
     * Sets the caption of anything that shows one.
     *
     * <p>{@code TextField} is included even though its content is a {@code String} rather than a
     * {@code Component}: it is what a player types into, and a graph pre-filling it is the same
     * gesture as setting a label. The component is flattened with {@code getString()} for it, which
     * resolves translations against whichever side is running — worth knowing before pre-filling a
     * field with a translation key on the server.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_set_text", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SetText extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_set_text.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UIElement element;
        @InputPort public Component text;
        @OutputPort public UIElement out;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            ctx.setOutput("out", element);
            Component text = ctx.getInput("text", Component.class, Component.empty());
            if (text == null) text = Component.empty();
            UIActions.done(ctx, applyText(element, text));
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", UIActions.element(ctx, "element"));
        }
    }

    /** Reads the caption back. Empty for an element that has none. */
    @NodeAttribute(name = "ldlib2_ui_get_text", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class GetText extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_get_text.tooltip");
        }

        @InputPort public UIElement element;
        @OutputPort public Component text;
        @OutputPort public String string;

        @Override
        public void evaluate(EvalContext ctx) {
            Component text = readText(UIActions.element(ctx, "element"));
            ctx.setOutput("text", text);
            ctx.setOutput("string", text.getString());
        }
    }

    private static boolean applyText(@Nullable UIElement element, Component text) {
        return switch (element) {
            // Button before TextElement: a Button is not a TextElement, it owns one, and delegating
            // through setText is what keeps the caption's own styling and layout intact.
            case Button button -> {
                button.setText(text);
                yield true;
            }
            case TextElement textElement -> {
                textElement.setText(text);
                yield true;
            }
            case TextField field -> {
                field.setText(text.getString());
                yield true;
            }
            case null, default -> false;
        };
    }

    private static Component readText(@Nullable UIElement element) {
        return switch (element) {
            case Button button -> button.text.getText();
            case TextElement textElement -> textElement.getText();
            // No null guard: TextField is @MethodsReturnNonnullByDefault, so getValue() is empty
            // rather than null for an empty field.
            case TextField field -> Component.literal(field.getValue());
            case null, default -> Component.empty();
        };
    }

    // ---- value -------------------------------------------------------------------------------

    /**
     * The element's own value, for the elements that have one.
     *
     * <p>The {@code valueType} option only types the output pin; the read itself is untyped and the
     * value is coerced on the way out. Leaving it as {@code Unknown} works and gives an
     * anything-goes pin.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_get_value", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class GetValue extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_get_value.tooltip");
        }

        @InputPort public UIElement element;
        @OutputPort public boolean ok;

        @Override
        protected void onDefineExtraOptions(IOptionDefinitionContext context) {
            context.addOption("valueType", String.class)
                    .withDefaultValue(TypeHandles.UNKNOWN.getIdentification())
                    .withConfigurable(KGSearchConfigurators.typeHandlePickerOption(() -> supportedTypes(this)))
                    .build();
        }

        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext context) {
            context.addOutputPort("value", valueType(this));
        }

        @Override
        public void evaluate(EvalContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            Object value = readValue(element);
            ctx.setOutput("value", value);
            ctx.setOutput("ok", isValueCarrier(element));
        }
    }

    /**
     * Writes the element's value through its own setter, so listeners and bindings see it.
     *
     * <p>{@code notify} maps to {@code BindableUIElement.setValue(value, notify)}. Turning it off is
     * how a handler writes a value back into the control that produced it without re-entering its own
     * change listener — the classic feedback loop in a two-way binding.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_set_value", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SetValue extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_set_value.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UIElement element;
        @InputPort public boolean notify = true;
        @OutputPort public UIElement out;
        @OutputPort public boolean ok;

        @Override
        protected void onDefineExtraOptions(IOptionDefinitionContext context) {
            context.addOption("valueType", String.class)
                    .withDefaultValue(TypeHandles.UNKNOWN.getIdentification())
                    .withConfigurable(KGSearchConfigurators.typeHandlePickerOption(() -> supportedTypes(this)))
                    .build();
        }

        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext context) {
            context.addInputPort("value", valueType(this));
        }

        @Override
        public void execute(ExecContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            ctx.setOutput("out", element);
            UIActions.done(ctx, writeValue(element, ctx.getInputRaw("value"), ctx.getBool("notify", true)));
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", UIActions.element(ctx, "element"));
        }
    }

    private static boolean isValueCarrier(@Nullable UIElement element) {
        return element instanceof IDataSource<?>;
    }

    @Nullable
    private static Object readValue(@Nullable UIElement element) {
        // BindableUIElement extends IDataSource, so the one check covers both; Label implements
        // IDataSource directly without extending BindableUIElement, which is why the test is on the
        // interface rather than the class.
        return element instanceof IDataSource<?> source ? source.getValue() : null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean writeValue(@Nullable UIElement element, @Nullable Object value, boolean notify) {
        if (element instanceof BindableUIElement bindable) {
            try {
                bindable.setValue(value, notify);
                return true;
            } catch (ClassCastException e) {
                // The graph handed a value of the wrong type. Refuse rather than propagate: an
                // untyped pin makes this a normal authoring mistake, not an exceptional condition.
                return false;
            }
        }
        if (element instanceof IDataSource source) {
            try {
                source.setValue(value);
                return true;
            } catch (ClassCastException e) {
                return false;
            }
        }
        return false;
    }

    // ---- @Configurable property --------------------------------------------------------------

    /**
     * Writes one of the element's {@code @Configurable} fields.
     *
     * <p>Two options work together: {@code elementType} chooses which element class's property list
     * to offer, and {@code property} picks from it. The value pin then takes the field's own declared
     * type — pick {@code progress-bar} then {@code value} and it becomes a float pin.</p>
     *
     * <p>{@code elementType} is only a lens for the picker; the write itself resolves the property
     * against the element that actually arrives, so pointing the node at a subclass still works.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_set_property", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SetProperty extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_set_property.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UIElement element;
        @OutputPort public UIElement out;
        @OutputPort public boolean ok;

        @Override
        protected void onDefineExtraOptions(IOptionDefinitionContext context) {
            definePropertyOptions(this, context);
        }

        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext context) {
            context.addInputPort("value", propertyType(this));
        }

        @Override
        public void execute(ExecContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            ctx.setOutput("out", element);
            var property = UIPropertyRegistry.find(element, ctx.getOption("property", String.class, ""));
            if (property == null) {
                UIActions.done(ctx, false);
                return;
            }
            // convertType first: a @Configurable field declared `boolean` yields the primitive class
            // token, and coerce() checks isInstance, which is false for every primitive token — so
            // coercing straight to boolean.class silently yields null and the write never happens.
            Class<?> raw = ReflectionUtils.getRawType(
                    TypeHandleHelpers.convertType(property.type()), Object.class);
            Object value = EvalContext.coerce(ctx.getInputRaw("value"), raw);
            if (value == null) {
                UIActions.done(ctx, false);
                return;
            }
            property.setter().accept(element, value);
            UIActions.done(ctx, true);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", UIActions.element(ctx, "element"));
        }
    }

    /** Reads one of the element's {@code @Configurable} fields. Same option pair as the setter. */
    @NodeAttribute(name = "ldlib2_ui_get_property", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class GetProperty extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_get_property.tooltip");
        }

        @InputPort public UIElement element;
        @OutputPort public boolean ok;

        @Override
        protected void onDefineExtraOptions(IOptionDefinitionContext context) {
            definePropertyOptions(this, context);
        }

        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext context) {
            context.addOutputPort("value", propertyType(this));
        }

        @Override
        public void evaluate(EvalContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            var property = UIPropertyRegistry.find(element, ctx.getOption("property", String.class, ""));
            ctx.setOutput("value", property == null ? null : property.getter().apply(element));
            ctx.setOutput("ok", property != null);
        }
    }

    /** Lists the {@code @Configurable} property keys of the chosen element type, for documentation. */
    @NodeAttribute(name = "ldlib2_ui_list_properties", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ListProperties extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_list_properties.tooltip");
        }

        @InputPort public UIElement element;
        @OutputPort public List<?> keys;
        @OutputPort public int count;

        @Override
        public void evaluate(EvalContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            List<String> keys = element == null ? List.of()
                    : List.copyOf(UIPropertyRegistry.propertiesOf(element.getClass()).keySet());
            ctx.setOutput("keys", keys);
            ctx.setOutput("count", keys.size());
        }
    }

    // ---- shared option / port plumbing -------------------------------------------------------

    private static void definePropertyOptions(AnnotatedNode node, IOptionDefinitionContext context) {
        context.addOption("elementType", String.class)
                .withDefaultValue(UIElements.DEFAULT)
                .withConfigurable(UISearchConfigurators.uiElementTypeOption())
                .build();
        context.addOption("property", String.class)
                .withDefaultValue("")
                .withConfigurable(UISearchConfigurators.propertyOption(
                        () -> optionString(node, "elementType", UIElements.DEFAULT)))
                .build();
    }

    /**
     * The declared type of the picked property, or {@code Unknown} while nothing is picked.
     *
     * <p>Through {@code convertType} so a primitive field becomes its wrapper: a handle minted from
     * {@code boolean.class} would carry the identification {@code "boolean"}, which no accessor is
     * registered under, leaving the pin with no editor and no constant.</p>
     */
    private static TypeHandle propertyType(AnnotatedNode node) {
        String elementType = optionString(node, "elementType", UIElements.DEFAULT);
        String key = optionString(node, "property", "");
        var property = UIPropertyRegistry.propertiesOfRegistered(elementType).get(key);
        return property == null ? TypeHandles.UNKNOWN
                : KGTypeHandles.handleFor(TypeHandleHelpers.convertType(property.type()));
    }

    /** The handle named by the {@code valueType} option. */
    private static TypeHandle valueType(AnnotatedNode node) {
        return UIActions.optionTypeHandle(node, "valueType");
    }

    private static String optionString(AnnotatedNode node, String optionId, String fallback) {
        return UIActions.optionString(node, optionId, fallback);
    }

    private static List<TypeHandle> supportedTypes(AnnotatedNode node) {
        return UIActions.supportedTypes(node);
    }
}
