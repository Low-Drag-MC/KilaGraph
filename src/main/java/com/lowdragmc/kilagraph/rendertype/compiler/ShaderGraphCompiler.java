package com.lowdragmc.kilagraph.rendertype.compiler;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.format.KGVertexElement;
import com.lowdragmc.kilagraph.rendertype.format.KGVertexElements;
import com.lowdragmc.kilagraph.rendertype.runtime.KGEngineUniforms;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.IVariableNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.IVariable;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.ModifierFlags;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableScope;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.BlockNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ContextNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.SubgraphNodeModel;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Compiles a {@link RenderTypeGraph} into GLSL vertex/fragment sources plus the material uniform
 * layout. Demand-driven, mirroring {@code GraphExecutor}: starting from the fragment stage's
 * semantic blocks, the compiler pulls backward through the data graph, emitting hoisted temp
 * variables in dependency order.
 *
 * <p>Two stage scopes are maintained (vertex, fragment), each with its own body, temp counter and
 * memo cache. When fragment compilation reaches a vertex {@link IVaryingBlock}'s output, the
 * compiler treats it as a stage boundary: the varying is built once in the vertex scope and the
 * fragment scope receives a reference to the interpolated {@code in} variable.</p>
 */
public final class ShaderGraphCompiler {

    private static final String GLSL_VERSION = "#version 330";

    /** Per-stage emission state. */
    private static final class StageScope {
        final String tempPrefix;
        final StringBuilder body = new StringBuilder();
        final Set<String> includes = new LinkedHashSet<>();
        final Map<PortModel, ShaderExpr> cache = new IdentityHashMap<>();
        final Set<AbstractNodeModel> visiting = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        int tempCounter;

        StageScope(String tempPrefix) {
            this.tempPrefix = tempPrefix;
        }
    }

    private final RenderTypeGraph graph;
    private final StageScope vertex = new StageScope("v");
    private final StageScope fragment = new StageScope("f");
    private final MaterialUniformLayout layout = new MaterialUniformLayout();
    private final Set<String> builtinUbos = new LinkedHashSet<>();
    /** name -> type of varyings already built in the vertex shader. */
    private final Map<String, GlslType> varyings = new java.util.LinkedHashMap<>();
    /** Baked default values for EXPOSED variable uniforms: uniform field name -> std140 components. */
    private final Map<String, float[]> uniformDefaults = new LinkedHashMap<>();
    /** Baked default textures+params for Sampler2D samplers: sampler name -> {@link SamplerDefault}. */
    private final Map<String, SamplerDefault> samplerDefaults = new LinkedHashMap<>();
    /** EXPOSED variable display name -> its KG_Material field (name + type), for set-by-name uniform updates. */
    private final Map<String, MaterialUniformLayout.Field> variableUniformFields = new LinkedHashMap<>();
    /** Sampler2D variable display name -> its sampler uniform name, for set-by-name texture updates. */
    private final Map<String, String> variableSamplerNames = new LinkedHashMap<>();
    /** Variable declaration uid -> the sanitized, unique GLSL identifier chosen for it. */
    private final Map<UUID, String> variableNames = new HashMap<>();
    /** Texture node uid -> its allocated sampler uniform name. */
    private final Map<UUID, String> nodeSamplerNames = new HashMap<>();
    /** All GLSL identifiers already handed out to variables/constants, to keep them unique. */
    private final Set<String> usedVariableNames = new LinkedHashSet<>();
    /** Attribute names a node/block default referenced that aren't in the active vertex format (a safe
     *  constant was substituted) — surfaced as editor warnings so the user knows a default degraded. */
    private final Set<String> missingAttributes = new LinkedHashSet<>();

    /** Sampler name for an unconnected Sampler2D fallback — bound to the MC missing-texture. */
    public static final String MISSING_SAMPLER = "kg_MissingSampler";
    /** Whether an OverlayTextureNode referenced {@code Sampler1} (so the pipeline must enable overlay). */
    private boolean usesOverlay;
    /** Whether a LightMapTextureNode referenced {@code Sampler2} (so the pipeline must enable lightmap). */
    private boolean usesLightmap;

    private StageScope current;
    /** Preview mode: compile a single port onto a flat quad, substituting stage inputs with defaults. */
    private boolean preview;
    /** Whether any node referenced the engine-globals block ({@code KG_Globals}: Time, ...). */
    private boolean usesEngineGlobals;
    /** Stage-affinity violations found during traversal, keyed by node uid (first conflict per node). */
    private final Map<java.util.UUID, StageError> stageErrors = new java.util.LinkedHashMap<>();
    /**
     * Active subgraph-inlining bindings: one frame per nested {@link SubgraphNodeModel} we're inside,
     * mapping an inner READ-variable's declaration uid → the outer input expression bound to it.
     * The {@link IVariableNode} branch consults this so an inner READ variable resolves to its caller's
     * argument instead of the top-level const/uniform path.
     */
    private final Deque<Map<UUID, ShaderExpr>> bindingStack = new ArrayDeque<>();

