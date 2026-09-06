package com.lowdragmc.kilagraph.rendertype.compiler;

import java.util.List;

/**
 * The coordinate-space vocabulary the geometry nodes share — the same strings the compiler's space seams
 * are keyed by ({@link ShaderCompileContext#tangentBasis(String)},
 * {@link ShaderCompileContext#spaceToTangent(String, ShaderExpr)}).
 *
 * <p>Position, Normal, Tangent, Bitangent and View Direction all offer the same four spaces, so they share
 * one list and one set of labels: a space added here reaches every one of them, and the dropdowns cannot
 * drift apart or disagree on capitalisation. Transform keeps its own list — it additionally reaches
 * {@code clip} and (as a target) {@code screen}, which are not places a surface quantity can be read
 * <em>in</em>.</p>
 */
public final class GeometrySpaces {
    private GeometrySpaces() {}

    public static final String OBJECT = "object";
    public static final String WORLD = "world";
    public static final String VIEW = "view";
    public static final String TANGENT = "tangent";
    public static final String CLIP = "clip";
    public static final String SCREEN = "screen";

    /** The spaces a surface quantity can be read in, in dropdown order. */
    public static final List<String> SURFACE = List.of(OBJECT, WORLD, VIEW, TANGENT);

    /** The option id every one of those nodes uses for its space dropdown. */
    public static final String OPTION = "space";

    /** The {@code optionChoices} answer for a node whose only option is the space dropdown. */
    public static List<String> optionChoices(String optionId) {
        return OPTION.equals(optionId) ? SURFACE : List.of();
    }

    /** Display label for the choice dropdown. Unknown values read as World, matching the usual default. */
    public static String label(String space) {
        return switch (space) {
            case OBJECT -> "Object";
            case VIEW -> "View";
            case TANGENT -> "Tangent";
            case CLIP -> "Clip";
            case SCREEN -> "Screen";
            default -> "World";
        };
    }
}
