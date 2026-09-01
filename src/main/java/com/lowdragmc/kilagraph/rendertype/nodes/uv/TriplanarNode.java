package com.lowdragmc.kilagraph.rendertype.nodes.uv;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.kilagraph.rendertype.gui.ChoiceConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.List;

/**
 * Unity's Triplanar: projects {@code texture} onto the surface along the three world axes and blends the
 * three samples by the surface normal, avoiding uv seams/stretching. {@link StageAffinity#FRAGMENT_ONLY}
 * (samples a texture). Unconnected {@code position}/{@code normal} default to the mesh model-space position
 * and world normal (both overridable, as in Unity); {@code texture} unconnected → the missing-texture
 * sampler.
 *
 * <p>Unity's <b>type</b> option is offered. <b>default</b> blends the three samples as colour. <b>normal</b>
 * treats them as tangent-space normal maps and blends them into a <em>world</em> normal with the whiteout
 * method: each plane's sample is re-oriented against the geometric normal, then swizzled back onto its axis.
 * Worth knowing: this mode builds its basis out of the three projection axes, so unlike every other
 * normal-mapping path it needs <b>no mesh tangent at all</b> — it is the one that stays exact in the vertex
 * stage's worst case and on meshes with no usable uv. The output port stays vec4 either way (the normal rides
 * in {@code xyz}); wiring it into a vec3 input truncates to {@code .xyz} automatically.</p>
 *
 * <p>Caveat: the default {@code position} is model space while the default {@code normal} is world space —
 * consistent for an unrotated mesh; wire matching-space inputs if you need exactness under rotation.</p>
 */
@NodeAttribute(name = "rt_triplanar", group = "rendertype_uv", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class TriplanarNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_triplanar.tooltip");
    }

    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.FRAGMENT_ONLY;
    }

    private static final List<String> TYPES = List.of("default", "normal");

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("type", TypeHandles.STRING).withDefaultValue("default")
                .withTooltips(Tooltips.of("kg.node.rt_triplanar.option.type.tooltip"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, TYPES, TriplanarNode::label)).build();
    }

    @Override
    public List<String> optionChoices(String optionId) {
        return "type".equals(optionId) ? TYPES : List.of();
    }

    private static String label(String type) {
        return "normal".equals(type) ? "Normal" : "Default";
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("texture", RenderTypeGraphTypes.SAMPLER2D).withoutConfigurator();
        context.addInputPort("position", RenderTypeGraphTypes.VEC3).withoutConfigurator();
        context.addInputPort("normal", RenderTypeGraphTypes.VEC3).withoutConfigurator();
        context.addInputPort("tile", TypeHandles.FLOAT).withDefaultValue(1f);
        context.addInputPort("blend", TypeHandles.FLOAT).withDefaultValue(1f);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String tex = (ctx.isConnected("texture") ? ctx.input("texture") : ctx.missingSampler()).code();
        ShaderExpr position = ctx.isConnected("position") ? ctx.input("position") : ctx.meshPosition();
        ShaderExpr normal = ctx.isConnected("normal") ? ctx.input("normal") : ctx.meshNormal();
        String tile = ctx.input("tile").code();
        String blend = ctx.input("blend").code();

        ShaderExpr uvw = ctx.temp(GlslType.VEC3, "(" + position.code() + " * " + tile + ")");
        ShaderExpr w = ctx.temp(GlslType.VEC3, "pow(abs(" + normal.code() + "), vec3(" + blend + "))");
        ShaderExpr wn = ctx.temp(GlslType.VEC3, "(" + w.code() + " / (" + w.code() + ".x + " + w.code() + ".y + " + w.code() + ".z))");
        String x = "texture(" + tex + ", " + uvw.code() + ".zy)";
        String y = "texture(" + tex + ", " + uvw.code() + ".xz)";
        String z = "texture(" + tex + ", " + uvw.code() + ".xy)";

        if ("normal".equals(type())) {
            ctx.output("out", triplanarNormal(ctx, normal, wn, x, y, z));
            return;
        }
        String code = "(" + x + " * " + wn.code() + ".x + " + y + " * " + wn.code() + ".y + " + z + " * " + wn.code() + ".z)";
        ctx.output("out", new ShaderExpr(code, GlslType.VEC4));
    }

    /**
     * The whiteout triplanar normal blend (Unity's Triplanar with Type = Normal). Each plane's sample is
     * unpacked to a signed tangent-space normal, then whiteout-blended against the geometric normal's two
     * in-plane components — {@code abs()} on z keeps the plane facing outward whichever side we see — and
     * finally swizzled so each plane's local xy lands on the two world axes it actually spans.
     */
    private ShaderExpr triplanarNormal(ShaderCompileContext ctx, ShaderExpr normal, ShaderExpr wn,
                                       String x, String y, String z) {
        ShaderExpr n = ctx.temp(GlslType.VEC3, "normalize(" + normal.code() + ")");
        ShaderExpr nx = ctx.temp(GlslType.VEC3, unpack(x));
        ShaderExpr ny = ctx.temp(GlslType.VEC3, unpack(y));
        ShaderExpr nz = ctx.temp(GlslType.VEC3, unpack(z));
        String bx = "vec3(" + nx.code() + ".xy + " + n.code() + ".zy, abs(" + nx.code() + ".z) * " + n.code() + ".x)";
        String by = "vec3(" + ny.code() + ".xy + " + n.code() + ".xz, abs(" + ny.code() + ".z) * " + n.code() + ".y)";
        String bz = "vec3(" + nz.code() + ".xy + " + n.code() + ".xy, abs(" + nz.code() + ".z) * " + n.code() + ".z)";
        String sum = "(" + bx + ").zyx * " + wn.code() + ".x + ("
                + by + ").xzy * " + wn.code() + ".y + ("
                + bz + ").xyz * " + wn.code() + ".z";
        return new ShaderExpr("vec4(normalize(" + sum + "), 1.0)", GlslType.VEC4);
    }

    /** A [0,1] normal-map sample as a signed tangent-space normal (the Normal Unpack node's math). */
    private static String unpack(String sample) {
        return "((" + sample + ").xyz * 2.0 - 1.0)";
    }

    /** The selected blend type, defaulting to {@code default} for an absent or unrecognised option. */
    private String type() {
        INodeOption opt = getNodeOptionById("type");
        Object raw = opt == null ? null : opt.tryGetValue(Object.class).result().orElse(null);
        return raw instanceof String s && TYPES.contains(s) ? s : "default";
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    @Override
    public String glslExample() {
        return """
                vec3 uvw = position * tile;
                vec3 w = pow(abs(normal), vec3(blend));
                w /= (w.x + w.y + w.z);
                out = texture(tex, uvw.zy) * w.x
                    + texture(tex, uvw.xz) * w.y
                    + texture(tex, uvw.xy) * w.z;""";
    }
}
