package com.lowdragmc.kilagraph.graph.util;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.language.SyntaxParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * A read-only, syntax-highlighted GLSL block for the node description panel: a dark card holding one
 * {@link Label} per source line, each line coloured by {@link GlslLanguage}.
 *
 * <p>Deliberately <em>not</em> a {@code CodeEditor}/{@code TextArea} — those are editable and would take
 * focus and swallow key presses inside the item-library popup. Lines wrap rather than clip, so nothing
 * is ever hidden, but snippets should still be authored short (~48 chars) to read well in the panel.</p>
 */
public final class GlslCodeBlock {
    private GlslCodeBlock() {}

    private static final int BACKGROUND = 0xFF1B1B1F;

    public static UIElement of(String code) {
        var container = new UIElement();
        container.layout(layout -> {
            layout.widthPercent(100);
            layout.paddingHorizontal(3);
            layout.paddingVertical(2);
            layout.gapAll(0);
        });
        container.style(style -> style.backgroundTexture(new ColorRectTexture(BACKGROUND)));

        // One parser per block: it is stateless between lines but holds the compiled pattern.
        var parser = new SyntaxParser();
        parser.setLanguageDefinition(GlslLanguage.DEFINITION);
        for (String line : code.split("\n", -1)) {
            container.addChild(lineLabel(parser, line));
        }
        return container;
    }

    private static UIElement lineLabel(SyntaxParser parser, String line) {
        var label = new Label();
        label.textStyle(style -> style
                .textWrap(TextWrap.WRAP)
                .adaptiveHeight(true)
                .fontSize(NodeDescriptionUI.CODE_FONT_SIZE)
                .textShadow(false));
        // A blank line still needs a line box, otherwise the paragraph spacing inside the block collapses.
        label.setText(line.isBlank() ? Component.literal(" ") : highlight(parser, line));
        label.layout(layout -> layout.widthPercent(100));
        return label;
    }

    private static Component highlight(SyntaxParser parser, String line) {
        MutableComponent result = Component.empty();
        for (var token : parser.parseLine(line)) {
            var style = token.type() == null
                    ? GlslLanguage.STYLES.getDefaultStyle()
                    : GlslLanguage.STYLES.getStyleForTokenType(token.type());
            result.append(Component.literal(token.text()).withStyle(style));
        }
        return result;
    }
}