    /** Fixed render state for per-node previews: opaque, depth-tested, no cull (so the quad always shows). */
    public static final RenderTypeGraph.Settings PREVIEW_SETTINGS = new RenderTypeGraph.Settings(
            com.lowdragmc.kilagraph.rendertype.format.VertexFormatPresets.POSITION_COLOR_TEX,
            RenderTypeGraph.Settings.VertexFormatMode.QUADS,
            RenderTypeGraph.Settings.BlendMode.OPAQUE,
            RenderTypeGraph.Settings.DepthTest.LEQUAL,
            true, false,
            RenderTypeGraph.Settings.OutputTarget.MAIN,
            false, false);

    public ShaderGraphCompiler(RenderTypeGraph graph) {
        this.graph = graph;
        // DynamicTransforms is always bound by RenderType.draw, so the pipeline must declare it.
        builtinUbos.add("DynamicTransforms");
    }

    // ---- public entry ------------------------------------------------------------------------

    public CompiledShaderGraph compile() {
        ContextNodeModel vertexStage = asContext(graph.getVertexStageModel(), "vertex");
        ContextNodeModel fragmentStage = asContext(graph.getFragmentStageModel(), "fragment");

        // 1) Fragment stage: pull from each fragment semantic block. This lazily builds the
        //    varyings it depends on in the vertex scope.
        current = fragment;
        FragmentOutputs out = new FragmentOutputs();
        for (BlockNodeModel block : fragmentStage.getBlocks()) {
            Node node = nodeOf(block);
            if (node instanceof IFragmentOutputBlock fb) {
                var ctx = new ShaderCompileContext(this, block);
                fb.emitFragment(ctx, out);
            }
        }

        // 2) Vertex position (gl_Position).
        current = vertex;
        ShaderExpr position = null;
        for (BlockNodeModel block : vertexStage.getBlocks()) {
            Node node = nodeOf(block);
            if (node instanceof IVertexPositionBlock pb) {
                var ctx = new ShaderCompileContext(this, block);
                position = convert(pb.compilePosition(ctx), GlslType.VEC4);
                break;
            }
        }
        if (position == null) {
            // No explicit position block — fall back to the standard MVP transform.
            addInclude("minecraft:dynamictransforms.glsl");
            addInclude("minecraft:projection.glsl");
            position = new ShaderExpr("ProjMat * ModelViewMat * vec4(Position, 1.0)", GlslType.VEC4);
        }
        line("gl_Position = " + position.code() + ";");

        String vsh = assembleVertex();
        String fsh = assembleFragment(out);
        return new CompiledShaderGraph(vsh, fsh, layout, new ArrayList<>(builtinUbos), usesEngineGlobals,
                new ArrayList<>(stageErrors.values()), graph.getSettings(),
                new LinkedHashMap<>(uniformDefaults), new LinkedHashMap<>(samplerDefaults),
                new LinkedHashMap<>(variableUniformFields), new LinkedHashMap<>(variableSamplerNames),
                usesOverlay, usesLightmap, new ArrayList<>(missingAttributes));
    }

    /**
     * Compile a single output port into a preview shader: a flat quad whose fragment colour is the
     * port's value (converted to vec4). Stage inputs that don't exist on a quad — vertex varyings,
     * mesh uv — are substituted with preview defaults; samplers and Minecraft UBOs work normally
     * because the preview is drawn through a real {@code RenderType} (so {@code bindDefaultUniforms}
     * applies). Used by per-node previews in the editor.
     */
    public CompiledShaderGraph compilePreview(PortModel outputPort) {
        preview = true;
        current = fragment;
        // The preview vsh provides Position + UV0 and passes uv through as vUv.
        builtinUbos.add("Projection"); // vsh uses ProjMat * ModelViewMat
        ShaderExpr value = previewValueOf(outputPort);
        if (value == null) value = new ShaderExpr("vec4(0.0)", GlslType.VEC4);
        ShaderExpr color = convert(value, GlslType.VEC4);

        String vsh = assemblePreviewVertex();
        String fsh = assemblePreviewFragment(color);
        return new CompiledShaderGraph(vsh, fsh, layout, new ArrayList<>(builtinUbos), usesEngineGlobals,
                new ArrayList<>(stageErrors.values()), PREVIEW_SETTINGS,
                new LinkedHashMap<>(uniformDefaults), new LinkedHashMap<>(samplerDefaults),
                new LinkedHashMap<>(variableUniformFields), new LinkedHashMap<>(variableSamplerNames),
                usesOverlay, usesLightmap, new ArrayList<>(missingAttributes));
    }

    /**
     * Resolve a port's value for preview. A varying-block output (e.g. TexCoord) has no ShaderNode to
     * evaluate; instead its preview value is its connected input compiled in the fragment scope, or —
     * when unconnected — the preview default for that varying (texcoord → quad uv).
     */
    @Nullable
    private ShaderExpr previewValueOf(PortModel outputPort) {
        Node owner = nodeOf(outputPort);
        if (owner instanceof IVaryingBlock vb
                && outputPort.getNodeModel() instanceof NodeModel nm) {
            // varying blocks expose a single input feeding the varying (same id family as the output)
            var inputs = nm.getInputsByDisplayOrder();
            if (!inputs.isEmpty() && inputs.getFirst().isConnected()) {
                return pullInput(inputs.getFirst(), vb.varyingType());
            }
            return previewVaryingDefault(vb.varyingName(), vb.varyingType());
        }
        return evaluateOutput(outputPort);
    }

