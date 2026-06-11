package com.lowdragmc.kilagraph.rendertype.compiler;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-node compilation context, mirroring {@code EvalContext} but emitting GLSL instead of values.
 * A node's {@link ShaderNode#compile(ShaderCompileContext)} reads inputs with {@link #input(String)}
 * and publishes outputs with {@link #output(String, ShaderExpr)}; side declarations (includes,
 * uniforms, samplers, raw statements) go through the helpers here. All emission targets the
 * compiler's <em>current stage</em>.
 */
public final class ShaderCompileContext {

    private final ShaderGraphCompiler compiler;
    private final NodeModel node;
    final Map<String, ShaderExpr> outputs = new HashMap<>();

    ShaderCompileContext(ShaderGraphCompiler compiler, NodeModel node) {
        this.compiler = compiler;
        this.node = node;
    }

    // ---- input reads -------------------------------------------------------------------------

    /** Pull an input port as a GLSL expression, converted to the port's declared type. */
    public ShaderExpr input(String portId) {
        PortModel pm = node.getInputsById().get(portId);
        if (pm == null) throw new IllegalArgumentException("No input port '" + portId + "' on " + node.getUid());
        GlslType expected = GlslType.of(pm.getDataTypeHandle());
        return compiler.pullInput(pm, expected);
    }

    /**
     * Pull an input port, but if it is unconnected return {@code builtinDefault} instead of reading
     * an embedded constant. Used by varying blocks whose unconnected inputs fall back to a builtin
     * vertex attribute/expression.
     */
    public ShaderExpr inputOr(String portId, ShaderExpr builtinDefault) {
        PortModel pm = node.getInputsById().get(portId);
        if (pm == null) throw new IllegalArgumentException("No input port '" + portId + "' on " + node.getUid());
        if (!pm.isConnected()) return builtinDefault;
        GlslType expected = GlslType.of(pm.getDataTypeHandle());
        return compiler.pullInput(pm, expected);
    }

    /** Whether the given input port has a wire. */
    public boolean isConnected(String portId) {
        PortModel pm = node.getInputsById().get(portId);
        return pm != null && pm.isConnected();
    }

    // ---- vertex attributes -------------------------------------------------------------------

    /**
     * A raw vertex-attribute reference (e.g. {@code Color}) when its element is in the active vertex
     * format, else {@code fallback} — so a node default referencing an attribute the user removed degrades
     * to a safe constant instead of producing undefined-variable GLSL. The substitution is recorded and
     * surfaced as an editor warning.
     */
    public ShaderExpr attribute(com.lowdragmc.kilagraph.rendertype.format.KGVertexElement element,
                                GlslType type, ShaderExpr fallback) {
        return compiler.attribute(element, type, fallback);
    }

    /** Whether the given vertex element is declared in the active vertex format. */
    public boolean hasAttribute(com.lowdragmc.kilagraph.rendertype.format.KGVertexElement element) {
        return compiler.hasAttribute(element);
    }

    /** Record that a referenced attribute is absent from the format (for callers building the ref themselves). */
    public void markMissingAttribute(String attribName) {
        compiler.markMissingAttribute(attribName);
    }

    // ---- output writes -----------------------------------------------------------------------

    /** Publish an output port's GLSL expression (converted/hoisted by the compiler after compile). */
    public void output(String portId, ShaderExpr expr) {
        outputs.put(portId, expr);
    }

    // ---- option reads ------------------------------------------------------------------------

    public <T> T option(String optionId, Class<T> type, T defaultIfMissing) {
        INodeOption opt = node.getNodeOptionById(optionId);
        if (opt == null) return defaultIfMissing;
        Object raw = opt.tryGetValue(Object.class).result().orElse(null);
        if (type.isInstance(raw)) return type.cast(raw);
        if (raw instanceof Number n) {
            if (type == Integer.class) return type.cast(n.intValue());
            if (type == Float.class) return type.cast(n.floatValue());
            if (type == Double.class) return type.cast(n.doubleValue());
            if (type == Long.class) return type.cast(n.longValue());
        }
        if (type == String.class && raw != null) return type.cast(raw.toString());
        return defaultIfMissing;
    }

