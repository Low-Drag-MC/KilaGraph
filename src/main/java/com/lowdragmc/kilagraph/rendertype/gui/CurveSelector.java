package com.lowdragmc.kilagraph.rendertype.gui;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.CurveValue;
import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Menu;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.lowdraglib2.math.curve.ExplicitCubicBezierCurve2;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The interactive curve editor popup for a {@code CURVE} value ({@link CurveValue}): a bounds column
 * (upper/lower remap range) plus a bezier graph canvas — draggable
 * key points and (green) control points, double-click on the curve to insert a key, right-click for
 * lock-control-points / remove-point. Edits mutate a private working copy; every change notifies the
 * listener with a fresh deep-copied {@link CurveValue}.
 */
public class CurveSelector extends UIElement {
    public final UIElement graphView = new UIElement();
    public final TextField upperBound = new TextField();
    public final TextField lowerBound = new TextField();

    /** The working state (deep-copied in {@link #setValue}, deep-copied out in {@link #getValue}). */
    protected List<ExplicitCubicBezierCurve2> segments = CurveValue.defaultValue().segments();
    protected float lower = 0f;
    protected float upper = 1f;

    protected final List<UIElement> pointsUI = new ArrayList<>();
    protected boolean lockControlPoint = true;
    protected int selectedPoint = -1;
    protected final UIElement controlPoint1 = new UIElement();
    protected final UIElement controlPoint2 = new UIElement();
    protected Consumer<CurveValue> onChanged;

    public CurveSelector() {
        getLayout().flexDirection(FlexDirection.ROW);
        getLayout().height(100);

        upperBound.setNumbersOnlyFloat(-1e9f, 1e9f);
        upperBound.setText(String.valueOf(upper));
        upperBound.setTextResponder(text -> {
            try {
                upper = Float.parseFloat(text);
            } catch (NumberFormatException ignored) {
                return;
            }
            notifyChanged();
        });
        upperBound.layout(layout -> {
            layout.widthPercent(100);
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.top(0);
        });
        lowerBound.setNumbersOnlyFloat(-1e9f, 1e9f);
        lowerBound.setText(String.valueOf(lower));
        lowerBound.setTextResponder(text -> {
            try {
                lower = Float.parseFloat(text);
            } catch (NumberFormatException ignored) {
                return;
            }
            notifyChanged();
        });
        lowerBound.layout(layout -> {
            layout.widthPercent(100);
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.bottom(0);
        });

        var boundContainer = new UIElement().layout(layout -> {
            layout.heightPercent(100);
            layout.width(40);
            layout.marginRight(2);
        }).addChildren(upperBound, lowerBound);

        graphView.layout(layout -> {
            layout.heightPercent(100);
            layout.flex(1);
        }).style(style -> style.backgroundTexture(this::drawGraphView))
                .addEventListener(UIEvents.MOUSE_DOWN, this::onGraphMouseDown)
                .addEventListener(UIEvents.DOUBLE_CLICK, this::onGraphDoubleClick)
                .addChildren(controlPoint1, controlPoint2);

        addChildren(boundContainer, graphView);

        setupControlPoint(controlPoint1, true);
        setupControlPoint(controlPoint2, false);
        refreshGraph();
    }

    // ---- value plumbing ------------------------------------------------------------------------

    public CurveSelector setOnCurveChangeListener(Consumer<CurveValue> listener) {
        this.onChanged = listener;
        return this;
    }

    public CurveValue getValue() {
        return new CurveValue(segments, lower, upper).copy();
    }

    public CurveSelector setValue(CurveValue value, boolean notify) {
        var copied = value.copy();
        this.segments = copied.segments();
        if (this.segments.isEmpty()) {
            this.segments = CurveValue.defaultValue().segments();
        }
        this.lower = copied.lower();
        this.upper = copied.upper();
        this.upperBound.setText(String.valueOf(upper), false);
        this.lowerBound.setText(String.valueOf(lower), false);
        this.selectedPoint = -1;
        refreshGraph();
        if (notify) notifyChanged();
        return this;
    }

    protected void notifyChanged() {
        if (onChanged != null) onChanged.accept(getValue());
    }

    // ---- interaction ---------------------------------------------------------------------------