    /** Default value for a vertex varying when previewing without a vertex stage. */
    private ShaderExpr previewVaryingDefault(String varyingName, GlslType type) {
        if ("texCoord0".equals(varyingName)) return new ShaderExpr("vUv", GlslType.VEC2);
        if ("vertexColor".equals(varyingName)) return new ShaderExpr("vec4(1.0)", GlslType.VEC4);
        return zero(type);
    }

    private static ShaderExpr zero(GlslType type) {
        return switch (type) {
            case FLOAT, INT, BOOL -> new ShaderExpr("0.0", type);
            case VEC2 -> new ShaderExpr("vec2(0.0)", GlslType.VEC2);
            case VEC3 -> new ShaderExpr("vec3(0.0)", GlslType.VEC3);
            case VEC4 -> new ShaderExpr("vec4(0.0)", GlslType.VEC4);
            case MAT4 -> new ShaderExpr("mat4(1.0)", GlslType.MAT4);
            case SAMPLER2D -> new ShaderExpr(MISSING_SAMPLER, GlslType.SAMPLER2D);
        };
    }

    /**
     * The interpolated mesh uv, routed through the {@code texCoord0} varying (vsh default {@code UV0}).
     * Preview: the quad's uv ({@code vUv}). Shares the single {@code texCoord0} varying with the TexCoord
     * vertex block / {@code FragmentTexCoordInput} node, so all uv readers see the same interpolant.
     */
    ShaderExpr meshUv() {
        return varyingInput("texCoord0", GlslType.VEC2,
                () -> attribute(KGVertexElements.UV0, GlslType.VEC2, new ShaderExpr("vec2(0.0)", GlslType.VEC2)),
                new ShaderExpr("vUv", GlslType.VEC2));
    }

    /** The element keys actually declared as {@code in} attributes in the current compile: the graph's
     *  composed vertex format, or — in preview — the fixed preview vsh's {@code Position}+{@code UV0}. */
    private Set<String> availableAttributes() {
        if (preview) return Set.of(KGVertexElements.POSITION.key(), KGVertexElements.UV0.key());
        return new java.util.HashSet<>(graph.getSettings().vertexFormatElements());
    }

    /** Whether the given vertex element is declared in the active vertex format (so its raw {@code in}
     *  attribute can be referenced without producing an undefined-variable shader). */
    boolean hasAttribute(KGVertexElement element) {
        return availableAttributes().contains(element.key());
    }

    /**
     * A raw vertex-attribute reference (e.g. {@code Color}) when its element is in the active vertex
     * format, else {@code fallback} — so a node/block <em>default</em> that references an attribute the
     * user removed degrades to a safe constant instead of emitting undefined-variable GLSL (which the GPU
     * rejects). Records the substituted attribute so the editor can warn about the degraded default.
     */
    ShaderExpr attribute(KGVertexElement element, GlslType type, ShaderExpr fallback) {
        if (hasAttribute(element)) return new ShaderExpr(element.attribName(), type);
        missingAttributes.add(element.attribName());
        return fallback;
    }

    /** Record that a referenced attribute is absent from the format (for callers that build the ref
     *  themselves, e.g. an explicit attribute node that casts {@code ivec2 → vec2}). */
    void markMissingAttribute(String attribName) {
        missingAttributes.add(attribName);
    }

    /**
     * Read a fixed interpolated varying in the fragment stage, ensuring the vsh writes it. In preview
     * (no vertex stage) returns {@code previewDefault}; otherwise declares + assigns the varying with
     * {@code vshDefault} (unless a vertex varying block already built it — first writer wins) and returns
     * a reference to it. Used by {@code FragmentInputNode}s and {@link #meshUv()}.
     */
    ShaderExpr varyingInput(String name, GlslType type,
                            java.util.function.Supplier<ShaderExpr> vshDefault, ShaderExpr previewDefault) {
        if (preview) return previewDefault;
        ensureVaryingWithDefault(name, type, vshDefault);
        return new ShaderExpr(name, type);
    }

    private void ensureVaryingWithDefault(String name, GlslType type,
                                          java.util.function.Supplier<ShaderExpr> vshDefault) {
        if (varyings.containsKey(name)) return; // already built (by a block or a prior reader)
        varyings.put(name, type);
        StageScope saved = current;
        current = vertex;
        try {
            ShaderExpr value = convert(vshDefault.get(), type);
            line(name + " = " + value.code() + ";");
        } finally {
            current = saved;
        }
    }

    /** World time in seconds from the {@code KG_Globals} engine block (updated by us each frame). */
    ShaderExpr engineTime() {
        usesEngineGlobals = true;
        return new ShaderExpr(KGEngineUniforms.timeAccessor(), GlslType.FLOAT);
    }

    /** Minecraft's builtin {@code Globals.GameTime} (day fraction). Bound by {@code bindDefaultUniforms}. */
    ShaderExpr mcGameTime() {
        addInclude("minecraft:globals.glsl");
        return new ShaderExpr("GameTime", GlslType.FLOAT);
    }