    // ---- emission helpers --------------------------------------------------------------------

    /** Register a {@code #moj_import <path>} include in the current stage. */
    public void include(String path) {
        compiler.addInclude(path);
    }

    /**
     * Declare use of a Minecraft builtin uniform block (e.g. {@code Fog}, {@code Projection},
     * {@code Lighting}) backed by an include that defines its std140 layout. Registers the include in
     * the current stage and records the UBO so the runtime pipeline declares the matching binding.
     */
    public void useMinecraftUniform(String uboName, String includePath) {
        compiler.addInclude(includePath);
        compiler.useBuiltinUbo(uboName);
    }

    /**
     * Register a material UBO field and return a {@link ShaderExpr} referencing it. The field is
     * exposed to users as a per-material uniform.
     */
    public ShaderExpr uniform(String name, GlslType type) {
        String accessor = compiler.layout().addField(name, type);
        return new ShaderExpr(accessor, type);
    }

    /** Register a sampler (builtin or material) and return a sampler-typed expression for it. */
    public ShaderExpr sampler(String name) {
        compiler.layout().addSampler(name);
        return new ShaderExpr(name, GlslType.SAMPLER2D);
    }

    /**
     * Allocate a per-node {@code uniform sampler2D} for a texture value and bake its texture + sampler
     * params as the material default. Used by {@code TextureNode}; the value is a
     * {@code RenderTypeGraphTypes.Sampler2DValue}.
     */
    public ShaderExpr textureSampler(Object value) {
        return compiler.textureSampler(node, value);
    }

    /** The fallback sampler for an unconnected Sampler2D input — bound to the MC missing-texture. */
    public ShaderExpr missingSampler() {
        return compiler.missingSampler();
    }

    /** Vanilla overlay sampler ({@code Sampler1}); flags the pipeline to enable overlay binding. */
    public ShaderExpr overlaySampler() {
        return compiler.overlaySampler();
    }

    /** Vanilla lightmap sampler ({@code Sampler2}); flags the pipeline to enable lightmap binding. */
    public ShaderExpr lightmapSampler() {
        return compiler.lightmapSampler();
    }

    /** Allocate a temp variable in the current stage holding {@code expr}, returning a reference. */
    public ShaderExpr temp(GlslType type, String code) {
        return compiler.hoist(type, code);
    }

    /** The interpolated mesh uv (vec2). In a per-node preview this is the preview quad's uv. */
    public ShaderExpr meshUv() {
        return compiler.meshUv();
    }

    /**
     * Read a fixed interpolated varying in the fragment stage, ensuring the vsh writes it with
     * {@code vshDefault} (unless a vertex varying block already produced it). In a per-node preview
     * (no vertex stage) returns {@code previewDefault}. Used by {@code FragmentInputNode}s.
     */
    public ShaderExpr varyingInput(String name, GlslType type,
                                   java.util.function.Supplier<ShaderExpr> vshDefault, ShaderExpr previewDefault) {
        return compiler.varyingInput(name, type, vshDefault, previewDefault);
    }

    /** World time in seconds, from KilaGraph's engine-globals block (we update it each frame). */
    public ShaderExpr engineTime() {
        return compiler.engineTime();
    }

    /** Minecraft's builtin {@code Globals.GameTime} (normalised day fraction, wraps every MC day). */
    public ShaderExpr mcGameTime() {
        return compiler.mcGameTime();
    }

    /** Append a raw statement to the current stage's main() body. */
    public void line(String statement) {
        compiler.line(statement);
    }

    /** Convert an expression to a target GLSL type using the standard float/vector rules. */
    public ShaderExpr convert(ShaderExpr expr, GlslType target) {
        return compiler.convert(expr, target);
    }

    public NodeModel node() {
        return node;
    }

    @Nullable
    public NodeModel getNodeModel() {
        return node;
    }
}
