package com.lowdragmc.kilagraph.graph.util;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NodeTooltipHelperTest {

    @Test
    void helperDoesNotOwnNodeTooltipRegistry() {
        assertThrows(NoSuchFieldException.class, () -> NodeTooltipHelper.class.getDeclaredField("NODE_TOOLTIP_IDS"));
        assertThrows(NoSuchFieldException.class, () -> NodeTooltipHelper.class.getDeclaredField("EXTRA_TOOLTIP_KEYS"));
        assertThrows(NoSuchMethodException.class, () -> NodeTooltipHelper.class.getDeclaredMethod("translationKeys"));
    }

    @Test
    void baseClassesDoNotInferTooltipsFromGroups() {
        assertNull(new PlainMcNode().tooltip());
        assertNull(new PlainSceneNode().tooltip());
    }

    @Test
    void rejectsUnsupportedTranslationArguments() {
        assertDoesNotThrow(() -> Component.translatable(
                "kg.node.rt_screen_position.tooltip",
                Component.literal("Default"),
                "raw",
                1,
                true
        ));
        assertThrows(IllegalArgumentException.class,
                () -> Component.translatable("kg.node.rt_screen_position.tooltip", List.of("bad")));
    }

    @NodeAttribute(name = "mc_plain_test", group = "mc", graphTypes = BlueprintGraph.class)
    private static final class PlainMcNode extends AnnotatedNode {
        @Nullable Component tooltip() {
            return getNodeTooltip();
        }
    }

    @NodeAttribute(name = "rt_plain_scene_test", group = "rendertype_scene", graphTypes = RenderTypeGraph.class)
    private static final class PlainSceneNode extends ShaderNode {
        @Nullable Component tooltip() {
            return getNodeTooltip();
        }

        @Override
        public void compile(ShaderCompileContext ctx) {}
    }
}