    /** The fallback sampler for an unconnected Sampler2D — declares it + bakes the MC missing-texture. */
    ShaderExpr missingSampler() {
        layout.addSampler(MISSING_SAMPLER);
        samplerDefaults.putIfAbsent(MISSING_SAMPLER, SamplerDefault.missing());
        return new ShaderExpr(MISSING_SAMPLER, GlslType.SAMPLER2D);
    }

    /** Vanilla overlay sampler ({@code Sampler1}); flags the pipeline to enable overlay binding. */
    ShaderExpr overlaySampler() {
        usesOverlay = true;
        return new ShaderExpr("Sampler1", GlslType.SAMPLER2D);
    }

    /** Vanilla lightmap sampler ({@code Sampler2}); flags the pipeline to enable lightmap binding. */
    ShaderExpr lightmapSampler() {
        usesLightmap = true;
        return new ShaderExpr("Sampler2", GlslType.SAMPLER2D);
    }

    // ---- traversal ---------------------------------------------------------------------------

    /** Pull an input port's value as a GLSL expression converted to {@code expected}. */
    ShaderExpr pullInput(PortModel inputPort, @Nullable GlslType expected) {
        GlslType target = expected != null ? expected : GlslType.of(inputPort.getDataTypeHandle());
        if (!inputPort.isConnected()) {
            GlslType declared = GlslType.of(inputPort.getDataTypeHandle());
            // An unconnected sampler can't be a literal — fall back to the missing-texture sampler.
            if (declared == GlslType.SAMPLER2D) return missingSampler();
            Object constant = readConstant(inputPort);
            String code = declared != null
                    ? GlslFormat.literal(constant, declared)
                    : "0.0";
            ShaderExpr lit = new ShaderExpr(code, declared != null ? declared : GlslType.FLOAT);
            return convert(lit, target);
        }
        // Resolve the real upstream output, FOLLOWING wire portals (an input wired through a portal is
        // physically connected to the portal's exit, not the source). getFirstConnectedPort() walks the
        // WirePortalModel entry/exit links; the raw getConnectedPorts() would stop at the portal node.
        PortModel outputPort = inputPort.getFirstConnectedPort() instanceof PortModel pm ? pm : null;
        if (outputPort == null) {
            return convert(new ShaderExpr("0.0", GlslType.FLOAT), target);
        }
        Node ownerNode = nodeOf(outputPort);

        // Varying boundary: a vertex varying block consumed from the fragment stage.
        if (current == fragment && ownerNode instanceof IVaryingBlock vb) {
            if (preview) {
                // No vertex stage in preview — substitute a sensible default for the interpolant.
                return convert(previewVaryingDefault(vb.varyingName(), vb.varyingType()), target);
            }
            ensureVaryingBuilt(outputPort, vb);
            ShaderExpr ref = new ShaderExpr(vb.varyingName(), vb.varyingType());
            return convert(ref, target);
        }

        ShaderExpr value = evaluateOutput(outputPort);
        if (value == null) value = new ShaderExpr("0.0", GlslType.FLOAT);
        return convert(value, target);
    }

    @Nullable
    private ShaderExpr evaluateOutput(PortModel outputPort) {
        ShaderExpr cached = current.cache.get(outputPort);
        if (cached != null) return cached;
        AbstractNodeModel owner = outputPort.getNodeModel() instanceof AbstractNodeModel a ? a : null;
        if (owner == null) return null;
        if (!current.visiting.add(owner)) {
            throw new ShaderCompileException("Cycle detected while compiling node " + owner.getUid());
        }
        try {
            evaluateNode(owner);
        } finally {
            current.visiting.remove(owner);
        }
        return current.cache.get(outputPort);
    }

