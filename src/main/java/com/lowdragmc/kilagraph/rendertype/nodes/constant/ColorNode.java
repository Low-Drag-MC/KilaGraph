package com.lowdragmc.kilagraph.rendertype.nodes.constant;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslFormat;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * A constant color literal: outputs a {@code vec4} edited via the built-in {@link TypeHandles#COLOR}
 * configurator (an ARGB color picker). The value is baked into the shader as a constant. For a
 * runtime-adjustable color, expose a global graph variable (which compiles to a material uniform)
 * rather than using this node.
 */
@NodeAttribute(name = "rt_color", group = "rendertype_constant", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ColorNode extends ShaderNode {
    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        // TypeHandles.COLOR (ARGB int) carries a ColorConfigurator, so this renders a color picker.
        context.addOption("color", TypeHandles.COLOR)
                .withDefaultValue(-1) // white, full alpha
                .build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("color", RenderTypeGraphTypes.VEC4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        int argb = ctx.option("color", Integer.class, -1);
        float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        String code = "vec4(" + GlslFormat.f(r) + ", " + GlslFormat.f(g) + ", "
                + GlslFormat.f(b) + ", " + GlslFormat.f(a) + ")";
        ctx.output("color", new ShaderExpr(code, GlslType.VEC4));
    }
}
