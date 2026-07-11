package com.lowdragmc.kilagraph.rendertype;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.rendertype.compiler.INodeValidator;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderGraphCompiler;
import com.lowdragmc.kilagraph.rendertype.format.IVertexFormatDependentNode;
import com.lowdragmc.kilagraph.rendertype.format.KGVertexElements;
import com.lowdragmc.kilagraph.rendertype.format.VertexFormatPresets;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import com.lowdragmc.kilagraph.rendertype.nodes.fog.ApplyFogNode;
import com.lowdragmc.kilagraph.rendertype.nodes.transform.DynamicTransformsUboNode;
import com.lowdragmc.kilagraph.rendertype.nodes.fog.FogUboNode;
import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentAlphaBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentBaseColorBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentStageNode;
import com.lowdragmc.kilagraph.rendertype.nodes.texture.SamplerTexture2DNode;
import com.lowdragmc.kilagraph.rendertype.nodes.texture.TextureNode;
import com.lowdragmc.kilagraph.rendertype.nodes.channel.SplitNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.basic.MultiplyNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.VertexColorNode;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VertexModelPositionBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingStageNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.GraphLogger;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.GraphNodeRegistry;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.BlockNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.command.GraphCommands;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.command.IGraphCommand;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.itemlibrary.GraphNodeCreationData;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.SpawnFlags;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ContextNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.CustomBlockNodeModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.SubgraphNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import net.minecraft.resources.Identifier;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Set;

public class RenderTypeGraph extends Graph {
    public static final GraphNodeRegistry NODE_REGISTRY =
            GraphNodeRegistry.create(Identifier.fromNamespaceAndPath(Kilagraph.MODID, "rendertype"),
                    RenderTypeGraph.class);

