package com.lowdragmc.kilagraph.rendertype.nodes.input;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Outputs the mesh's interpolated vertex colour (vec4). {@link StageAffinity#FRAGMENT_ONLY} (reads an
 * interpolated varying). The {@code lit} toggle picks between vanilla per-vertex diffuse lighting
 * ({@code minecraft_mix_light} — the entity default) and the raw, unlit {@code Color} attribute. Like the
 * {@code UV} node, it's a clean dedicated source; the per-vertex lighting it carries used to live in the
 * (now removed) vertex Color varying block.
 */
@NodeAttribute(name = "rt_vertex_color", group = "rendertype_input", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class VertexColorNode extends ShaderNode {
    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.FRAGMENT_ONLY;
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("lit", TypeHandles.BOOL).withDefaultValue(true).build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("out", RenderTypeGraphTypes.VEC4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.output("out", lit() ? ctx.litVertexColor() : ctx.meshColor());
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    private boolean lit() {
        INodeOption opt = getNodeOptionById("lit");
        Object raw = opt == null ? null : opt.tryGetValue(Object.class).result().orElse(null);
        return !(raw instanceof Boolean b) || b; // default lit
    }
}