    private void evaluateNode(AbstractNodeModel owner) {
        if (!(owner instanceof NodeModel nm)) return;
        // NGT built-in constant node (the generic "Constant" you drag a value into): not a ShaderNode,
        // so read its value and emit it as a GLSL literal. Mirrors GraphExecutor's IConstantNode case.
        if (owner instanceof com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.IConstantNode constant) {
            Object value = constant.tryGetValue(constant.getDataType()).result().orElse(null);
            for (PortModel outp : nm.getOutputsByDisplayOrder()) {
                GlslType decl = GlslType.of(outp.getDataTypeHandle());
                if (decl == null) continue;
                // SAMPLER2D is not a constant type (textures come from TextureNode) — guard defensively.
                current.cache.put(outp, decl == GlslType.SAMPLER2D
                        ? missingSampler() : hoist(decl, GlslFormat.literal(value, decl)));
            }
            return;
        }
        // NGT variable node (a Blackboard variable dragged into the canvas). A variable is one
        // shader-basic type; how it compiles depends on its scope:
        //   LOCAL/UNKNOWN -> bake the declared value inline (like a constant);
        //   EXPOSED       -> a KG_Material uniform field (default value baked at material build);
        //   Sampler2D     -> ALWAYS a uniform (opaque type cannot be a literal), with a default texture.
        if (owner instanceof IVariableNode varNode) {
            compileVariableNode(nm, varNode);
            return;
        }
        // Subgraph node: inline the inner ShaderFunctionGraph (mirrors GraphExecutor.evaluateSubgraph,
        // emitting GLSL instead of values). Inner READ vars bind to the outer input expressions; inner
        // WRITE vars become the outer output ports.
        if (owner instanceof SubgraphNodeModel sub) {
            compileSubgraphNode(sub, nm);
            return;
        }
        if (!(owner instanceof ICustomNodeModel cnm)) return;
        Node userNode = cnm.getNode();
        if (!(userNode instanceof ShaderNode sn)) return; // non-shader nodes contribute nothing
        // Stage inference: this node is being used in the current stage. Flag if its affinity forbids it.
        ShaderStage stage = current == vertex ? ShaderStage.VERTEX : ShaderStage.FRAGMENT;
        StageAffinity affinity = sn.stageAffinity();
        if (!affinity.allows(stage)) {
            stageErrors.putIfAbsent(nm.getUid(),
                    new StageError(nm.getUid(), sn.getDisplayName().getString(), stage, affinity));
        }
        var ctx = new ShaderCompileContext(this, nm);
        sn.compile(ctx);
        for (PortModel outp : nm.getOutputsByDisplayOrder()) {
            ShaderExpr raw = ctx.outputs.get(outp.getPortId());
            if (raw == null) continue;
            GlslType decl = GlslType.of(outp.getDataTypeHandle());
            if (decl == null) {
                current.cache.put(outp, raw);
                continue;
            }
            ShaderExpr conv = convert(raw, decl);
            if (decl == GlslType.SAMPLER2D) {
                current.cache.put(outp, conv); // opaque — cannot copy into a temp
            } else {
                current.cache.put(outp, hoist(decl, conv.code()));
            }
        }
    }

    /** Emit GLSL for each output port of a variable node (see scope rules at the call site). */
    private void compileVariableNode(NodeModel nm, IVariableNode varNode) {
        IVariable variable = varNode.getVariable();
        if (variable == null) return;
        // Inside an inlined subgraph: a READ variable is a function input — emit the bound outer
        // expression instead of a const/uniform. (WRITE/local vars of the subgraph fall through.)
        if (variable instanceof VariableDeclarationModelBase vd) {
            ShaderExpr bound = lookupBinding(vd.getUid());
            if (bound != null) {
                for (PortModel outp : nm.getOutputsByDisplayOrder()) {
                    GlslType decl = GlslType.of(outp.getDataTypeHandle());
                    current.cache.put(outp, decl != null ? convert(bound, decl) : bound);
                }
                return;
            }
        }
        VariableScope scope = (variable instanceof VariableDeclarationModelBase vd)
                ? vd.getScope() : VariableScope.LOCAL;
        Object defaultValue = variable.tryGetDefaultValue(variable.getDataType()).result().orElse(null);
        for (PortModel outp : nm.getOutputsByDisplayOrder()) {
            GlslType decl = GlslType.of(outp.getDataTypeHandle());
            if (decl == null) continue;
            if (decl == GlslType.SAMPLER2D) {
                String name = variableUniformName(variable);
                layout.addSampler(name);
                SamplerDefault def = SamplerDefault.of(defaultValue);
                if (def != null) samplerDefaults.putIfAbsent(name, def);
                variableSamplerNames.putIfAbsent(variable.getName(), name);
                current.cache.put(outp, new ShaderExpr(name, decl));
            } else if (scope == VariableScope.EXPOSED) {
                String name = variableUniformName(variable);
                String accessor = layout.addField(name, decl);
                uniformDefaults.putIfAbsent(name, GlslFormat.components(defaultValue, decl));
                variableUniformFields.putIfAbsent(variable.getName(), new MaterialUniformLayout.Field(name, decl));
                current.cache.put(outp, new ShaderExpr(accessor, decl));
            } else {
                // LOCAL / UNKNOWN: bake the declared value inline (mirrors the IConstantNode branch).
                current.cache.put(outp, hoist(decl, GlslFormat.literal(defaultValue, decl)));
            }
        }
    }

    /**
     * Inline a subgraph node by emitting the inner {@code ShaderFunctionGraph}'s logic into the current
     * stage. Binds each inner READ variable to the outer input port's expression (pushed as a binding
     * frame), then computes each outer output port from the inner WRITE variable's source. Mirrors
     * {@link com.lowdragmc.kilagraph.graph.exec.GraphExecutor}'s {@code evaluateSubgraph}.
     */
    private void compileSubgraphNode(SubgraphNodeModel sub, NodeModel nm) {
        if (!(sub.getSubgraphModel() instanceof CustomGraphModelImpl inner)) return; // unresolved → outputs null
        if (inner == graph.graphModel) return; // trivial self-reference guard

        Map<UUID, ShaderExpr> frame = new HashMap<>();
        for (var v : inner.getGraphVariableModels()) {
            if (v == null) continue;
            var mods = v.getModifiers();
            if (mods == null || !mods.hasFlag(ModifierFlags.READ)) continue;
            PortModel outerInput = lookupSubgraphPort(sub, v, true, mods);
            if (outerInput == null) continue;
            frame.put(v.getUid(), pullInput(outerInput, GlslType.of(outerInput.getDataTypeHandle())));
        }
        bindingStack.push(frame);
        try {
            for (var v : inner.getGraphVariableModels()) {
                if (v == null) continue;
                var mods = v.getModifiers();
                if (mods == null || !mods.hasFlag(ModifierFlags.WRITE)) continue;
                PortModel outerOutput = lookupSubgraphPort(sub, v, false, mods);
                if (outerOutput == null) continue;
                GlslType decl = GlslType.of(outerOutput.getDataTypeHandle());
                current.cache.put(outerOutput, compileInnerWriteVar(v, inner, decl));
            }
        } finally {
            bindingStack.pop();
        }
    }

