package com.lowdragmc.kilagraph.graph.core;

/**
 * Interned ids for the numbered ports that variable-arity nodes declare — {@code in1}, {@code out2},
 * {@code case3} and friends.
 *
 * <p>Nodes like {@code Add}, {@code Sequence} and {@code Switch} name their ports by index, and used
 * to build that name with {@code "in" + i} both when declaring the port and again on every read. The
 * declaration happens once and does not matter; the read happens on every evaluation, and string
 * concatenation allocates twice (the {@code String} and its {@code byte[]}). Measured on a chain of
 * {@code Add} nodes that was 96 bytes per node — after the executor itself had been brought to zero,
 * it was the entire remaining cost.</p>
 *
 * <p>So the ids are built once into a table. Indices past the table fall back to concatenation,
 * which keeps an absurdly wide node correct rather than fast.</p>
 *
 * <p>The strings are interned, which also makes the executor's id lookup a reference comparison in
 * the common case: the ids a node passes here are the same objects the prepared port table holds.</p>
 */
public final class PortIds {

    /** Ports are 1-based in the node library; the table is sized well past any usable node width. */
    private static final int MAX = 64;

    private static final String[] IN = table("in");
    private static final String[] OUT = table("out");
    private static final String[] CASE = table("case");
    private static final String[] ARG = table("arg");
    private static final String[] KEY = table("key");
    private static final String[] VALUE = table("value");

    private PortIds() {}

    private static String[] table(String prefix) {
        String[] ids = new String[MAX + 1];
        for (int i = 0; i <= MAX; i++) ids[i] = (prefix + i).intern();
        return ids;
    }

    private static String at(String[] ids, String prefix, int index) {
        return index >= 0 && index < ids.length ? ids[index] : prefix + index;
    }

    public static String in(int index) {
        return at(IN, "in", index);
    }

    public static String out(int index) {
        return at(OUT, "out", index);
    }

    public static String caseId(int index) {
        return at(CASE, "case", index);
    }

    public static String arg(int index) {
        return at(ARG, "arg", index);
    }

    public static String key(int index) {
        return at(KEY, "key", index);
    }

    public static String value(int index) {
        return at(VALUE, "value", index);
    }
}
