package com.lowdragmc.kilagraph.blueprint.nodes.string;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Shared pattern handling for the three regex nodes.
 *
 * <h2>A bad pattern is data, not a bug</h2>
 * The pattern comes from a text field a player typed, so a syntax error in it is as ordinary as a
 * misspelled item id — it must not escape as an exception and kill the graph run. Every node here reports
 * it on an {@code ok} port instead, which is the same shape the registry lookups use for an unknown id.
 *
 * <h2>Not cached, on purpose</h2>
 * {@link Pattern#compile} runs on every evaluation. Caching would want a map keyed by the pattern string,
 * and that map would be shared mutable state living as long as the game, filled from player input, in a
 * codebase whose nodes are deliberately stateless definitions. The cost is a few microseconds per
 * evaluation; if a graph ever puts a regex inside a hot loop and that shows up in a profile, the fix is a
 * bounded cache here, in one place.
 *
 * <h2>Runaway patterns</h2>
 * Java's regex engine backtracks and has no timeout, so a pattern like {@code (a+)+b} against a long
 * non-matching string can run effectively forever and there is nothing these nodes can do about it from
 * inside — it would take a watchdog thread and an interruptible input sequence. Possessive quantifiers
 * ({@code a++}) and atomic groups ({@code (?>...)}) are the way out, and the node docs say so.
 */
final class Regex {

    private Regex() {
    }

    /** The compiled pattern, or null when {@code pattern} is not valid regex. */
    @Nullable
    static Pattern compile(String pattern) {
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }
}
