package com.lowdragmc.kilagraph.rendertype.gui;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.CurveValue;
import com.lowdragmc.lowdraglib2.configurator.ui.ValueConfigurator;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StyleOrigin;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import net.minecraft.client.gui.GuiGraphics;
import dev.vfyjxf.taffy.style.TaffyPosition;

import javax.annotation.Nonnull;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A {@link CurveValue} editor configurator. Modeled on {@link GradientColorConfigurator}: a live curve
 * preview strip in the config row that, on click, pops up the interactive {@link CurveSelector}
 * (draggable keys/tangents, bounds fields). The selector notifies a fresh deep-copied value on every
 * edit.
 *
 * <p>Constructed with {@code forceUpdate = false} on purpose: the selector edits one working state and
 * we never re-read the supplier mid-edit, so dragging a key is never clobbered by a passive refresh.</p>
 */
public class CurveValueConfigurator extends ValueConfigurator<CurveValue> {
    public final CurveSelector curveSelector;
    public final UIElement preview;

    public CurveValueConfigurator(String name, Supplier<CurveValue> supplier, Consumer<CurveValue> onUpdate,
                                  @Nonnull CurveValue defaultValue, boolean forceUpdate) {
        super(name, supplier, onUpdate, defaultValue, forceUpdate);
        setCopiable(CurveValue::copy);

        if (value == null) {
            value = defaultValue;
        }

        this.curveSelector = new CurveSelector();
        this.curveSelector.style(style -> {
            style.setPipelineState(StyleOrigin.DEFAULT);
            style.backgroundTexture(Sprites.RECT_SOLID);
            style.setPipelineState(StyleOrigin.INLINE);
        });
        this.curveSelector.addClass("panel_bg");
        this.curveSelector.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.widthPercent(100);
            layout.maxWidth(240);
            layout.minWidth(160);
            layout.paddingAll(4);
        });
        this.curveSelector.setOnCurveChangeListener(this::updateValueActively);
        this.curveSelector.setFocusable(true);
        this.curveSelector.setEnforceFocus(e -> hide());
        this.curveSelector.addEventListener(UIEvents.LAYOUT_CHANGED, e -> curveSelector.adaptPositionToScreen());

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
                .style(style -> style.backgroundTexture(new IGuiTexture() {
                    @Override
                    public void draw(GuiGraphics graphics, float mouseX, float mouseY, float x, float y, float width, float height, float partialTicks) {
                        CurveSelector.drawCurveLine(graphics, x, y, width, height, value == null ? defaultValue : value);
                    }
                }))
                .addEventListener(UIEvents.MOUSE_DOWN, this::onClick)));

        this.curveSelector.setValue(value, false);
    }

    @Override
    protected void onValueUpdatePassively(CurveValue newValue) {
        if (newValue == null) newValue = defaultValue;
        if (newValue.equals(value)) return;
        super.onValueUpdatePassively(newValue);
        this.curveSelector.setValue(newValue, false);
    }

    public void show() {
        if (this.curveSelector.getParent() != null) {
            return;
        }
        var mui = getModularUI();
        if (mui != null) {
            var root = mui.ui.rootElement;
            root.addChild(curveSelector.layout(layout -> {
                var worldMouse = preview.getWorldMouse(preview.getPositionX(), preview.getPositionY());
                var layoutOffset = root.worldToLocalLayoutOffset(worldMouse);
                layout.left(layoutOffset.x);
                layout.top(layoutOffset.y);
                layout.width(Math.max(preview.getSizeWidth(), 200));
            }));
            this.curveSelector.focus();
        }
    }

    public void hide() {
        var parent = this.curveSelector.getParent();
        if (parent != null) {
            this.curveSelector.blur();
            parent.removeChild(this.curveSelector);
        }
    }

    protected void onClick(UIEvent event) {
        if (this.curveSelector.getParent() != null) {
            hide();
        } else {
            show();
        }
    }
}
