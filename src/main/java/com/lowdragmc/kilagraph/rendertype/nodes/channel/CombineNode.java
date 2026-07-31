package com.lowdragmc.kilagraph.rendertype.nodes.channel;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity's Combine: assembles the four float channels {@code R/G/B/A} (default 0) into vectors, exposing
 * the {@code RGBA} (vec4), {@code RGB} (vec3) and {@code RG} (vec2) groupings as separate outputs.
 */
@NodeAttribute(name = "rt_combine", group = "rendertype_channel", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class CombineNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("r", TypeHandles.FLOAT);
        context.addInputPort("g", TypeHandles.FLOAT);
        context.addInputPort("b", TypeHandles.FLOAT);
        context.addInputPort("a", TypeHandles.FLOAT);
        context.addOutputPort("rgba", RenderTypeGraphTypes.VEC4);
        context.addOutputPort("rgb", RenderTypeGraphTypes.VEC3);
        context.addOutputPort("rg", RenderTypeGraphTypes.VEC2);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String r = ctx.input("r").code();
        String g = ctx.input("g").code();
        String b = ctx.input("b").code();
        String a = ctx.input("a").code();
        ctx.output("rgba", new ShaderExpr("vec4(" + r + ", " + g + ", " + b + ", " + a + ")", GlslType.VEC4));
        ctx.output("rgb", new ShaderExpr("vec3(" + r + ", " + g + ", " + b + ")", GlslType.VEC3));
        ctx.output("rg", new ShaderExpr("vec2(" + r + ", " + g + ")", GlslType.VEC2));
    }

    @Override
    protected String previewOutputPortId() {
        return "rgba";
    }

    @Override
    public String glslExample() {
        return """
                rgba = vec4(r, g, b, a);
                rgb  = vec3(r, g, b);
                rg   = vec2(r, g);""";
    }
}
