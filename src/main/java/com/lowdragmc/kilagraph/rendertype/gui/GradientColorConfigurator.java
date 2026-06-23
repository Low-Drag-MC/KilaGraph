package com.lowdragmc.kilagraph.rendertype.gui;

import com.lowdragmc.lowdraglib2.configurator.ui.ValueConfigurator;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.style.StyleOrigin;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.lowdragmc.lowdraglib2.math.GradientColor;
import dev.vfyjxf.taffy.style.TaffyPosition;

import javax.annotation.Nonnull;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A {@link GradientColor} editor configurator (Unity-style gradient bar). Modeled on LDLib2's
 * {@link com.lowdragmc.lowdraglib2.configurator.ui.ColorConfigurator}: a live preview swatch in the
 * config row that, on click, pops up the interactive {@link GradientColorSelector} (draggable colour /
 * alpha keys, double-click to add, right-click to remove, embedded colour picker). The popup mutates the
 * working {@link GradientColor} in place and notifies on every edit.
 *
 * <p>Constructed with {@code forceUpdate = false} on purpose: the selector mutates one working instance
 * and we never re-read the supplier mid-edit, so dragging a key is never clobbered by a passive refresh.</p>
 */
public class GradientColorConfigurator extends ValueConfigurator<GradientColor> {
    public final GradientColorSelector gradientSelector;
    public final UIElement preview;

    public GradientColorConfigurator(String name, Supplier<GradientColor> supplier, Consumer<GradientColor> onUpdate,
                                     @Nonnull GradientColor defaultValue, boolean forceUpdate) {
        super(name, supplier, onUpdate, defaultValue, forceUpdate);
        setCopiable(GradientColor::copy);

        if (value == null) {
            value = defaultValue;
        }

        this.gradientSelector = new GradientColorSelector();
        this.gradientSelector.style(style -> {
            style.setPipelineState(StyleOrigin.DEFAULT);
            style.backgroundTexture(Sprites.RECT_SOLID);
            style.setPipelineState(StyleOrigin.INLINE);
        });
        this.gradientSelector.addClass("panel_bg");
        this.gradientSelector.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.widthPercent(100);
            layout.maxWidth(170);
            layout.minWidth(120);
            layout.paddingAll(4);
        });
        this.gradientSelector.setOnColorGradientChangeListener(this::updateValueActively);
        this.gradientSelector.setFocusable(true);
        this.gradientSelector.setEnforceFocus(e -> hide());
        this.gradientSelector.addEventListener(UIEvents.LAYOUT_CHANGED, e -> gradientSelector.adaptPositionToScreen());

        preview = new UIElement();
        inlineContainer.addChildren(preview.layout(layout -> {
            layout.setPipelineState(StyleOrigin.DEFAULT);
            layout.height(14);
            layout.paddingAll(3);
            layout.setPipelineState(StyleOrigin.INLINE);
        }).style(style -> {
            style.setPipelineState(StyleOrigin.DEFAULT);
            style.backgroundTexture(Sprites.RECT_RD_SOLID);
            style.setPipelineState(StyleOrigin.INLINE);
        }).addClass("configurator_preview_bg").addChildren(new UIElement()
                .layout(layout -> layout.heightPercent(100))
                .style(style -> style.backgroundTexture(GuiTexture.of(
                        (GUIContext ctx, float x, float y, float w, float h) ->
                                GradientColorSelector.drawGradientBar(ctx, x, y, w, h, value == null ? defaultValue : value))))
                .addEventListener(UIEvents.MOUSE_DOWN, this::onClick)));

        this.gradientSelector.setValue(value, false);
    }

    @Override
    protected void onValueUpdatePassively(GradientColor newValue) {
        if (newValue == null) newValue = defaultValue;
        if (newValue.equals(value)) return;
        super.onValueUpdatePassively(newValue);
        this.gradientSelector.setValue(newValue, false);
    }

    public void show() {
        if (this.gradientSelector.getParent() != null) {
            return;
        }
        var mui = getModularUI();
        if (mui != null) {
            var root = mui.ui.rootElement;
            root.addChild(gradientSelector.layout(layout -> {
                var worldMouse = preview.getWorldMouse(preview.getPositionX(), preview.getPositionY());
                var layoutOffset = root.worldToLocalLayoutOffset(worldMouse);
                layout.left(layoutOffset.x);
                layout.top(layoutOffset.y);
                layout.width(preview.getSizeWidth());
            }));
            this.gradientSelector.focus();
        }
    }

    public void hide() {
        var parent = this.gradientSelector.getParent();
        if (parent != null) {
            this.gradientSelector.blur();
            parent.removeChild(this.gradientSelector);
        }
    }

    protected void onClick(UIEvent event) {
        if (this.gradientSelector.getParent() != null) {
            hide();
        } else {
            show();
        }
    }
}