    private Settings settings = Settings.defaults();
    private NodeModel vertexStageModel;
    private NodeModel fragmentStageModel;
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Bumped on every model change (see {@link #onGraphChanged}); previews gate recompiles on it. */
    private volatile long changeVersion;

    public RenderTypeGraph() {
        this(true);
    }

    /**
     * @param initialize whether to build the default entity shader (the fixed vertex/fragment stages and
     *  the default node network). Pass {@code false} when the graph is about to be deserialized into — this
     *  skips constructing a throwaway default graph, and (more importantly) avoids priming the model's
     *  {@code getNodes()} cache with default nodes that would then go stale once {@code deserialize} clears
     *  and reloads {@code nodeModels}. {@link #restoreFixedStagesAfterDeserialize()} re-ensures the fixed
     *  stages after the load (recreating them only if a loaded graph somehow lacks them).
     */
    public RenderTypeGraph(boolean initialize) {
        if (initialize) {
            ensureFixedStages();
        }
    }

    @Override
    protected CustomGraphModelImpl createGraphModel() {
        return new RenderTypeGraphModel(this);
    }

    protected NodeModel createNode(Class<? extends Node> nodeClass, float x, float y) {
        var data = new GraphNodeCreationData(graphModel, new Vector2f(x, y), SpawnFlags.DEFAULT, null);
        return (NodeModel) CustomGraphModelImpl.createNodeFromData(data, nodeClass);
    }

    /** Create a {@link TextureNode} bound to {@code texture} — the default shader's texture source. */
    private NodeModel createTextureNode(float x, float y, String texture) {
        NodeModel node = createNode(TextureNode.class, x, y);
        setNodeOption(node, "texture",
                RenderTypeGraphTypes.Sampler2DValue.defaultValue().withLocation(texture));
        return node;
    }

    /**
     * The default shader's texture-source node: a node exposing a {@code SAMPLER2D} output port named
     * {@code "sampler"}, wired into the default {@code SamplerTexture2DNode}. Defaults to a
     * {@link TextureNode} bound to dirt; subclasses whose texture comes from outside the graph (e.g. a
     * SlideShow slide fed via an external sampler) override this to supply their own source node.
     */
    protected NodeModel createDefaultTextureSource() {
        return createTextureNode(48, -128, "minecraft:textures/block/dirt.png");
    }

    /**
     * The {@code mode} option for the default shader's {@link VertexColorNode} (see its dropdown:
     * {@code mix_light} / {@code color} / {@code block}). Defaults to {@code mix_light} (the entity look,
     * per-vertex diffuse). Subclasses whose geometry has no usable {@code Normal} (e.g. a SlideShow slide
     * quad) override this to {@code block} so the default lighting uses the baked lightmap instead.
     */
    protected String defaultVertexColorMode() {
        return VertexColorNode.MODE_MIX_LIGHT;
    }

    /**
     * The vertex-position block class the default shader places in the vertex stage. Subclasses with their
     * own fixed transform override this so the default graph — and nothing else — uses their block.
     * Default: the Unity-like model-space Position block (unconnected = identity, and — unlike the advanced
     * glPosition block — a driven one stays injectable under an Iris shaderpack).
     */
    protected Class<? extends BlockNode> defaultVertexPositionBlockClass() {
        return VertexModelPositionBlock.class;
    }

    /** Set a node option's value (mirrors the editor's option-constant write). */
    private static void setNodeOption(NodeModel node, String optionId, Object value) {
        for (var opt : node.getNodeOptions()) {
            if (opt.id.equals(optionId)) {
                var constant = node.getInputConstantsById().get(opt.portModel.getUniqueName());
                if (constant != null) constant.setValue(value);
                node.defineNode();
                return;
            }
        }
    }

    private NodeModel createFixedStage(Class<? extends Node> nodeClass, float x) {
        return createNode(nodeClass, x, -80);
    }

    public void restoreFixedStagesAfterDeserialize() {
        ensureFixedStages();
    }

    private void ensureFixedStages() {
        vertexStageModel = findTopLevelNode(VaryingStageNode.class);
        fragmentStageModel = findTopLevelNode(FragmentStageNode.class);
        boolean missing = vertexStageModel == null || fragmentStageModel == null;
        if (vertexStageModel == null) {
            vertexStageModel = createFixedStage(VaryingStageNode.class, -420);
        }
        if (fragmentStageModel == null) {
            fragmentStageModel = createFixedStage(FragmentStageNode.class, 420);
        }
        if (missing) {
            initializeDefaultEntityShader();
        }
    }

    private NodeModel findTopLevelNode(Class<? extends Node> nodeClass) {
        return graphModel.getNodeModels().stream()
                .filter(NodeModel.class::isInstance)
                .map(NodeModel.class::cast)
                .filter(model -> model instanceof ICustomNodeModel custom && nodeClass.isInstance(custom.getNode()))
                .findFirst()
                .orElse(null);
    }

    protected void initializeDefaultEntityShader() {
        if (!(vertexStageModel instanceof ContextNodeModel vertexStage)
                || !(fragmentStageModel instanceof ContextNodeModel fragmentStage)) {
            return;
        }
        if (!vertexStage.getBlocks().isEmpty() || !fragmentStage.getBlocks().isEmpty()
                || hasTopLevelShaderNodes()) {
            return;
        }

        // Only Position is an explicit vertex block now. The mesh uv (texture sample's uv), the lit vertex
        // colour (VertexColorNode), and the fog distances (inside the fog subgraph) all come from compiler
        // defaults — no specialized varying blocks needed.
        createBlock(vertexStage, defaultVertexPositionBlockClass());
        var vertexColor = createNode(VertexColorNode.class, 208, 112); // lit vertex colour (fsh)
        setNodeOption(vertexColor, "mode", defaultVertexColorMode());
        vertexColor.setPreviewExpanded(false);
        var sampler = createDefaultTextureSource();
        var textureSample = createNode(SamplerTexture2DNode.class, 208, -128);
        var entityColor = createNode(MultiplyNode.class, 384, -128);
        entityColor.setPreviewExpanded(false);
        var dynamicTransforms = createNode(DynamicTransformsUboNode.class, 352, 0);
        var modulatedColor = createNode(MultiplyNode.class, 512, -128);
        modulatedColor.setPreviewExpanded(false);
        var fogSub = createFogSubgraphNode(608, -96);
        var split = createNode(SplitNode.class, 752, 0);
        var baseColor = createBlock(fragmentStage, FragmentBaseColorBlock.class);
        var alpha = createBlock(fragmentStage, FragmentAlphaBlock.class);

        // The two fixed stages sit on the right; their blocks auto-stack at the stage origin.
        vertexStage.setPosition(new Vector2f(832, -320));
        fragmentStage.setPosition(new Vector2f(832, -128));

        graphModel.createWire(textureSample.getInputsById().get("sampler"), sampler.getOutputsById().get("sampler"));
        // textureSample.uv is left unconnected — the UV-typed port defaults to the mesh uv (UV0).
        graphModel.createWire(entityColor.getInputsById().get("a"), textureSample.getOutputsById().get("color"));
        graphModel.createWire(entityColor.getInputsById().get("b"), vertexColor.getOutputsById().get("out"));
        graphModel.createWire(modulatedColor.getInputsById().get("a"), entityColor.getOutputsById().get("out"));
        graphModel.createWire(modulatedColor.getInputsById().get("b"), dynamicTransforms.getOutputsById().get("ColorModulator"));
        graphModel.createWire(fogSub.inColor(), modulatedColor.getOutputsById().get("out"));
        // fog distances default inside the subgraph (ApplyFog's distance ports → the fog-distance varyings).
        // The fogged colour (vec4) feeds base color directly (rgb swizzle); Split extracts its alpha.
        graphModel.createWire(baseColor.getInputsById().get("color"), fogSub.out());
        graphModel.createWire(split.getInputsById().get("in"), fogSub.out());
        graphModel.createWire(alpha.getInputsById().get("alpha"), split.getOutputsById().get("a"));
    }

    /** The fog subgraph node's outer ports, resolved by inner-variable uid. */
    private record FogSubgraph(NodeModel node, PortModel inColor, PortModel out) {}

    /**
     * Build the default fog as a collapsed {@link ShaderFunctionGraph} subgraph node — the Fog UBO and
     * {@code apply_fog} live inside, so the node exposes just {@code inColor} as input and the fogged colour
     * as output. The vertex distances default inside (ApplyFog's distance ports → the fog-distance
     * varyings), so they aren't exposed. The user can dive in to tweak or delete + rebuild it.
     */
    private FogSubgraph createFogSubgraphNode(float x, float y) {
        CustomGraphModelImpl inner = graphModel.createLocalSubgraphInstance(ShaderFunctionGraph.class);
        graphModel.addLocalSubgraph(inner);

        var inColorVar = (VariableDeclarationModelBase) inner.createVariable(
                "inColor", RenderTypeGraphTypes.VEC4, new Vector4f(), VariableKind.INPUT);
        var outVar = (VariableDeclarationModelBase) inner.createVariable(
                "out", RenderTypeGraphTypes.VEC4, new Vector4f(), VariableKind.OUTPUT);

        var inColorNode = inner.createVariableNode(inColorVar, new Vector2f(-400, 0), null, null);
        var outNode = inner.createVariableNode(outVar, new Vector2f(400, 0), null, null);

        NodeModel fogUbo = innerNode(inner, FogUboNode.class, -180, 140);
        NodeModel applyFog = innerNode(inner, ApplyFogNode.class, 40, 16);

        inner.createWire(applyFog.getInputsById().get("inColor"), inColorNode.getOutputPort());
        // sphericalVertexDistance / cylindricalVertexDistance left unconnected → ApplyFog defaults them to
        // the fog-distance varyings (ctx.sphericalVertexDistance() / cylindricalVertexDistance()).
        inner.createWire(applyFog.getInputsById().get("environmentalStart"), fogUbo.getOutputsById().get("FogEnvironmentalStart"));
        inner.createWire(applyFog.getInputsById().get("environmentalEnd"), fogUbo.getOutputsById().get("FogEnvironmentalEnd"));
        inner.createWire(applyFog.getInputsById().get("renderDistanceStart"), fogUbo.getOutputsById().get("FogRenderDistanceStart"));
        inner.createWire(applyFog.getInputsById().get("renderDistanceEnd"), fogUbo.getOutputsById().get("FogRenderDistanceEnd"));
        inner.createWire(applyFog.getInputsById().get("fogColor"), fogUbo.getOutputsById().get("FogColor"));
        inner.createWire(outNode.getInputPort(), applyFog.getOutputsById().get("out"));

        var subNode = graphModel.createNodeWithType(SubgraphNodeModel.class, "rt_apply_fog_subgraph",
                new Vector2f(x, y), null, n -> n.setLocalSubgraph(inner), SpawnFlags.DEFAULT);
        subNode.defineNode();
        return new FogSubgraph(subNode,
                subNode.getInputsById().get(inColorVar.getUid().toString()),
                subNode.getOutputsById().get(outVar.getUid().toString()));
    }

    /**
     * Create a node inside a subgraph as a <b>registered</b> (non-orphan) node so it appears in the
     * inner graph's node list and renders when the user dives in. {@code ofOrphan} would leave it
     * wire-reachable (so compilation works) but absent from {@code nodeModels} — invisible in the editor.
     */
    private NodeModel innerNode(CustomGraphModelImpl inner, Class<? extends Node> nodeClass, float x, float y) {
        var data = new GraphNodeCreationData(inner, new Vector2f(x, y), SpawnFlags.DEFAULT, null);
        return (NodeModel) CustomGraphModelImpl.createNodeFromData(data, nodeClass);
    }

    private boolean hasTopLevelShaderNodes() {
        return graphModel.getNodeModels().stream()
                .filter(NodeModel.class::isInstance)
                .map(NodeModel.class::cast)
                .anyMatch(model -> !isFixedStageModel(model));
    }

    private NodeModel createBlock(ContextNodeModel contextModel, Class<? extends BlockNode> blockClass) {
        BlockNode userNode;
        try {
            userNode = blockClass.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Cannot instantiate block " + blockClass.getName(), e);
        }
        var block = new CustomBlockNodeModelImpl();
        block.setGraphModel(graphModel);
        block.setSpawnFlags(SpawnFlags.DEFAULT);
        block.initCustomNode(userNode);
        block.setContextNodeModel(contextModel);
        block.onCreateNode();
        contextModel.insertBlock(block, -1);
        return block;
    }

    public Settings getSettings() {
        return settings;
    }

    /**
     * The compiler used everywhere this graph is turned into GLSL — real compiles, editor previews,
     * per-node previews, and validation. Subclasses with a different compile target override this to
     * supply their {@link ShaderGraphCompiler} subclass.
     */
    public ShaderGraphCompiler createCompiler() {
        return new ShaderGraphCompiler(this);
    }

    public NodeModel getVertexStageModel() {
        return vertexStageModel;
    }

    public NodeModel getFragmentStageModel() {
        return fragmentStageModel;
    }

    public void setSettings(Settings settings) {
        this.settings = settings == null ? Settings.defaults() : settings;
        // Settings live outside the node-graph model, so editing them never fires onGraphChanged. Bump the
        // change version directly so the live previews recompile (vertex format / mode / blend all feed the
        // content hash + pipeline) instead of waiting for an unrelated node/wire edit.
        changeVersion++;
        // Re-validate: removing/reordering an element may leave a vertex-attribute node needing an absent
        // attribute. No editor GraphLogger here (Settings edits don't route through onGraphChanged), so log
        // to the console; the GraphLogger surfacing happens on the next node-graph change.
        validateGraph(null);
    }

    /**
     * Editor-surfaced validation of the current graph: (1) every {@link IVertexFormatDependentNode} (e.g. a
     * {@code VertexAttributeInputNode}) whose chosen element isn't in the composed vertex format — its GLSL
     * would reference an undeclared {@code in}; (2) stage-affinity violations (a VERTEX_ONLY/FRAGMENT_ONLY
     * node pulled into the wrong stage), read from a single CPU {@code compile()}. Surfaced through the
     * editor's {@link GraphLogger} when one is supplied (node-graph edits), else logged to the console
     * (Settings-only edits, which can't fix per-node markers anyway).
     */
    private void validateGraph(@Nullable GraphLogger logger) {
        var present = settings.vertexFormatElements();
        // 1) Explicit attribute-reader nodes (e.g. VertexAttributeInputNode): a node-keyed ERROR for one
        //    whose chosen element isn't in the format. Track the attribute names so the default-behavior
        //    pass below doesn't also warn about the same attribute.
        var flaggedAttribs = new java.util.HashSet<String>();
        for (var model : graphModel.getNodeModels()) {
            if (!(model instanceof ICustomNodeModel custom)) continue;
            if (!(custom.getNode() instanceof IVertexFormatDependentNode dependent)) continue;
            String key = dependent.requiredElementKey();
            if (present.contains(key)) continue;
            var element = KGVertexElements.get(key);
            String name = element == null ? key : element.attribName();
            flaggedAttribs.add(name);
            if (logger != null) {
                logger.error(Component.translatable("rendertypegraph.error.missing_vertex_element", name), model);
            } else {
                LOGGER.warn("[KilaGraph] a vertex-attribute node needs '{}' but the vertex format does not include it", name);
            }
        }
        // 1b) Nodes that validate themselves (e.g. the Expression node's reserved-word / duplicate port
        //     names, which would otherwise only surface as a GPU compile error). Node-keyed ERRORs.
        for (var model : graphModel.getNodeModels()) {
            if (!(model instanceof ICustomNodeModel custom)) continue;
            if (!(custom.getNode() instanceof INodeValidator validator)) continue;
            for (var error : validator.validationErrors()) {
                if (logger != null) {
                    logger.error(error, model);
                } else {
                    LOGGER.warn("[KilaGraph] {}", error.getString());
                }
            }
        }

        // 2) Default-behavior references (e.g. the vertex Color block defaulting to minecraft_mix_light with
        //    Color/Normal): caught by compiling once (CPU-only) and reading the substituted attributes. Only
        //    when an editor logger is present (a per-edit compile is cheap; skip it on the headless setSettings
        //    path). Reported as WARNINGs — the shader still compiles (the default degraded to a constant).
        if (logger == null) return;
        try {
            var compiled = createCompiler().compile();
            for (String attrib : compiled.missingAttributes()) {
                if (flaggedAttribs.add(attrib)) {
                    logger.warning(Component.translatable("rendertypegraph.warn.vertex_element_defaulted", attrib));
                }
            }
            // Stage-affinity violations from the same compile: a node pulled into a stage its affinity
            // forbids (e.g. a vertex attribute read in the fragment stage). Keyed to the offending node.
            for (var err : compiled.stageErrors()) {
                logger.error(Component.translatable("rendertypegraph.error.stage_affinity",
                                err.nodeName(), err.affinity().name(), err.usedIn().name()),
                        graphModel.getModel(err.nodeUid()));
            }
        } catch (RuntimeException ignored) {
            // A malformed graph throws during compile; the preview/material path reports that separately.
        }
    }

    @Override
    public List<Class<? extends Node>> getSupportNodes() {
        return NODE_REGISTRY.getNodeClasses();
    }

    @Override
    public List<Class<? extends Node>> getLibrarySupportNodes() {
        var nodes = super.getLibrarySupportNodes();
        return nodes.stream()
                .filter(node -> node != VaryingStageNode.class && node != FragmentStageNode.class)
                .toList();
    }

    @Override
    public boolean canExecuteCommand(IGraphCommand command) {
        if (command instanceof GraphCommands.DeleteElementsCommand deleteCommand) {
            return deleteCommand.elementsToDelete.stream()
                    .noneMatch(this::isFixedStageModel);
        }
        return super.canExecuteCommand(command);
    }

    private boolean isFixedStageModel(Object model) {
        return model == vertexStageModel || model == fragmentStageModel;
    }

    @Override
    public List<TypeHandle> getSupportTypes() {
        return RenderTypeGraphTypes.SUPPORT_TYPES;
    }

    /**
     * Blackboard variables are restricted to the shader-basic types that have a GLSL representation
     * (i.e. those {@link com.lowdragmc.kilagraph.rendertype.compiler.GlslType#of} resolves). STRING is
     * excluded — a variable becomes either an inlined constant, a {@code KG_Material} uniform, or a
     * {@code sampler2D}, none of which can carry it.
     */
    @Override
    public List<TypeHandle> getVariableSupportTypes() {
        return RenderTypeGraphTypes.VARIABLE_SUPPORT_TYPES;
    }

    /** Draggable constants are scalars only (vectors come from the Vec2/3/4 nodes). */
    @Override
    public List<TypeHandle> getLibrarySupportTypes() {
        return RenderTypeGraphTypes.CONSTANT_SUPPORT_TYPES;
    }

    /** Accept {@link ShaderFunctionGraph} as a (cross-type) local/external subgraph. */
    @Override
    public boolean acceptsSubgraphGraph(Graph other) {
        return other instanceof ShaderFunctionGraph;
    }

    /**
     * Called by the editor after each applied model changeset (structural <em>and</em> value/option
     * edits — both route through the change description). We bump {@link #changeVersion} so the live
     * previews can skip their per-frame recompile when nothing changed: the GLSL is regenerated (and
     * its content hash recomputed) only when the version advances, instead of every frame.
     */
    @Override
    public void onGraphChanged(GraphLogger logger) {
        super.onGraphChanged(logger);
        changeVersion++;
        validateGraph(logger);
    }

    /** A monotonically increasing counter bumped on every graph change; see {@link #onGraphChanged}. */
    public long getChangeVersion() {
        return changeVersion;
    }

    /**
     * A RenderTypeGraph is a top-level shader (never embedded as a subgraph), so its variables can be
     * exposed inputs (material uniforms / subgraph args) but an OUTPUT port is meaningless — its real
     * outputs are the fixed fragment stage. Offer only {@link VariableKind#INPUT} in the Blackboard
     * (vs. {@link ShaderFunctionGraph}, which keeps both directions for reuse).
     */
    @Override
    public Set<VariableKind> getSupportedSubgraphVariableKinds() {
        return Set.of(VariableKind.INPUT);
    }

    public record Settings(
            // Ordered list of KGVertexElement registry keys composing the vertex format (replaces the old
            // fixed VertexFormatPreset enum). Resolved to a real VertexFormat on the client via KGVertexFormat;
            // the shader compiler derives the matching `in …;` declarations from the same keys.
            List<String> vertexFormatElements,
            VertexFormatMode vertexFormatMode,
            BlendMode blend,
            DepthTest depthTest,
            boolean depthWrite,
            boolean cull,
            OutputTarget outputTarget,
            boolean affectsOutline,
            boolean sortOnUpload
    ) {
        public Settings {
            // A vertex format is semantically a *set* of elements: both the GPU (attributes bind by name)
            // and the CPU writer (VertexConsumer setters write by element offset) are order-independent.
            // So canonicalise — dedupe and sort by element id — giving a stable equals/hash/content-hash and
            // serialization, and reproducing DefaultVertexFormat.ENTITY/BLOCK's layout (id order) for reuse.
            vertexFormatElements = canonicalizeElements(vertexFormatElements);
        }

        /** Dedupe + sort the element keys into the canonical id order (unknown keys kept, sorted last). */
        private static List<String> canonicalizeElements(List<String> keys) {
            var unique = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(keys));
            unique.sort(java.util.Comparator.comparingInt(key -> {
                var element = KGVertexElements.get(key);
                return element == null ? Integer.MAX_VALUE : element.mcElementId();
            }));
            return List.copyOf(unique);
        }

        public static Settings defaults() {
            return new Settings(
                    VertexFormatPresets.defaults(),
                    VertexFormatMode.QUADS,
                    BlendMode.OPAQUE,
                    DepthTest.LEQUAL,
                    true,
                    true,
                    OutputTarget.MAIN,
                    true,
                    false
            );
        }

        public enum VertexFormatMode {
            QUADS,
            TRIANGLES,
            TRIANGLE_STRIP,
            LINES,
            LINE_STRIP
        }

        public enum BlendMode {
            OPAQUE,
            TRANSLUCENT,
            ADDITIVE,
            LIGHTNING,
            GLINT,
            OVERLAY,
            TRANSLUCENT_PREMULTIPLIED_ALPHA,
            ENTITY_OUTLINE_BLIT,
            INVERT
        }

        public enum DepthTest {
            LEQUAL,
            LESS,
            EQUAL,
            ALWAYS,
            NONE
        }

        public enum OutputTarget {
            MAIN,
            TRANSLUCENT,
            PARTICLES,
            WEATHER,
            ITEM_ENTITY
        }
    }
}
