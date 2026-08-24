package com.lowdragmc.kilagraph.blueprint.nodes.ui.ctx;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.UIActions;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The UI a graph is running inside, and which side it is running on.
 *
 * <h2>Reading the side is fine. Branching the structure on it is not.</h2>
 * {@code ldlib2_ui_side} exists because plenty of decisions legitimately differ: only the server can
 * change the world, only the client can start an animation, and only the client has a screen size.
 *
 * <p>What it must <b>not</b> gate is the shape of the UI tree or the set of sync values and RPCs
 * registered on it. Those are identified on the wire by registration order, so a graph that skips an
 * element on one side shifts every id after it and quietly decodes the rest of the UI's traffic into
 * the wrong slots. If a panel has nothing to show on the server, build it anyway — LDLib2's own
 * guards already make its layout and styling free there.</p>
 */
public final class UIContextNodes {

    private static final String GROUP = "ui/context";

    private UIContextNodes() {
    }

    /**
     * Which side this evaluation is on.
     *
     * <p>Derived from the current thread, not from any UI: it answers correctly even before a
     * {@code ModularUI} exists. Both outputs are false on a thread that is neither, which does happen
     * — a resource reload, a worker.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_side", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Side extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_side.tooltip");
        }

        @OutputPort public boolean isClient;
        @OutputPort public boolean isServer;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("isClient", LDLib2.isRemote());
            ctx.setOutput("isServer", LDLib2.isServer());
        }
    }

    /**
     * The live UI an element belongs to.
     *
     * <p>Null until the element is added to a mounted tree, which is why most nodes that need one
     * take it as an input instead of deriving it: during a build there is nothing to derive.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_modular_of", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ModularOf extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_modular_of.tooltip");
        }

        @InputPort public UIElement element;
        @OutputPort public ModularUI mui;
        @OutputPort public boolean mounted;

        @Override
        public void evaluate(EvalContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            ModularUI mui = element == null ? null : element.getModularUI();
            ctx.setOutput("mui", mui);
            ctx.setOutput("mounted", mui != null);
        }
    }

    /** Everything about a live UI a graph is likely to ask for. */
    @NodeAttribute(name = "ldlib2_ui_mui_info", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class MuiInfo extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_mui_info.tooltip");
        }

        @InputPort public ModularUI mui;
        @OutputPort public Player player;
        @OutputPort public UIElement root;
        @OutputPort public int screenWidth;
        @OutputPort public int screenHeight;
        @OutputPort public float width;
        @OutputPort public float height;
        @OutputPort public float leftPos;
        @OutputPort public float topPos;
        @OutputPort public long tick;
        @OutputPort public UIElement focused;
        @OutputPort public UIElement hovered;

        @Override
        public void evaluate(EvalContext ctx) {
            ModularUI mui = ctx.getInput("mui", ModularUI.class, null);
            ctx.setOutput("player", mui == null ? null : mui.player);
            ctx.setOutput("root", mui == null ? null : mui.ui.rootElement);
            ctx.setOutput("screenWidth", mui == null ? 0 : mui.getScreenWidth());
            ctx.setOutput("screenHeight", mui == null ? 0 : mui.getScreenHeight());
            ctx.setOutput("width", mui == null ? 0f : mui.getWidth());
            ctx.setOutput("height", mui == null ? 0f : mui.getHeight());
            ctx.setOutput("leftPos", mui == null ? 0f : mui.getLeftPos());
            ctx.setOutput("topPos", mui == null ? 0f : mui.getTopPos());
            // The UI's own tick counter, not the world's: it starts at zero when the UI opens, which
            // is what an animation or a timeout in a UI actually wants to measure against.
            ctx.setOutput("tick", mui == null ? 0L : mui.getTickCounter());
            ctx.setOutput("focused", mui == null ? null : mui.getFocusedElement());
            ctx.setOutput("hovered", mui == null ? null : mui.getLastHoveredElement());
        }
    }

    /**
     * Closes the UI.
     *
     * <p>Goes through the player's container, which is what makes it work from either side and for
     * both a menu-backed UI and a plain screen: closing the container is the one action both paths
     * agree on, and the client's screen follows.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_close", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Close extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_close.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public ModularUI mui;
        @InputPort public UIElement element;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            ModularUI mui = ctx.getInput("mui", ModularUI.class, null);
            if (mui == null) {
                UIElement element = UIActions.element(ctx, "element");
                if (element != null) mui = element.getModularUI();
            }
            Player player = mui == null ? null : mui.player;
            if (player == null) {
                UIActions.done(ctx, false);
                return;
            }
            player.closeContainer();
            UIActions.done(ctx, true);
        }
    }
}