    private void setupControlPoint(UIElement controlPoint, boolean isLeft) {
        controlPoint.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.width(2);
            layout.height(2);
            layout.marginLeft(-1);
            layout.marginTop(-1);
        }).style(style -> style.backgroundTexture(ColorPattern.GREEN.rectTexture())).setDisplay(false)
                .addEventListener(UIEvents.MOUSE_DOWN, event -> event.currentElement.startDrag(null, null))
                .addEventListener(UIEvents.MOUSE_ENTER, event ->
                        event.currentElement.style(style -> style.overlayTexture(ColorPattern.RED.borderTexture(1))))
                .addEventListener(UIEvents.MOUSE_LEAVE, event ->
                        event.currentElement.style(style -> style.overlayTexture(IGuiTexture.EMPTY)))
                .addEventListener(UIEvents.DRAG_SOURCE_UPDATE, event -> {
                    var result = draggingPoint(event, false);
                    if (isLeft) {
                        // controlPoint1: the incoming tangent (c1 of the segment left of the selected key).
                        result.x = Math.min(result.x, segments.get(selectedPoint - 1).p1.x);
                        if (selectedPoint > 0) {
                            segments.get(selectedPoint - 1).c1.set(result);
                            if (lockControlPoint && selectedPoint < segments.size()) {
                                segments.get(selectedPoint).c0
                                        .set(new Vector2f(segments.get(selectedPoint).p0).mul(2).sub(result));
                            }
                        }
                    } else {
                        // controlPoint2: the outgoing tangent (c0 of the segment right of the selected key).
                        result.x = Math.max(result.x, segments.get(selectedPoint).p0.x);
                        if (selectedPoint < segments.size()) {
                            segments.get(selectedPoint).c0.set(result);
                            if (lockControlPoint && selectedPoint - 1 >= 0) {
                                segments.get(selectedPoint - 1).c1
                                        .set(new Vector2f(segments.get(selectedPoint).p0).mul(2).sub(result));
                            }
                        }
                    }
                    updateControlPoints();
                    notifyChanged();
                });
    }

    private TreeBuilder.Menu createMenu() {
        var menu = TreeBuilder.Menu.start();
        menu.leaf(lockControlPoint ? Icons.CHECK_SPRITE : IGuiTexture.EMPTY, "kg.curve.lock_control_points",
                () -> lockControlPoint = !lockControlPoint);
        if (selectedPoint != -1 && pointsUI.size() > 1) {
            menu.leaf("ldlib.gui.editor.menu.remove", () -> {
                if (selectedPoint >= 0 && selectedPoint < pointsUI.size()) {
                    if (selectedPoint == 0) {
                        segments.removeFirst();
                    } else if (selectedPoint < segments.size()) {
                        segments.get(selectedPoint - 1).p1.set(segments.get(selectedPoint).p1);
                        segments.get(selectedPoint - 1).c1.set(segments.get(selectedPoint).c0);
                        segments.remove(selectedPoint);
                    } else {
                        segments.removeLast();
                    }
                    notifyChanged();
                }
                selectedPoint = -1;
                refreshGraph();
            });
        }
        return menu;
    }

    private void onGraphMouseDown(UIEvent event) {
        if (event.button == 1) {
            var menu = createMenu();
            if (!menu.isEmpty()) {
                this.addChild(new Menu<>(createMenu().build(), TreeBuilder.Menu::uiProvider)
                        .setOnClose(graphView::focus)
                        .setHoverTextureProvider(TreeBuilder.Menu::hoverTextureProvider)
                        .setOnNodeClicked(TreeBuilder.Menu::handle)
                        .layout(layout -> {
                            layout.left(event.x - this.getContentX());
                            layout.top(event.y - this.getContentY());
                        }));
            }
        }
    }

    private void onGraphDoubleClick(UIEvent event) {
        var x = (event.x - graphView.getContentX()) / graphView.getContentWidth();
        var y = segments.getFirst().p0.y;
        var found = x < segments.getFirst().p0.x;
        var index = 0;
        if (!found) {
            for (var curve : segments) {
                index++;
                if (x >= curve.p0.x && x <= curve.p1.x) {
                    y = curve.getPoint((x - curve.p0.x) / (curve.p1.x - curve.p0.x)).y;
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            index++;
            y = segments.getLast().p1.y;
        }
        var position = getPointPosition(new Vector2f(x, y), graphView.getContentX(), graphView.getContentY(),
                graphView.getContentWidth(), graphView.getContentHeight());
        if (isMouseOver((int) (position.x - 2), (int) (position.y - 2), 4, 4, event.x, event.y)) {
            if (index == 0) {
                var right = segments.getFirst().p0;
                var rightCP = segments.getFirst().c0;
                segments.addFirst(new ExplicitCubicBezierCurve2(
                        new Vector2f(x, y),
                        new Vector2f(x + 0.1f, y),
                        new Vector2f(right.x + (right.x - rightCP.x), right.y + (right.y - rightCP.y)),
                        right));
            } else if (index > segments.size()) {
                var left = segments.getLast().p1;
                var leftCP = segments.getLast().c1;
                segments.add(new ExplicitCubicBezierCurve2(
                        left,
                        new Vector2f(left.x + (left.x - leftCP.x), left.y + (left.y - leftCP.y)),
                        new Vector2f(x - 0.1f, y),
                        new Vector2f(x, y)));
            } else {
                var curve = segments.get(index - 1);
                segments.add(index, new ExplicitCubicBezierCurve2(
                        new Vector2f(x, y),
                        new Vector2f(x + 0.1f, y),
                        new Vector2f(curve.c1),
                        new Vector2f(curve.p1)));
                curve.c1.set(x - 0.1f, y);
                curve.p1.set(x, y);
            }
            notifyChanged();
            selectedPoint = index;
            refreshGraph();
        }
    }

    private @NotNull Vector2f draggingPoint(UIEvent event, boolean clamp) {
        var x = event.x - graphView.getContentX();
        var y = event.y - graphView.getContentY();
        var width = graphView.getContentWidth();
        var height = graphView.getContentHeight();
        var percentX = clamp ? Mth.clamp(x / width, 0f, 1f) : x / width;
        var percentY = clamp ? Mth.clamp(y / height, 0f, 1f) : y / height;
        event.currentElement.layout(layout -> {
            layout.leftPercent(percentX * 100);
            layout.topPercent(percentY * 100);
        });
        return new Vector2f(percentX, 1 - percentY);
    }

    public void refreshGraph() {
        pointsUI.forEach(graphView::removeChild);
        pointsUI.clear();
        for (int i = 0; i < segments.size(); i++) {
            var curve = segments.get(i);
            if (i == 0) {
                var ui = createPointUI(0, curve.p0);
                graphView.addChild(ui);
                pointsUI.add(ui);
            }
            var ui = createPointUI(i + 1, curve.p1);
            graphView.addChild(ui);
            pointsUI.add(ui);
        }
        updateControlPoints();
    }

    private UIElement createPointUI(int index, Vector2f point) {
        return new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.width(4);
            layout.height(4);
            layout.marginLeft(-2);
            layout.marginTop(-2);
            layout.leftPercent(point.x * 100);
            layout.topPercent((1 - point.y) * 100);
        }).style(style -> style.backgroundTexture(ColorPattern.LIGHT_GRAY.rectTexture()))
                .addEventListener(UIEvents.MOUSE_DOWN, event -> {
                    setSelected(index);
                    event.currentElement.startDrag(null, null);
                }).addEventListener(UIEvents.MOUSE_ENTER, event ->
                        event.currentElement.style(style -> style.overlayTexture(ColorPattern.RED.borderTexture(1))))
                .addEventListener(UIEvents.MOUSE_LEAVE, event ->
                        event.currentElement.style(style -> style.overlayTexture(IGuiTexture.EMPTY)))
                .addEventListener(UIEvents.DRAG_SOURCE_UPDATE, event -> {
                    var result = draggingPoint(event, true);
                    // Clamp x within the neighbours' range (leaving a tiny gap so adjacent points never become
                    // exactly equal -> avoids the 0/0 in getCurveY), so an overshoot renders as a clean
                    // vertical jump instead of an inverted/crossed-over segment.
                    var lo = index > 0 ? segments.get(index - 1).p0.x + 0.001f : 0f;
                    var hi = index < segments.size() ? segments.get(index).p1.x - 0.001f : 1f;
                    result.x = Math.max(lo, Math.min(hi, result.x));
                    event.currentElement.layout(layout -> layout.leftPercent(result.x * 100));
                    if (index < segments.size()) {
                        var offset = new Vector2f(result.x - segments.get(index).p0.x, result.y - segments.get(index).p0.y);
                        segments.get(index).p0.set(result);
                        segments.get(index).c0.add(offset);
                    }
                    if (index > 0) {
                        var offset = new Vector2f(result.x - segments.get(index - 1).p1.x, result.y - segments.get(index - 1).p1.y);
                        segments.get(index - 1).p1.set(result);
                        segments.get(index - 1).c1.add(offset);
                    }
                    updateControlPoints();
                    notifyChanged();
                });
    }

    public void setSelected(int index) {
        if (selectedPoint == index) return;
        if (selectedPoint >= 0 && selectedPoint < pointsUI.size()) {
            pointsUI.get(selectedPoint).style(style -> style.backgroundTexture(ColorPattern.LIGHT_GRAY.rectTexture()));
        }
        if (index >= 0 && index < pointsUI.size()) {
            selectedPoint = index;
            pointsUI.get(selectedPoint).style(style -> style.backgroundTexture(ColorPattern.ORANGE.rectTexture()));
        } else {
            selectedPoint = -1;
        }
        updateControlPoints();
    }

    private void updateControlPoints() {
        if (selectedPoint >= 0 && selectedPoint < pointsUI.size()) {
            if (selectedPoint > 0) {
                controlPoint1.layout(layout -> {
                    layout.leftPercent(segments.get(selectedPoint - 1).c1.x * 100);
                    layout.topPercent((1 - segments.get(selectedPoint - 1).c1.y) * 100);
                }).setDisplay(true);
            } else {
                controlPoint1.setDisplay(false);
            }
            if (selectedPoint < segments.size()) {
                controlPoint2.layout(layout -> {
                    layout.leftPercent(segments.get(selectedPoint).c0.x * 100);
                    layout.topPercent((1 - segments.get(selectedPoint).c0.y) * 100);
                }).setDisplay(true);
            } else {
                controlPoint2.setDisplay(false);
            }
        } else {
            controlPoint1.setDisplay(false);
            controlPoint2.setDisplay(false);
        }
    }

    // ---- drawing --------------------------------------------------------------------------------

    private Vector2f getPointPosition(Vector2f coord, float x, float y, float width, float height) {
        return new Vector2f(x + width * coord.x, y + height * (1 - coord.y));
    }

    private void drawGraphView(GuiGraphics graphics, float mouseX, float mouseY, float x, float y, float width, float height, float partialTick) {
        DrawerHelper.drawSolidRect(graphics, x, y, width, height, ColorPattern.BLACK.color);
        for (int i = 0; i < 6; i++) {
            DrawerHelper.drawSolidRect(graphics, x + i * width / 6, y, 1, height, ColorPattern.T_GRAY.color);
        }
        for (int i = 0; i < 6; i++) {
            DrawerHelper.drawSolidRect(graphics, x, y + i * height / 6, width, 1, ColorPattern.T_GRAY.color);
        }
        // render lines
        var points = segments.stream().flatMap(curve -> curve.getPoints(100).stream()
                .map(coord -> getPointPosition(coord, x, y, width, height)).toList().stream())
                .collect(Collectors.toList());
        DrawerHelper.drawLines(graphics, points, -1, -1, 0.5f);
        Collections.reverse(points);
        DrawerHelper.drawLines(graphics, points, -1, -1, 0.5f);
        // render outer lines (the held first/last y outside the key range)
        if (segments.getFirst().p0.x > 0) {
            DrawerHelper.drawLines(graphics, List.of(
                    getPointPosition(new Vector2f(0, segments.getFirst().p0.y), x, y, width, height),
                    getPointPosition(segments.getFirst().p0, x, y, width, height)),
                    ColorPattern.T_RED.color, ColorPattern.T_RED.color, 0.3f);
        }
        if (segments.getLast().p1.x < 1) {
            DrawerHelper.drawLines(graphics, List.of(
                    getPointPosition(new Vector2f(1, segments.getLast().p1.y), x, y, width, height),
                    getPointPosition(segments.getLast().p1, x, y, width, height)),
                    ColorPattern.T_RED.color, ColorPattern.T_RED.color, 0.3f);
        }
        // render control lines
        if (selectedPoint >= 0) {
            if (selectedPoint > 0) { // render left
                var curve = segments.get(selectedPoint - 1);
                DrawerHelper.drawLines(graphics, List.of(
                        getPointPosition(curve.c1, x, y, width, height),
                        getPointPosition(curve.p1, x, y, width, height)),
                        ColorPattern.T_GREEN.color, ColorPattern.T_GREEN.color, 0.3f);
            }
            if (selectedPoint < segments.size()) { // render right
                var curve = segments.get(selectedPoint);
                DrawerHelper.drawLines(graphics, List.of(
                        getPointPosition(curve.c0, x, y, width, height),
                        getPointPosition(curve.p0, x, y, width, height)),
                        ColorPattern.T_GREEN.color, ColorPattern.T_GREEN.color, 0.3f);
            }
        }
    }

    /** Draw a curve's normalized 0..1 shape as a polyline — the inline preview strip's texture. */
    public static void drawCurveLine(GuiGraphics graphics, float x, float y, float width, float height, CurveValue value) {
        var points = new ArrayList<Vector2f>();
        for (int i = 0; i < width; i++) {
            float coordX = i / width;
            points.add(new Vector2f(coordX, value.getCurveY(coordX)));
        }
        if (points.size() < 2) return;
        points.add(new Vector2f(1, value.getCurveY(1)));
        DrawerHelper.drawLines(graphics,
                points.stream().map(coord -> new Vector2f(x + width * coord.x, y + height * (1 - coord.y))).toList(),
                ColorPattern.T_RED.color, ColorPattern.T_RED.color, 0.5f);
    }
}
