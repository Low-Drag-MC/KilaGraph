package com.lowdragmc.kilagraph.rendertype;

import com.lowdragmc.kilagraph.Kilagraph;
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
import com.lowdragmc.kilagraph.rendertype.nodes.vector.ShaderSplitNode;
import com.lowdragmc.kilagraph.rendertype.nodes.vector.ShaderVec4MultiplyNode;
import com.lowdragmc.kilagraph.rendertype.nodes.vector.Vec3Node;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingVertexColorBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingCylindricalVertexDistanceBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VertexPositionBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingSphericalVertexDistanceBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingStageNode;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingTexCoordBlock;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.GraphLogger;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.GraphNodeRegistry;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.BlockNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
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
        ensureFixedStages();
    }

    @Override
    protected CustomGraphModelImpl createGraphModel() {
        return new RenderTypeGraphModel(this);
    }

    private NodeModel createNode(Class<? extends Node> nodeClass, float x, float y) {
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

    private void initializeDefaultEntityShader() {
        if (!(vertexStageModel instanceof ContextNodeModel vertexStage)
                || !(fragmentStageModel instanceof ContextNodeModel fragmentStage)) {
            return;
        }
        if (!vertexStage.getBlocks().isEmpty() || !fragmentStage.getBlocks().isEmpty()
                || hasTopLevelShaderNodes()) {
            return;
        }

        var position = createBlock(vertexStage, VertexPositionBlock.class);
        var vertexColor = createBlock(vertexStage, VaryingVertexColorBlock.class);
        var sphericalDistance = createBlock(vertexStage, VaryingSphericalVertexDistanceBlock.class);
        var cylindricalDistance = createBlock(vertexStage, VaryingCylindricalVertexDistanceBlock.class);
        var texCoord = createBlock(vertexStage, VaryingTexCoordBlock.class);
        var sampler = createTextureNode(-120, 96, "minecraft:textures/block/dirt.png");
        var textureSample = createNode(SamplerTexture2DNode.class, 80, 96);
        var entityColor = createNode(ShaderVec4MultiplyNode.class, 280, 64);
        var dynamicTransforms = createNode(DynamicTransformsUboNode.class, 280, 190);
        var modulatedColor = createNode(ShaderVec4MultiplyNode.class, 500, 96);
        var fogSub = createFogSubgraphNode(740, 96);
        var split = createNode(ShaderSplitNode.class, 960, 96);
        var baseColorVec = createNode(Vec3Node.class, 1160, 64);
        var baseColor = createBlock(fragmentStage, FragmentBaseColorBlock.class);
        var alpha = createBlock(fragmentStage, FragmentAlphaBlock.class);

        position.setPosition(new Vector2f(-430, -8));
        vertexColor.setPosition(new Vector2f(-430, 24));
        sphericalDistance.setPosition(new Vector2f(-430, 56));
        cylindricalDistance.setPosition(new Vector2f(-430, 88));
        texCoord.setPosition(new Vector2f(-430, 120));
        baseColor.setPosition(new Vector2f(410, -8));
        alpha.setPosition(new Vector2f(410, 24));

        graphModel.createWire(textureSample.getInputsById().get("sampler"), sampler.getOutputsById().get("sampler"));
        graphModel.createWire(textureSample.getInputsById().get("uv"), texCoord.getOutputsById().get("texCoord"));
        graphModel.createWire(entityColor.getInputsById().get("a"), textureSample.getOutputsById().get("color"));
        graphModel.createWire(entityColor.getInputsById().get("b"), vertexColor.getOutputsById().get("color"));
        graphModel.createWire(modulatedColor.getInputsById().get("a"), entityColor.getOutputsById().get("out"));
        graphModel.createWire(modulatedColor.getInputsById().get("b"), dynamicTransforms.getOutputsById().get("ColorModulator"));
        graphModel.createWire(fogSub.inColor(), modulatedColor.getOutputsById().get("out"));
        graphModel.createWire(fogSub.spherical(), sphericalDistance.getOutputsById().get("distance"));
        graphModel.createWire(fogSub.cylindrical(), cylindricalDistance.getOutputsById().get("distance"));
        graphModel.createWire(split.getInputsById().get("in"), fogSub.out());
        graphModel.createWire(baseColorVec.getInputsById().get("x"), split.getOutputsById().get("r"));
        graphModel.createWire(baseColorVec.getInputsById().get("y"), split.getOutputsById().get("g"));
        graphModel.createWire(baseColorVec.getInputsById().get("z"), split.getOutputsById().get("b"));
        graphModel.createWire(baseColor.getInputsById().get("color"), baseColorVec.getOutputsById().get("out"));
        graphModel.createWire(alpha.getInputsById().get("alpha"), split.getOutputsById().get("a"));
    }

    /** The fog subgraph node's outer ports, resolved by inner-variable uid. */
    private record FogSubgraph(NodeModel node, PortModel inColor, PortModel spherical,
                               PortModel cylindrical, PortModel out) {}

    /**
     * Build the default fog as a collapsed {@link ShaderFunctionGraph} subgraph node — the Fog UBO and
     * {@code apply_fog} live inside, so the node exposes just {@code inColor} + the two vertex distances
     * as inputs and the fogged colour as output. The user can dive in to tweak or delete + rebuild it.
     */
    private FogSubgraph createFogSubgraphNode(float x, float y) {
        CustomGraphModelImpl inner = graphModel.createLocalSubgraphInstance(ShaderFunctionGraph.class);
        graphModel.addLocalSubgraph(inner);

        var inColorVar = (VariableDeclarationModelBase) inner.createVariable(
                "inColor", RenderTypeGraphTypes.VEC4, new Vector4f(), VariableKind.INPUT);
        var sphericalVar = (VariableDeclarationModelBase) inner.createVariable(
                "sphericalVertexDistance", TypeHandles.FLOAT, 0f, VariableKind.INPUT);
        var cylindricalVar = (VariableDeclarationModelBase) inner.createVariable(
                "cylindricalVertexDistance", TypeHandles.FLOAT, 0f, VariableKind.INPUT);
        var outVar = (VariableDeclarationModelBase) inner.createVariable(
                "out", RenderTypeGraphTypes.VEC4, new Vector4f(), VariableKind.OUTPUT);

        var inColorNode = inner.createVariableNode(inColorVar, new Vector2f(-400, 0), null, null);
        var sphericalNode = inner.createVariableNode(sphericalVar, new Vector2f(-400, 48), null, null);
        var cylindricalNode = inner.createVariableNode(cylindricalVar, new Vector2f(-400, 96), null, null);
        var outNode = inner.createVariableNode(outVar, new Vector2f(400, 0), null, null);

        NodeModel fogUbo = innerNode(inner, FogUboNode.class, -180, 140);
        NodeModel applyFog = innerNode(inner, ApplyFogNode.class, 40, 16);

        inner.createWire(applyFog.getInputsById().get("inColor"), inColorNode.getOutputPort());
        inner.createWire(applyFog.getInputsById().get("sphericalVertexDistance"), sphericalNode.getOutputPort());
        inner.createWire(applyFog.getInputsById().get("cylindricalVertexDistance"), cylindricalNode.getOutputPort());
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
                subNode.getInputsById().get(sphericalVar.getUid().toString()),
                subNode.getInputsById().get(cylindricalVar.getUid().toString()),
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
        validateVertexFormat(null);
    }

    /**
     * Flag every {@link IVertexFormatDependentNode} (e.g. a {@code VertexAttributeInputNode}) whose chosen
     * element isn't present in the composed vertex format — its generated GLSL would reference an undeclared
     * {@code in}. Surfaced through the editor's {@link GraphLogger} when one is supplied (node-graph edits),
     * else logged to the console (Settings-only edits).
     */
    private void validateVertexFormat(@Nullable GraphLogger logger) {
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
        // 2) Default-behavior references (e.g. the vertex Color block defaulting to minecraft_mix_light with
        //    Color/Normal): caught by compiling once (CPU-only) and reading the substituted attributes. Only
        //    when an editor logger is present (a per-edit compile is cheap; skip it on the headless setSettings
        //    path). Reported as WARNINGs — the shader still compiles (the default degraded to a constant).
        if (logger == null) return;
        try {
            var compiled = new ShaderGraphCompiler(this).compile();
            for (String attrib : compiled.missingAttributes()) {
                if (flaggedAttribs.add(attrib)) {
                    logger.warning(Component.translatable("rendertypegraph.warn.vertex_element_defaulted", attrib));
                }
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
        validateVertexFormat(logger);
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
            ALPHA,
            ADDITIVE,
            TRANSLUCENT
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
