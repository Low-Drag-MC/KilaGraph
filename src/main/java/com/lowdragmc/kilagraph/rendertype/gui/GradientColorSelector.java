package com.lowdragmc.kilagraph.rendertype.gui;

import com.lowdragmc.lowdraglib2.gui.texture.DynamicTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableUIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ColorSelector;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelperClient;
import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.lowdraglib2.utils.ColorUtils;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import lombok.Getter;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.function.Consumer;

/**
 * An interactive Unity-style gradient editor widget. A live gradient bar with two indicator rails: alpha keys above,
 * colour keys below. Double-click a rail to add a key, drag an indicator to move it, right-click to
 * remove it, left-click to select it and edit its value in the embedded {@link ColorSelector} (alpha
 * slider for alpha keys, full picker for colour keys). Operates directly on a {@link GradientColor}
 * (its {@code aP}/{@code rgbP} key lists), notifying listeners on every edit.
 */
public class GradientColorSelector extends BindableUIElement<GradientColor> {
    public final UIElement gradientPreview = new UIElement();
    public final UIElement alphaIndicatorContainer = new UIElement();
    public final UIElement rgbIndicatorContainer = new UIElement();
    public final ColorSelector colorSelector = new ColorSelector();
    @Getter
    protected GradientColor value = new GradientColor();

    // runtime
    private boolean isSelectAlpha = false;
    private int selectedPoint = -1;

    public GradientColorSelector() {
        getLayout().gapAll(1);
        gradientPreview.layout(layout -> {
            layout.marginLeft(2.5f);
            layout.marginRight(2.5f);
        }).addChildren(
                alphaIndicatorContainer.layout(layout -> {
                    layout.flexDirection(FlexDirection.ROW);
                    layout.height(5);
                    layout.widthPercent(100);
                    layout.alignItems(AlignItems.CENTER);
                }).addEventListener(UIEvents.DOUBLE_CLICK, event -> createNewIndicator(event, true)),
                new UIElement().layout(layout -> {
                    layout.height(10);
                    layout.widthPercent(100);
                }).style(style -> style.backgroundTexture(GuiTexture.of(
                        (GUIContext ctx, float x, float y, float w, float h) -> drawGradientBar(ctx, x, y, w, h, value)))),
                rgbIndicatorContainer.layout(layout -> {
                    layout.flexDirection(FlexDirection.ROW);
                    layout.height(5);
                    layout.widthPercent(100);
                    layout.alignItems(AlignItems.CENTER);
                }).addEventListener(UIEvents.DOUBLE_CLICK, event -> createNewIndicator(event, false))
        );
        colorSelector.setOnColorChangeListener(this::onColorChanged);
        refreshGradient();
        addChildren(gradientPreview, colorSelector);
    }

    private void onColorChanged(int color) {
        if (selectedPoint >= 0) {
            if (isSelectAlpha && selectedPoint < value.getAP().size()) {
                ((Vector2f) value.getAP().get(selectedPoint)).y = ColorUtils.alpha(color);
                notifyListeners();
            } else if (!isSelectAlpha && selectedPoint < value.getRgbP().size()) {
                var rgbP = (Vector4f) value.getRgbP().get(selectedPoint);
                rgbP.y = ColorUtils.red(color);
                rgbP.z = ColorUtils.green(color);
                rgbP.w = ColorUtils.blue(color);
                notifyListeners();
            }
        }
    }

