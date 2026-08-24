package com.lowdragmc.kilagraph.graph.ui;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * XML in, UI out — with the ceremony made optional.
 *
 * <h2>Minimal XML</h2>
 * A full LDLib2 UI document looks like this:
 *
 * <pre>{@code
 * <ui>
 *   <style>.big { width: 100; }</style>
 *   <root><button id="ok"/></root>
 * </ui>
 * }</pre>
 *
 * <p>Typing all of that to place one button in a graph node's text field is noise, so this class
 * accepts three progressively shorter forms and normalises them to the first:</p>
 *
 * <ol>
 *   <li>{@code <ui>…</ui>} — the full document, passed through untouched.</li>
 *   <li>{@code <root>…</root>} — wrapped in {@code <ui>}. Use when there are no stylesheets.</li>
 *   <li>{@code <button/><label/>} — bare elements, wrapped in {@code <ui><root>}. Note that this
 *       form is not well-formed XML on its own (several roots), which is exactly why it has to be
 *       recognised textually and wrapped <em>before</em> the parser sees it rather than after a
 *       failed parse.</li>
 * </ol>
 *
 * <p>The recognition is a scan for the first tag name, skipping an XML declaration, comments and
 * whitespace. It is not a parser and does not need to be: the three forms differ in their first
 * tag, and anything it guesses wrong about still reaches the real parser, which reports the real
 * error.</p>
 */
public final class UIXml {

    private static final Logger LOGGER = LogUtils.getLogger();

    private UIXml() {}

    /**
     * Parses any of the three forms into a {@link UI}.
     *
     * <p>Returns an empty UI on a parse failure rather than throwing — the node that calls this has
     * an output pin that has to carry something, and a graph mid-edit will spend most of its life
     * holding XML that does not parse yet.</p>
     */
    public static UI parseUI(@Nullable String xml) {
        Document document = parseDocument(xml);
        if (document == null) return UI.of();
        return UI.of(document);
    }

    /** Loads a UI xml file through the active {@code ResourceManager}. */
    public static UI loadUI(@Nullable Identifier location) {
        if (location == null) return UI.of();
        Document document = XmlUtils.loadXml(location);
        if (document == null) {
            // Worth naming the side: the client resolves this against assets, the server against
            // datapacks, so a file that works in singleplayer can be missing on a dedicated server.
            LOGGER.warn("No ldlib2 ui xml at {} (assets on the client, datapacks on the server)", location);
            return UI.of();
        }
        return UI.of(document);
    }

    /**
     * Parses a fragment into loose elements, without the {@code UI} wrapper.
     *
     * <p>Reproduces {@code UIElement.parseXmlChildElement}'s contract — the tag name is the registry
     * name, and each element loads its own attributes and children — by handing the fragment to a
     * throwaway root and taking that root's children. Doing it through a real {@code UIElement}
     * rather than reimplementing the walk is what keeps {@code <style>} blocks, {@code class} lists
     * and nested {@code <internal>} children behaving identically to a file-loaded UI.</p>
     */
    public static List<UIElement> parseElements(@Nullable String xml) {
        Document document = parseDocument(xml);
        if (document == null) return List.of();
        Element root = rootChild(document.getDocumentElement());
        if (root == null) return List.of();
        var holder = new UIElement();
        holder.loadXml(root);
        // Copy out and detach: the holder is scaffolding, and an element still pointing at it would
        // refuse to be added anywhere else (addChildAt removes from the old parent, but the holder
        // would then be the thing keeping a whole discarded subtree alive).
        var children = new ArrayList<>(holder.getChildren());
        for (UIElement child : children) {
            holder.removeChild(child);
        }
        return children;
    }

    // ---- normalisation -----------------------------------------------------------------------

    /** Parses after wrapping, or {@code null} if the text is blank or malformed. */
    @Nullable
    private static Document parseDocument(@Nullable String xml) {
        if (xml == null || xml.isBlank()) return null;
        String normalised = normalise(xml.trim());
        Document document = XmlUtils.loadXml(normalised);
        if (document == null) {
            LOGGER.warn("Could not parse ldlib2 ui xml: {}", abbreviate(xml));
        }
        return document;
    }

    /** Brings any of the three accepted forms up to a full {@code <ui>} document. */
    private static String normalise(String xml) {
        String declaration = "";
        String body = xml;
        if (body.startsWith("<?xml")) {
            int end = body.indexOf("?>");
            if (end >= 0) {
                // The declaration has to stay at position 0 of the final string, so it is lifted out
                // before wrapping and put back in front afterwards.
                declaration = body.substring(0, end + 2);
                body = body.substring(end + 2).trim();
            }
        }
        String tag = firstTagName(body);
        if ("ui".equals(tag)) return declaration + body;
        if ("root".equals(tag)) return declaration + "<ui>" + body + "</ui>";
        return declaration + "<ui><root>" + body + "</root></ui>";
    }

    /** The name of the first element tag, skipping comments and whitespace. {@code ""} if none. */
    private static String firstTagName(String xml) {
        int i = 0;
        while (i < xml.length()) {
            char c = xml.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (xml.startsWith("<!--", i)) {
                int end = xml.indexOf("-->", i);
                if (end < 0) return "";
                i = end + 3;
            } else if (c == '<') {
                int start = ++i;
                while (i < xml.length() && (Character.isLetterOrDigit(xml.charAt(i))
                        || xml.charAt(i) == '-' || xml.charAt(i) == '_' || xml.charAt(i) == ':')) {
                    i++;
                }
                return xml.substring(start, i);
            } else {
                return "";
            }
        }
        return "";
    }

    /** The {@code <root>} child of a normalised {@code <ui>} document. */
    @Nullable
    private static Element rootChild(@Nullable Element document) {
        if (document == null) return null;
        var nodes = document.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element element && element.getTagName().equals("root")) {
                return element;
            }
        }
        return null;
    }

    private static String abbreviate(String xml) {
        String flat = xml.replaceAll("\\s+", " ").trim();
        return flat.length() <= 120 ? flat : flat.substring(0, 117) + "...";
    }
}
