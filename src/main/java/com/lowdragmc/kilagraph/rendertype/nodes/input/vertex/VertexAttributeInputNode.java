package com.lowdragmc.kilagraph.rendertype.nodes.input.vertex;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.kilagraph.rendertype.format.IVertexFormatDependentNode;
import com.lowdragmc.kilagraph.rendertype.format.KGVertexElement;
import com.lowdragmc.kilagraph.rendertype.format.KGVertexElements;
import com.lowdragmc.kilagraph.rendertype.gui.VertexAttributeConfigurator;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Reads one raw vertex-format attribute (a vsh {@code in} variable) chosen from a dropdown listing every
 * registered {@link KGVertexElement} — so a single node covers Position/Color/UV0/UV1/UV2/Normal and any
 * element other mods register, instead of one node class per attribute. The output port's type follows the
 * chosen element ({@code vec3}/{@code vec4}/{@code vec2}; {@code ivec2} attributes like UV1/UV2 are read as
 * {@code vec2}). Changing the element re-defines the node (the option's change callback calls
 * {@code defineNode()}), re-typing the port.
 *
 * <p>Vertex-only (the attribute exists only in the vsh): pulling it into the fragment stage is a stage
 * error — route it through a vertex varying block or a {@code FragmentInputNode}. As an
 * {@link IVertexFormatDependentNode} the graph also flags it when its chosen element isn't in the format.</p>
 */
@NodeAttribute(name = "rt_in_vertex_attribute", group = "rendertype_input/vertex", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class VertexAttributeInputNode extends ShaderNode implements IVertexFormatDependentNode {

    private static final String OPTION = "element";
    private static final String DEFAULT_KEY = "position";

    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.VERTEX_ONLY;
    }

    @Override
    public String requiredElementKey() {
        return currentKey();
    }

    /** The currently-selected element key (read from the option; falls back to position). */
    private String currentKey() {
        INodeOption opt = getNodeOptionById(OPTION);
        if (opt == null) return DEFAULT_KEY;
        Object raw = opt.tryGetValue(Object.class).result().orElse(null);
        return raw instanceof String s && !s.isEmpty() ? s : DEFAULT_KEY;
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        // String option holding the element key, edited via the registry dropdown (client-only, lazy).
        context.addOption(OPTION, TypeHandles.STRING)
                .withDefaultValue(DEFAULT_KEY)
                .withConfigurable((vc, type) -> VertexAttributeConfigurator.build(vc))
                .build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("out", outputType(currentKey()));
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        KGVertexElement element = KGVertexElements.get(currentKey());
        if (element == null) {
            ctx.output("out", new ShaderExpr("vec4(0.0)", GlslType.VEC4));
            return;
        }
        // Per-node preview: there is no real vertex stage (just a flat quad drawn through the preview vsh,
        // which forwards Position→vPos and UV0→vUv). Substitute a fragment-safe default for the attribute
        // so the thumbnail compiles and shows something meaningful instead of undefined-variable GLSL.
        if (ctx.isPreview()) {
            ctx.output("out", previewExpr(element));
            return;
        }
        // An explicit read of an attribute the format doesn't declare would emit undefined-variable GLSL;
        // fall back to a zero of the output type (and record it — the graph also flags this node via
        // IVertexFormatDependentNode validation, so the user gets a node-level error too).
        if (!ctx.hasAttribute(element)) {
            ctx.markMissingAttribute(element.attribName());
            ctx.output("out", zeroExpr(element.glslType()));
            return;
        }
        ctx.output("out", attributeExpr(element));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    /** The graph port type for an element's GLSL type (ivec2 attributes are surfaced as vec2). */
    private static TypeHandle outputType(String key) {
        KGVertexElement element = KGVertexElements.get(key);
        String glsl = element == null ? "vec4" : element.glslType();
        return switch (glsl) {
            case "float" -> TypeHandles.FLOAT;
            case "vec2", "ivec2" -> RenderTypeGraphTypes.VEC2;
            case "vec3" -> RenderTypeGraphTypes.VEC3;
            default -> RenderTypeGraphTypes.VEC4; // vec4 and anything unrecognised
        };
    }

    /**
     * A fragment-safe preview value for an attribute (the per-node preview has no vertex stage). The
     * preview vsh forwards {@code Position→vPos} and {@code UV0→vUv}, so position/uv read those
     * interpolants; the rest fall back to neutral constants (a flat quad has no real color/normal).
     */
    private static ShaderExpr previewExpr(KGVertexElement element) {
        return switch (element.key()) {
            case "position" -> new ShaderExpr("vPos", GlslType.VEC3);
            case "uv0" -> new ShaderExpr("vUv", GlslType.VEC2);
            case "color" -> new ShaderExpr("vec4(1.0)", GlslType.VEC4);
            case "normal" -> new ShaderExpr("vec3(0.0, 0.0, 1.0)", GlslType.VEC3);
            default -> zeroExpr(element.glslType()); // uv1/uv2/lineWidth/custom → zero of its type
        };
    }

    /** A zero of the element's surfaced GLSL type, for an explicit read of an absent attribute. */
    private static ShaderExpr zeroExpr(String glslType) {
        return switch (glslType) {
            case "float" -> new ShaderExpr("0.0", GlslType.FLOAT);
            case "vec2", "ivec2" -> new ShaderExpr("vec2(0.0)", GlslType.VEC2);
            case "vec3" -> new ShaderExpr("vec3(0.0)", GlslType.VEC3);
            default -> new ShaderExpr("vec4(0.0)", GlslType.VEC4);
        };
    }

    /** The output GLSL expression for an element (ivec2 attributes are cast to vec2 to be wire-usable). */
    private static ShaderExpr attributeExpr(KGVertexElement element) {
        return switch (element.glslType()) {
            case "float" -> new ShaderExpr(element.attribName(), GlslType.FLOAT);
            case "vec2" -> new ShaderExpr(element.attribName(), GlslType.VEC2);
            case "ivec2" -> new ShaderExpr("vec2(" + element.attribName() + ")", GlslType.VEC2);
            case "vec3" -> new ShaderExpr(element.attribName(), GlslType.VEC3);
            default -> new ShaderExpr(element.attribName(), GlslType.VEC4);
        };
    }
}