    private void refreshGradient() {
        alphaIndicatorContainer.clearAllChildren();
        rgbIndicatorContainer.clearAllChildren();
        for (var alphaP : value.getAP()) {
            alphaIndicatorContainer.addChild(new UIElement().layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.leftPercent(alphaP.x() * 100);
                layout.marginLeft(-2.5f);
                layout.width(5);
                layout.height(5);
            }).style(style -> style.backgroundTexture(DynamicTexture.of(() -> (isSelectAlpha && selectedPoint == value.getAP().indexOf(alphaP)) ?
                    Icons.DOWN_ARROW_NO_BAR_S : Icons.DOWN_ARROW_NO_BAR_S_WHITE)))
                    .addEventListener(UIEvents.DRAG_SOURCE_UPDATE, event -> onDragIndicator(event, true, value.getAP().indexOf(alphaP)))
                    .addEventListener(UIEvents.MOUSE_DOWN, event -> onIndicatorMouseDown(event, true, value.getAP().indexOf(alphaP))));
        }
        for (var rgbP : value.getRgbP()) {
            rgbIndicatorContainer.addChild(new UIElement().layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.leftPercent(rgbP.x() * 100);
                layout.marginLeft(-2.5f);
                layout.width(5);
                layout.height(5);
            }).style(style -> style.backgroundTexture(DynamicTexture.of(() -> (!isSelectAlpha && selectedPoint == value.getRgbP().indexOf(rgbP)) ?
                    Icons.UP_ARROW_NO_BAR_S : Icons.UP_ARROW_NO_BAR_S_WHITE)))
                    .addEventListener(UIEvents.DRAG_SOURCE_UPDATE, event -> onDragIndicator(event, false, value.getRgbP().indexOf(rgbP)))
                    .addEventListener(UIEvents.MOUSE_DOWN, event -> onIndicatorMouseDown(event, false, value.getRgbP().indexOf(rgbP))));
        }
        refreshColorSelector();
    }

    private void createNewIndicator(UIEvent event, boolean isAlpha) {
        if (event.button == 0) {
            var percent = (event.x - gradientPreview.getPositionX()) / gradientPreview.getSizeWidth();
            percent = Math.max(0, Math.min(1, percent));
            if (isAlpha) {
                value.addAlpha(percent, value.getAlpha(percent));
                notifyListeners();
            } else {
                var rgb = value.getRGB(percent);
                value.addRGB(percent, rgb.x, rgb.y, rgb.z);
                notifyListeners();
            }
            refreshGradient();
        }
    }

    private void onDragIndicator(UIEvent event, boolean isAlpha, int point) {
        var percent = (event.x - gradientPreview.getPositionX()) / gradientPreview.getSizeWidth();
        percent = Math.max(0, Math.min(1, percent));
        var offset = percent * 100;
        var p = percent;
        event.currentElement.layout(layout -> layout.leftPercent(offset));
        if (isAlpha) {
            if (point >= 0 && point < value.getAP().size()) {
                ((Vector2f) value.getAP().get(point)).x = p;
                value.getAP().sort((a, b) -> Float.compare(a.x(), b.x()));
                notifyListeners();
            }
        } else {
            if (point >= 0 && point < value.getRgbP().size()) {
                ((Vector4f) value.getRgbP().get(point)).x = p;
                value.getRgbP().sort((a, b) -> Float.compare(a.x(), b.x()));
                notifyListeners();
            }
        }
    }

    private void onIndicatorMouseDown(UIEvent event, boolean isSelectAlpha, int selectedPoint) {
        if (event.button == 0) {
            this.isSelectAlpha = isSelectAlpha;
            this.selectedPoint = selectedPoint;
            refreshColorSelector();
            event.currentElement.startDrag(null, null);
        } else if (event.button == 1) {
            if (isSelectAlpha && selectedPoint >= 0 && selectedPoint < value.getAP().size() && value.getAP().size() > 1) {
                value.getAP().remove(selectedPoint);
            } else if (!isSelectAlpha && selectedPoint >= 0 && selectedPoint < value.getRgbP().size() && value.getRgbP().size() > 1) {
                value.getRgbP().remove(selectedPoint);
            }
            this.isSelectAlpha = isSelectAlpha;
            this.selectedPoint = -1;
            notifyListeners();
            refreshGradient();
        }
    }

    private void refreshColorSelector() {
        if (selectedPoint >= 0) {
            if (isSelectAlpha && selectedPoint < value.getAP().size()) {
                colorSelector.setColor(ColorUtils.color(value.getAP().get(selectedPoint).y(), 1, 1, 1), false);
                colorSelector.colorPreview.setDisplay(false);
                colorSelector.colorSlider.setDisplay(false);
                colorSelector.hsbButton.setDisplay(false);
                colorSelector.alphaSlider.setDisplay(true);
            } else if (!isSelectAlpha && selectedPoint < value.getRgbP().size()) {
                var rgb = value.getRgbP().get(selectedPoint);
                colorSelector.setColor(ColorUtils.color(1, rgb.y(), rgb.z(), rgb.w()), false);
                colorSelector.colorPreview.setDisplay(true);
                colorSelector.colorSlider.setDisplay(true);
                colorSelector.hsbButton.setDisplay(true);
                colorSelector.alphaSlider.setDisplay(false);
            } else {
                hideColorSelector();
            }
        } else {
            hideColorSelector();
        }
    }

    private void hideColorSelector() {
        colorSelector.colorPreview.setDisplay(false);
        colorSelector.colorSlider.setDisplay(false);
        colorSelector.hsbButton.setDisplay(false);
        colorSelector.alphaSlider.setDisplay(false);
    }

    public GradientColorSelector setOnColorGradientChangeListener(Consumer<GradientColor> listener) {
        registerValueListener(listener);
        return this;
    }

    @Override
    public GradientColorSelector setValue(GradientColor value, boolean notify) {
        if (this.value == value) return this;
        this.value = value;
        if (notify) {
            notifyListeners();
        }
        refreshGradient();
        return this;
    }

    /** Draw a smooth gradient bar by sampling {@link GradientColor#getColor} across 1px vertical strips. */
    static void drawGradientBar(GUIContext ctx, float x, float y, float width, float height, GradientColor gradient) {
        int steps = Math.max(1, (int) width);
        for (int i = 0; i < steps; i++) {
            float t = steps == 1 ? 0f : i / (float) (steps - 1);
            DrawerHelperClient.drawSolidRect(ctx, x + i, y, 1, height, gradient.getColor(t));
        }
    }
}