    /** Compile the value assigned to an inner WRITE variable (its "set" writer node's input source). */
    private ShaderExpr compileInnerWriteVar(VariableDeclarationModelBase v, CustomGraphModelImpl inner, @Nullable GlslType decl) {
        GlslType target = decl != null ? decl : GlslType.FLOAT;
        for (var innerNm : inner.getNodeModels()) {
            if (!(innerNm instanceof NodeModel n)) continue;
            IVariableNode vn = null;
            if (innerNm instanceof IVariableNode direct) {
                vn = direct;
            } else if (innerNm instanceof ICustomNodeModel cnm && cnm.getNode() instanceof IVariableNode wrapped) {
                vn = wrapped;
            }
            if (vn == null) continue;
            IVariable ref = vn.getVariable();
            if (ref == null || !java.util.Objects.equals(ref.getName(), v.getName())) continue;
            var inputs = n.getInputsById();
            if (inputs.isEmpty()) continue; // not the "set" form
            return pullInput(inputs.values().iterator().next(), target);
        }
        // No writer node — fall back to the variable's declared default (baked literal), else zero.
        Object def = v.tryGetDefaultValue(v.getDataType()).result().orElse(null);
        return decl != null ? hoist(decl, GlslFormat.literal(def, decl)) : new ShaderExpr("0.0", GlslType.FLOAT);
    }

    /** The outer subgraph-node port mirroring an inner variable (port id = uid, or uid+"-in"/"-out"). */
    @Nullable
    private PortModel lookupSubgraphPort(SubgraphNodeModel sub, VariableDeclarationModelBase v,
                                         boolean wantInput, ModifierFlags mods) {
        String suffix = (mods == ModifierFlags.READ_WRITE) ? (wantInput ? "-in" : "-out") : "";
        String portId = v.getUid().toString() + suffix;
        return wantInput ? sub.getInputsById().get(portId) : sub.getOutputsById().get(portId);
    }

    /** The bound outer expression for an inner READ variable, or null if not inside that subgraph. */
    @Nullable
    private ShaderExpr lookupBinding(UUID uid) {
        for (var frame : bindingStack) { // head = innermost subgraph; uids are globally unique
            ShaderExpr e = frame.get(uid);
            if (e != null) return e;
        }
        return null;
    }

    /**
     * The unique GLSL identifier (uniform field / sampler name) for a variable, namespaced with a
     * {@code kg_} prefix so it can never collide with builtin samplers (Sampler0/1/2) or UBO names.
     * Stable per variable declaration uid; distinct sanitized collisions get a numeric suffix.
     */
    private String variableUniformName(IVariable variable) {
        UUID uid = (variable instanceof VariableDeclarationModelBase vd) ? vd.getUid() : null;
        if (uid != null) {
            String existing = variableNames.get(uid);
            if (existing != null) return existing;
        }
        String base = ("kg_" + sanitizeIdentifier(variable.getName())).replaceAll("_+", "_");
        String name = base;
        for (int i = 1; usedVariableNames.contains(name); i++) {
            name = base + "_" + i;
        }
        usedVariableNames.add(name);
        if (uid != null) variableNames.put(uid, name);
        return name;
    }

