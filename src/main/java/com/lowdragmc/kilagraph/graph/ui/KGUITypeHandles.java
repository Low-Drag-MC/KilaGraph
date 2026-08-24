package com.lowdragmc.kilagraph.graph.ui;

import com.lowdragmc.lowdraglib2.gui.sync.SyncValue;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBinding;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEmitter;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.UITemplate;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandleHelpers;

import java.util.List;

/**
 * The {@link TypeHandle}s for LDLib2's UI system.
 *
 * <h2>Why these live apart from {@link com.lowdragmc.kilagraph.graph.type.KGTypeHandles}</h2>
 * {@code KGTypeHandles} carries what every KilaGraph graph needs — scalars, collections, Minecraft.
 * These nine are a binding to one library's UI toolkit, and the node ids that use them say so
 * ({@code ldlib2_ui_*}) precisely so a second UI system could be added later without a name clash.
 * Keeping the handles in the same package as the nodes keeps that seam in one place.
 *
 * <h2>All wire-only, deliberately</h2>
 * None of these types has an {@code AccessorRegistries} entry, so {@code NodeMetadata} routes their
 * ports through {@code withoutConfigurator()}: no inspector row, no embedded constant, no
 * serialisation. That is correct rather than a limitation — a {@code UIElement} is a live object
 * graph with a parent pointer and a taffy node id, and "the literal value of this pin" is not a
 * thing a user could author for it. Every one of them arrives over a wire from the node that made it.
 *
 * <p>Consequently no {@code setCustomDefaultValue} either. A default is read when a port builds its
 * embedded constant, which these never do; supplying one would only invite a caller to treat
 * {@code null} as "empty element" somewhere it means "nothing was wired".</p>
 *
 * <p>Colour <em>is</em> set here, and has to be: LDLib2 caches colour lazily <b>per handle
 * instance</b>, so a colour attached after anything has asked for one is silently dropped. The
 * palette is a deliberate family — blues for structure, greens for style, warm tones for the
 * event/sync half — so a UI wire reads as a UI wire on a busy canvas.</p>
 */
public final class KGUITypeHandles {

    /** A node in the UI tree. The type most UI nodes take and return. */
    public static final TypeHandle UI_ELEMENT;
    /** A whole UI: a root element plus the stylesheets it was constructed with. */
    public static final TypeHandle UI;
    /** A serialised UI snapshot — an NBT tree plus style references. Copyable and sendable. */
    public static final TypeHandle UI_TEMPLATE;
    /** A live UI instance bound to a player, screen and sync manager. */
    public static final TypeHandle MODULAR_UI;
    /** A parsed LSS stylesheet. */
    public static final TypeHandle STYLESHEET;
    /** One dispatched UI event, as handed to a listener. */
    public static final TypeHandle UI_EVENT;
    /** A registered synchronised value. */
    public static final TypeHandle SYNC_VALUE;
    /** A two-way data binding between a UI element and a server-side source. */
    public static final TypeHandle UI_BINDING;
    /** A handle for calling a registered RPC event on the other side. */
    public static final TypeHandle RPC;

    static {
        UI_ELEMENT = ui(UIElement.class, "UIElement", 0xFF5E9CD3);
        UI = ui(UI.class, "UI", 0xFF4A7EBB);
        UI_TEMPLATE = ui(UITemplate.class, "UITemplate", 0xFF7FB3E0);
        MODULAR_UI = ui(ModularUI.class, "ModularUI", 0xFF3B6EA5);
        STYLESHEET = ui(Stylesheet.class, "Stylesheet", 0xFF7EC8A0);
        UI_EVENT = ui(UIEvent.class, "UIEvent", 0xFFE0A24A);
        SYNC_VALUE = ui(SyncValue.class, "SyncValue", 0xFFD2795E);
        UI_BINDING = ui(IBinding.class, "UIBinding", 0xFFC96A8A);
        RPC = ui(RPCEmitter.class, "RPC", 0xFFB07ACF);
    }

    private KGUITypeHandles() {}

    /**
     * Mints one handle, fully described in the single call that creates it.
     *
     * <p>{@code fromType} rather than {@code customType}: the identification becomes the class name,
     * so {@code KGTypeHandles.handleFor(UIElement.class)} resolves to this very handle with no
     * override registration. That is what lets a node just declare
     * {@code @InputPort public UIElement element} and get the right pin.</p>
     */
    private static TypeHandle ui(Class<?> javaType, String display, int colour) {
        TypeHandle handle = TypeHandleHelpers.fromType(javaType, display);
        TypeHandleHelpers.setCustomColor(handle, colour);
        return handle;
    }

    /** Every handle here, for {@code BlueprintGraph.getSupportTypes()}. */
    public static List<TypeHandle> all() {
        return List.of(UI_ELEMENT, UI, UI_TEMPLATE, MODULAR_UI, STYLESHEET,
                UI_EVENT, SYNC_VALUE, UI_BINDING, RPC);
    }

    /** Force static init from elsewhere, before any node class is scanned. */
    public static void init() {
    }
}
