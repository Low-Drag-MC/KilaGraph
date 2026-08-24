package com.lowdragmc.kilagraph.graph.util;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.utils.LocalizationUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

/**
 * Fluent builder for the documentation shown in the item library's description panel
 * ({@link Node#createDescriptionUI()}). Deliberately graph-agnostic — the shader graph drives it through
 * {@link NodeDescriptions}, which both the shader graph and the blueprint route their
 * {@link Node#createDescriptionUI()} through.
 *
 * <p>The panel is narrow (see {@code ItemLibrary#setDescriptionWidth}), so everything here is small text
 * that wraps and grows vertically; the library scrolls it. Layout order is header → summary → body →
 * Ports → Options → GLSL → Example → Notes, but the builder imposes no order: sections are appended in
 * call order and simply omitted when the caller has nothing for them.</p>
 */
public final class NodeDescriptionUI {
    public static final float BODY_FONT_SIZE = 6f;
    public static final float CODE_FONT_SIZE = 6f;

    private static final int COLOR_BODY = 0xFFC9C9C9;
    private static final int COLOR_BREADCRUMB = 0xFF808080;
    private static final int COLOR_HEADING = 0xFFFFB454;
    private static final int COLOR_NAME = 0xFFEDEDED;
    private static final int COLOR_MUTED = 0xFF9A9A9A;
    private static final int COLOR_NOTE = 0xFFE0A030;
    private static final int COLOR_DIVIDER = 0x33FFFFFF;

    private final UIElement root;
    /** Content added beyond the header — decides whether the panel is worth showing at all. */
    private int bodyElements = 0;

    private NodeDescriptionUI() {
        root = new UIElement();
        root.layout(layout -> {
            layout.widthPercent(100);
            layout.gapAll(2);
        });
    }

    public static NodeDescriptionUI create() {
        return new NodeDescriptionUI();
    }

    // region header

    /** Icon + display name, with the library group as a breadcrumb underneath when {@code group} is set. */
    public NodeDescriptionUI header(Node node, @Nullable String group) {
        var title = UIElementProvider.iconText(Node::getNodeIcon, Node::getDisplayName).apply(node);
        title.layout(layout -> layout.widthPercent(100));
        root.addChild(title);
        if (group != null && !group.isEmpty()) {
            root.addChild(text(breadcrumb(group), COLOR_BREADCRUMB));
        }
        root.addChild(divider());
        return this;
    }

    /** {@code "rendertype_math/vector"} → {@code "Math > Vector"}; every segment is already a lang key. */
    private static Component breadcrumb(String group) {
        MutableComponent result = Component.empty();
        String[] segments = group.split("/");
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) result.append(Component.literal(" > "));
            result.append(Component.translatable(segments[i]));
        }
        return result;
    }

    // endregion

    // region content

    public NodeDescriptionUI paragraph(Component content) {
        root.addChild(text(content, COLOR_BODY));
        bodyElements++;
        return this;
    }

    /** Adds {@code key} as a paragraph when the lang file defines it. */
    public NodeDescriptionUI paragraphKey(String key) {
        if (LocalizationUtils.exist(key)) {
            paragraph(Component.translatable(key));
        }
        return this;
    }

    /**
     * Adds {@code prefix.1}, {@code prefix.2}, … as paragraphs, stopping at the first missing index. Lets a
     * node grow from one line to a full article by adding lang keys only.
     */
    public NodeDescriptionUI paragraphSeries(String prefix) {
        for (int i = 1; LocalizationUtils.exist(prefix + "." + i); i++) {
            paragraph(Component.translatable(prefix + "." + i));
        }
        return this;
    }

    /** A section heading ({@code Ports}, {@code GLSL}, …). Does not count as body content on its own. */
    public NodeDescriptionUI section(String titleKey) {
        var heading = text(Component.translatable(titleKey), COLOR_HEADING);
        heading.layout(layout -> layout.marginTop(2));
        root.addChild(heading);
        return this;
    }

    /**
     * A port/option row: {@code name} in the node's own wording, an optional type chip coloured by the
     * wire colour, then the explanation.
     */
    public NodeDescriptionUI entry(Component name, @Nullable TypeHandle type, Component description) {
        MutableComponent line = Component.empty()
                .append(name.copy().withStyle(Style.EMPTY.withColor(rgb(COLOR_NAME))));
        if (type != null) {
            line.append(Component.literal(" "))
                    .append(Component.literal(type.getFriendlyName())
                            .withStyle(Style.EMPTY.withColor(rgb(type.getTypeColor()))));
        }
        line.append(Component.literal("  ")).append(description.copy()
                .withStyle(Style.EMPTY.withColor(rgb(COLOR_MUTED))));
        var element = text(line, COLOR_BODY);
        element.layout(layout -> layout.paddingLeft(2));
        root.addChild(element);
        bodyElements++;
        return this;
    }

    /** An indented child of the previous {@link #entry} — used for the choices of an enum option. */
    public NodeDescriptionUI subEntry(Component name, Component description) {
        MutableComponent line = Component.empty()
                .append(Component.literal("- "))
                .append(name.copy().withStyle(Style.EMPTY.withColor(rgb(COLOR_NAME))))
                .append(Component.literal("  "))
                .append(description.copy().withStyle(Style.EMPTY.withColor(rgb(COLOR_MUTED))));
        var element = text(line, COLOR_BODY);
        element.layout(layout -> layout.paddingLeft(7));
        root.addChild(element);
        bodyElements++;
        return this;
    }

    /** A caveat, in a warning colour so it reads apart from the body text. */
    public NodeDescriptionUI note(Component content) {
        root.addChild(text(Component.literal("! ").append(content), COLOR_NOTE));
        bodyElements++;
        return this;
    }

    /** A syntax-highlighted GLSL card. */
    public NodeDescriptionUI code(String glsl) {
        root.addChild(GlslCodeBlock.of(glsl));
        bodyElements++;
        return this;
    }

    public NodeDescriptionUI addChild(UIElement element) {
        root.addChild(element);
        bodyElements++;
        return this;
    }

    // endregion

    /** Whether anything beyond the header was added; {@code false} means the panel should stay collapsed. */
    public boolean hasContent() {
        return bodyElements > 0;
    }

    public UIElement build() {
        return root;
    }

    // region elements

    private static UIElement text(Component content, int color) {
        var label = new Label();
        label.textStyle(style -> style
                .textWrap(TextWrap.WRAP)
                .adaptiveHeight(true)
                .fontSize(BODY_FONT_SIZE)
                .textColor(color));
        label.setText(content);
        label.layout(layout -> layout.widthPercent(100));
        label.moveInlineAsDefault();
        label.addClass("__node_description_text__");
        return label;
    }

    private static UIElement divider() {
        var line = new UIElement();
        line.layout(layout -> {
            layout.widthPercent(100);
            layout.height(1);
        });
        line.style(style -> style.backgroundTexture(new ColorRectTexture(COLOR_DIVIDER)));
        line.moveInlineAsDefault();
        line.addClass("__node_description_divider__");
        return line;
    }

    /** Chat {@link Style} colours are 24-bit; drop the alpha byte the UI palette carries. */
    private static int rgb(int argb) {
        return argb & 0xFFFFFF;
    }

    // endregion
}