    /**
     * Reduce an arbitrary variable name to a legal GLSL identifier body ([A-Za-z0-9_]). Runs of
     * non-identifier chars (and any resulting consecutive underscores) collapse to a single {@code _} —
     * GLSL reserves identifiers containing {@code __}, so a doubled underscore fails to compile. Callers
     * always prefix with {@code kg_*}, so a leading digit is fine (the result never starts with one).
     */
    private static String sanitizeIdentifier(String raw) {
        if (raw == null || raw.isEmpty()) return "var";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            sb.append((c < 128 && (Character.isLetterOrDigit(c) || c == '_')) ? c : '_');
        }
        String s = sb.toString().replaceAll("_+", "_");
        return s.isEmpty() ? "var" : s;
    }

    /**
     * Allocate a per-node {@code uniform sampler2D kg_tex_*} for a {@code TextureNode}'s value and bake
     * its texture + sampler params as the material default. The sampler name is stable per node uid.
     */
    ShaderExpr textureSampler(NodeModel nm, Object value) {
        String name = nodeSamplerName(nm);
        layout.addSampler(name);
        SamplerDefault def = SamplerDefault.of(value);
        samplerDefaults.putIfAbsent(name, def != null ? def : SamplerDefault.missing());
        return new ShaderExpr(name, GlslType.SAMPLER2D);
    }

    /** The stable, unique {@code kg_tex_*} sampler name for a texture node (keyed by node uid). */
    private String nodeSamplerName(NodeModel nm) {
        return nodeSamplerNames.computeIfAbsent(nm.getUid(), uid -> {
            // collapse so the uid's dashes-turned-underscores can't double the prefix's trailing '_'.
            String base = ("kg_tex_" + sanitizeIdentifier(uid.toString())).replaceAll("_+", "_");
            String name = base;
            for (int i = 1; usedVariableNames.contains(name); i++) name = base + "_" + i;
            usedVariableNames.add(name);
            return name;
        });
    }

    private void ensureVaryingBuilt(PortModel blockOutput, IVaryingBlock vb) {
        String name = vb.varyingName();
        if (varyings.containsKey(name)) return;
        varyings.put(name, vb.varyingType());
        AbstractNodeModel owner = blockOutput.getNodeModel() instanceof AbstractNodeModel a ? a : null;
        if (!(owner instanceof NodeModel nm)) return;
        StageScope saved = current;
        current = vertex;
        try {
            var ctx = new ShaderCompileContext(this, nm);
            ShaderExpr value = convert(vb.compileVarying(ctx), vb.varyingType());
            line(name + " = " + value.code() + ";");
        } finally {
            current = saved;
        }
    }

    // ---- emission helpers (called via ShaderCompileContext) ----------------------------------

    ShaderExpr hoist(GlslType type, String code) {
        String name = current.tempPrefix + "_" + (current.tempCounter++);
        current.body.append("    ").append(type.glsl()).append(' ').append(name)
                .append(" = ").append(code).append(";\n");
        return new ShaderExpr(name, type);
    }

    void line(String statement) {
        current.body.append("    ").append(statement).append('\n');
    }

    /**
     * Minecraft include files that unconditionally declare a {@code layout(std140) uniform} block.
     * Importing one means the generated GLSL contains that block, so the pipeline must declare the
     * matching uniform — registered automatically here to keep shader and pipeline in lockstep.
     */
    private static final Map<String, String> INCLUDE_UBOS = Map.of(
            "minecraft:fog.glsl", "Fog",
            "minecraft:light.glsl", "Lighting",
            "minecraft:dynamictransforms.glsl", "DynamicTransforms",
            "minecraft:projection.glsl", "Projection",
            "minecraft:globals.glsl", "Globals"
    );

    void addInclude(String path) {
        current.includes.add(path);
        String ubo = INCLUDE_UBOS.get(path);
        if (ubo != null) builtinUbos.add(ubo);
    }

    void useBuiltinUbo(String name) {
        builtinUbos.add(name);
    }

    MaterialUniformLayout layout() {
        return layout;
    }

    // ---- type conversion ---------------------------------------------------------------------

    static ShaderExpr convert(ShaderExpr expr, @Nullable GlslType target) {
        if (expr == null || target == null || expr.type() == target) return expr;
        GlslType from = expr.type();
        String code = expr.code();
        if (from == GlslType.SAMPLER2D || target == GlslType.SAMPLER2D
                || from == GlslType.MAT4 || target == GlslType.MAT4) {
            return new ShaderExpr(code, target); // not convertible; keep code, retag
        }
        // Normalise int/bool to float for arithmetic targets.
        if ((from == GlslType.INT || from == GlslType.BOOL) && target.isFloatVector()) {
            from = GlslType.FLOAT;
            code = "float(" + code + ")";
        }
        if (from == GlslType.FLOAT && target == GlslType.INT) {
            return new ShaderExpr("int(" + code + ")", GlslType.INT);
        }
        if (from == GlslType.INT && target == GlslType.FLOAT) {
            return new ShaderExpr("float(" + code + ")", GlslType.FLOAT);
        }
        if (!from.isFloatVector() || !target.isFloatVector()) {
            return new ShaderExpr(code, target);
        }
        if (from == GlslType.FLOAT) {
            // scalar broadcast
            return new ShaderExpr(target == GlslType.FLOAT ? code : target.glsl() + "(" + code + ")", target);
        }
        if (target == GlslType.FLOAT) {
            return new ShaderExpr("(" + code + ").x", GlslType.FLOAT);
        }
        int fc = from.components();
        int tc = target.components();
        if (tc <= fc) {
            return new ShaderExpr("(" + code + ")." + "xyzw".substring(0, tc), target);
        }
        // pad missing components: 0.0, with w forced to 1.0 for vec4 targets
        StringBuilder pad = new StringBuilder();
        for (int i = fc; i < tc; i++) {
            pad.append(", ");
            pad.append((target == GlslType.VEC4 && i == tc - 1) ? "1.0" : "0.0");
        }
        return new ShaderExpr(target.glsl() + "(" + code + pad + ")", target);
    }

    // ---- assembly ----------------------------------------------------------------------------

    private String assembleVertex() {
        StringBuilder sb = new StringBuilder();
        sb.append(GLSL_VERSION).append("\n\n");
        for (String inc : vertex.includes) sb.append("#moj_import <").append(inc).append(">\n");
        if (!vertex.includes.isEmpty()) sb.append('\n');
        sb.append(vertexAttributes(graph.getSettings().vertexFormatElements()));
        String uniforms = layout.declareGlsl();
        if (!uniforms.isEmpty()) sb.append('\n').append(uniforms);
        if (usesEngineGlobals) sb.append('\n').append(KGEngineUniforms.declareGlsl());
        if (!varyings.isEmpty()) {
            sb.append('\n');
            for (var e : varyings.entrySet()) {
                sb.append("out ").append(e.getValue().glsl()).append(' ').append(e.getKey()).append(";\n");
            }
        }
        sb.append("\nvoid main() {\n");
        sb.append(vertex.body).append("}\n");
        return sb.toString();
    }

    private String assemblePreviewVertex() {
        return GLSL_VERSION + "\n\n"
                + "#moj_import <minecraft:dynamictransforms.glsl>\n"
                + "#moj_import <minecraft:projection.glsl>\n\n"
                + "in vec3 Position;\nin vec2 UV0;\n\nout vec2 vUv;\n\n"
                + "void main() {\n"
                + "    vUv = UV0;\n"
                + "    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);\n"
                + "}\n";
    }

    private String assemblePreviewFragment(ShaderExpr color) {
        StringBuilder sb = new StringBuilder();
        sb.append(GLSL_VERSION).append("\n\n");
        for (String inc : fragment.includes) sb.append("#moj_import <").append(inc).append(">\n");
        if (!fragment.includes.isEmpty()) sb.append('\n');
        String uniforms = layout.declareGlsl();
        if (!uniforms.isEmpty()) sb.append(uniforms);
        if (usesEngineGlobals) sb.append(KGEngineUniforms.declareGlsl());
        sb.append("\nin vec2 vUv;\n\nout vec4 fragColor;\n");
        sb.append("\nvoid main() {\n").append(fragment.body);
        sb.append("    fragColor = ").append(color.code()).append(";\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String assembleFragment(FragmentOutputs out) {
        StringBuilder sb = new StringBuilder();
        sb.append(GLSL_VERSION).append("\n\n");
        for (String inc : fragment.includes) sb.append("#moj_import <").append(inc).append(">\n");
        if (!fragment.includes.isEmpty()) sb.append('\n');
        String uniforms = layout.declareGlsl();
        if (!uniforms.isEmpty()) sb.append(uniforms);
        if (usesEngineGlobals) sb.append(KGEngineUniforms.declareGlsl());
        if (!varyings.isEmpty()) {
            for (var e : varyings.entrySet()) {
                sb.append("in ").append(e.getValue().glsl()).append(' ').append(e.getKey()).append(";\n");
            }
        }
        sb.append("\nout vec4 fragColor;\n");
        sb.append("\nvoid main() {\n").append(fragment.body);
        String baseColor = out.baseColor != null ? out.baseColor.code() : "vec3(1.0)";
        String alpha = out.alpha != null ? out.alpha.code() : "1.0";
        sb.append("    vec3 kg_baseColor = ").append(baseColor).append(";\n");
        sb.append("    float kg_alpha = ").append(alpha).append(";\n");
        if (out.emission != null) {
            sb.append("    kg_baseColor += ").append(out.emission.code()).append(";\n");
        }
        if (out.alphaDiscardCutoff != null) {
            sb.append("    if (kg_alpha < ").append(out.alphaDiscardCutoff.code()).append(") discard;\n");
        }
        sb.append("    fragColor = vec4(kg_baseColor, kg_alpha);\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * The {@code in} vertex attribute declarations for the graph's composed vertex format. Each declared
     * element contributes one {@code in <glslType> <attribName>;} line; the {@code attribName} is exactly
     * the name {@link com.lowdragmc.kilagraph.rendertype.format.KGVertexFormat} binds the element under, so
     * the shader's inputs line up with the pipeline layout. Unknown keys are skipped.
     */
    private static String vertexAttributes(java.util.List<String> elementKeys) {
        StringBuilder sb = new StringBuilder();
        var seen = new java.util.HashSet<String>();
        for (String key : elementKeys) {
            var e = com.lowdragmc.kilagraph.rendertype.format.KGVertexElements.get(key);
            if (e == null) continue;
            if (!seen.add(e.attribName())) continue; // never declare the same `in` twice
            sb.append("in ").append(e.glslType()).append(' ').append(e.attribName()).append(";\n");
        }
        return sb.toString();
    }

    // ---- helpers -----------------------------------------------------------------------------

    @Nullable
    private static Node nodeOf(AbstractNodeModel model) {
        return model instanceof ICustomNodeModel cnm ? cnm.getNode() : null;
    }

    @Nullable
    private static Node nodeOf(PortModel port) {
        return port.getNodeModel() instanceof AbstractNodeModel a ? nodeOf(a) : null;
    }

    @Nullable
    private static Object readConstant(PortModel inputPort) {
        try {
            return inputPort.tryGetValue(Object.class).result().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static ContextNodeModel asContext(NodeModel model, String which) {
        if (model instanceof ContextNodeModel cnm) return cnm;
        throw new ShaderCompileException("RenderTypeGraph is missing its " + which + " stage");
    }
}
