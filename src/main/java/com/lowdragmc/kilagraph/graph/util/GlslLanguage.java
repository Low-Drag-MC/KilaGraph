package com.lowdragmc.kilagraph.graph.util;

import com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.language.LanguageDefinition;
import com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.language.StyleManager;
import com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.language.TokenType;
import com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.language.TokenTypes;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.Set;

/**
 * A GLSL {@link LanguageDefinition} for LDLib2's syntax parser, modelled on
 * {@link com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.language.Languages#JAVASCRIPT}. Used by
 * {@link GlslCodeBlock} to colour the code snippets in the node description panel; it is a plain
 * language definition, so a future editable GLSL editor (the Expression node) can reuse it as-is.
 *
 * <p>A {@link TokenType}'s name doubles as a regex named-group, so every name here is alphanumeric and
 * unique within {@link #DEFINITION}. Names that {@link StyleManager} already knows ({@code Number},
 * {@code Keyword}, …) are reused deliberately so the built-in colours apply.</p>
 */
public final class GlslLanguage {
    private GlslLanguage() {}

    private static final List<String> KEYWORDS = List.of(
            "attribute", "break", "case", "const", "continue", "default", "discard", "do", "else",
            "false", "flat", "for", "highp", "if", "in", "inout", "layout", "lowp", "mediump",
            "noperspective", "out", "precision", "return", "smooth", "struct", "switch", "true",
            "uniform", "varying", "while");

    /** GLSL built-in types plus KilaGraph's own generated struct types. */
    private static final TokenType TYPE = new TokenType("GlslType").setPattern(
            "\\b(void|bool|int|uint|float|double|[ibud]?vec[234]|mat[234](x[234])?|"
                    + "sampler[123]D|samplerCube|sampler2DShadow|KG_Gradient|KG_Curve)\\b");

    /** Any identifier immediately followed by {@code (} — one rule covers builtins and user functions. */
    private static final TokenType CALL = new TokenType("GlslCall")
            .setPattern("\\b[a-zA-Z_][a-zA-Z0-9_]*(?=\\s*\\()");

    /** LDLib2's {@code NUMBER} is integer-only; GLSL literals are mostly floats ({@code 1.0}, {@code .5}, {@code 1e-3}). */
    private static final TokenType NUMBER = new TokenType("Number")
            .setPattern("\\b\\d+\\.?\\d*([eE][-+]?\\d+)?[fuU]?\\b|\\.\\d+");

    /** A member/swizzle access ({@code .xyz}, {@code .rgb}) — kept off the plain-identifier colour. */
    private static final TokenType MEMBER = new TokenType("GlslMember")
            .setPattern("\\.[a-zA-Z_][a-zA-Z0-9_]*");

    public static final LanguageDefinition DEFINITION = new LanguageDefinition("GLSL", List.of(
            TokenTypes.COMMENT,
            TokenTypes.KEYWORD.createTokenType(KEYWORDS),
            TYPE,
            CALL,
            NUMBER,
            MEMBER,
            TokenTypes.IDENTIFIER,
            TokenTypes.OPERATOR,
            TokenTypes.WHITESPACE,
            TokenTypes.OTHER), Set.of("{"));

    /** Dark-editor palette; plain RGB (no alpha) because chat {@link Style} colours are 24-bit. */
    public static final StyleManager STYLES = new StyleManager();

    static {
        STYLES.setDefaultStyle(Style.EMPTY.withColor(0xD4D4D4));
        var styles = STYLES.getStyleMap();
        styles.put(TokenTypes.COMMENT.name, Style.EMPTY.withColor(0x6A9955));
        styles.put(TokenTypes.KEYWORD.name, Style.EMPTY.withColor(0xC586C0));
        styles.put(TYPE.name, Style.EMPTY.withColor(0x4EC9B0));
        styles.put(CALL.name, Style.EMPTY.withColor(0xDCDCAA));
        styles.put(NUMBER.name, Style.EMPTY.withColor(0xB5CEA8));
        styles.put(MEMBER.name, Style.EMPTY.withColor(0x9CDCFE));
        styles.put(TokenTypes.IDENTIFIER.name, Style.EMPTY.withColor(0x9CDCFE));
        styles.put(TokenTypes.OPERATOR.name, Style.EMPTY.withColor(0xD4D4D4));
    }
}
