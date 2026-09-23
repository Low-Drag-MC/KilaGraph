package com.lowdragmc.kilagraph.test.uitest;

import com.lowdragmc.kilagraph.graph.util.INodeDescription;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.InjectionSnippet;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderGraphCompiler;
import com.lowdragmc.kilagraph.rendertype.nodes.artistic.curve.SampleCurveNode;
import com.lowdragmc.kilagraph.rendertype.nodes.artistic.gradient.SampleGradientNode;
import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentStageNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.matrix.Mat4TransformNode;
import com.lowdragmc.kilagraph.rendertype.nodes.texture.SamplerTexture2DNode;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingStageNode;
import com.lowdragmc.kilagraph.rendertype.iris.IrisCompat;
import com.lowdragmc.kilagraph.rendertype.iris.IrisSurfaceRegistry;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeFactory;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.Bare;
import com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.Canvas;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.BlockNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.ContextNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.IBlockNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.IContextNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import com.mojang.blaze3d.systems.RenderSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addBlock;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;
import static com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.bare;
import static com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.quad;

/**
 * Every RenderType node × option combination × output (into fragment Base Color and vertex Position), plus
 * every stage block: compiled, GPU-validated and drawn once. With Iris present, injectable graphs must also
 * pass the surface registry's GL validation. Failures go to {@code kg-node-sweep-<backend>.txt}.
 */
@LDLRegisterClient(name = "kg_rt_node_sweep", group = "kilagraph", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public class RenderTypeNodeSweepScenario implements UIScenario {

    /** Per-frame work budget, so the watchdog keeps seeing frames. */
    private static final long FRAME_BUDGET_NANOS = 40_000_000L;
    /** Options whose full cartesian product is at most this many are swept exhaustively. */
    private static final int MAX_COMBINATIONS = 128;
    private static final String SWEEP = "sweep";

    @Override
    public void configure(ScenarioOptions options) {
        options.defaultSettleMs(0).tags("kilagraph", "rendertype", "gpu", "slow").guiScale(2)
                .scenarioTimeoutMs(1_200_000).defaultTimeoutMs(1_200_000);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.step("plan every node and block variant", ctx -> ctx.put(SWEEP, new Sweep()))
                .waitUntil("every variant compiled and drew", ctx -> ctx.<Sweep>get(SWEEP).advance())
                .step("report", RenderTypeNodeSweepScenario::report)
                .teardown("free the canvas", ctx -> {
                    Sweep sweep = ctx.get(SWEEP, null);
                    if (sweep != null) sweep.canvas.close();
                });
    }

    private static void report(TestContext ctx) {
        Sweep sweep = ctx.get(SWEEP);
        String backend = RenderSystem.getDevice().getDeviceInfo().backendName();
        String summary = String.format(java.util.Locale.ROOT,
                "%s: %d node classes, %d block classes, %d variants, %d distinct pipelines drawn, %d stage-restricted, %d failures",
                backend, sweep.nodeClasses, sweep.blockClasses, sweep.variants, sweep.pipelines.size(),
                sweep.stageRestricted, sweep.failures.size());
        if (IrisCompat.ENABLED) {
            summary += String.format(java.util.Locale.ROOT, "; Iris: %d injectable pipelines validated, not injectable by design: %s",
                    sweep.irisChecked, sweep.irisPassthrough);
        }
        ctx.log(summary);
        ctx.attach("summary", summary);

        // A node none of whose variants ever produced a pipeline is untested, whatever the counts say.
        Set<String> uncovered = new TreeSet<>(sweep.planned);
        uncovered.removeAll(sweep.covered);
        uncovered.removeAll(sweep.failedNodes);
        ctx.attach("uncovered", String.join(", ", uncovered));

        StringBuilder file = new StringBuilder(summary).append("\n\nuncovered (no output port a graph can pull): ")
                .append(uncovered).append("\n\n");
        for (String failure : sweep.failures) file.append(failure).append("\n\n");
        file.append("content hash -> variant\n");
        sweep.hashes.forEach((hash, label) -> file.append(hash).append("  ").append(label).append('\n'));
        writeReport(file.toString());

        ctx.check("every variant of every node builds a pipeline and draws (" + backend + ")",
                sweep.failures.isEmpty(), 0, sweep.failures.size());
        int shown = 0;
        for (String failure : sweep.failures) {
            if (shown++ == 40) break;
            ctx.check(failure.lines().findFirst().orElse(failure), false);
        }
        ctx.check("every node class produced at least one drawn pipeline", uncovered.isEmpty(),
                "[]", uncovered.toString());
    }

    private static void writeReport(String text) {
        String out = System.getProperty("ldlib2.uitest.out");
        if (out == null) return;
        try {
            Path path = Path.of(out, "kg-node-sweep-" + RenderSystem.getDevice().getDeviceInfo().backendName()
                    .toLowerCase(java.util.Locale.ROOT) + ".txt");
            Files.createDirectories(path.getParent());
            Files.writeString(path, text);
        } catch (IOException ignored) {
            // the report entry still carries the summary and the first failures
        }
    }

    // ---- the sweep -----------------------------------------------------------------------------

    private enum Stage { FRAGMENT, VERTEX }

    /** One graph to build: a node class under one option assignment, one output routed into one stage. */
    private record Variant(Class<? extends Node> node, Map<String, Object> options, String port, Stage stage) {
        String label() {
            return node.getSimpleName() + (options.isEmpty() ? "" : " " + options) + " ." + port + " -> " + stage;
        }
    }

    /** A block inserted into a stage, inputs unconnected. */
    private record BlockVariant(Class<? extends BlockNode> block, Stage stage) {
        String label() {
            return block.getSimpleName() + " in the " + stage + " stage";
        }
    }

    private static final class Sweep {
        final Canvas canvas = new Canvas();
        final Deque<Object> queue = new ArrayDeque<>();
        final Set<String> pipelines = new HashSet<>();
        final List<String> failures = new ArrayList<>();
        final Set<String> planned = new TreeSet<>();
        final Set<String> covered = new HashSet<>();
        final Set<String> failedNodes = new HashSet<>();
        /** content hash -> the first variant that produced it, so a hash in the game log can be traced back. */
        final Map<String, String> hashes = new LinkedHashMap<>();
        /** Node classes some variant of which the compiler deemed not injectable under a shaderpack. */
        final Set<String> irisPassthrough = new TreeSet<>();
        int irisChecked;
        int nodeClasses, blockClasses, variants, stageRestricted;

        Sweep() {
            List<Class<? extends Node>> classes = new ArrayList<>(RenderTypeGraph.NODE_REGISTRY.getNodeClasses());
            classes.sort(Comparator.comparing(Class::getName));
            for (Class<? extends Node> cls : classes) {
                if (IBlockNode.class.isAssignableFrom(cls) || IContextNode.class.isAssignableFrom(cls)) continue;
                nodeClasses++;
                planned.add(cls.getSimpleName());
                for (Map<String, Object> options : optionAssignments(cls)) {
                    // Ports depend on options, so list them per assignment.
                    for (String port : outputPorts(cls, options)) {
                        queue.add(new Variant(cls, options, port, Stage.FRAGMENT));
                        queue.add(new Variant(cls, options, port, Stage.VERTEX));
                    }
                }
            }
            for (var stage : Map.<Stage, ContextNode>of(Stage.FRAGMENT, new FragmentStageNode(), Stage.VERTEX, new VaryingStageNode()).entrySet()) {
                for (Class<? extends BlockNode> block : stage.getValue().getSupportBlocks()) {
                    blockClasses++;
                    queue.add(new BlockVariant(block, stage.getKey()));
                }
            }
        }

        /** Work through the queue for one frame's budget. @return true once everything has been run. */
        boolean advance() {
            long deadline = System.nanoTime() + FRAME_BUDGET_NANOS;
            while (!queue.isEmpty() && System.nanoTime() < deadline) {
                Object next = queue.poll();
                if (next instanceof Variant v) run(v);
                else if (next instanceof BlockVariant b) run(b);
            }
            return queue.isEmpty();
        }

        private void run(Variant v) {
            variants++;
            Bare b;
            try {
                b = bare();
                NodeModel node = addNode(b.graph(), v.node());
                v.options().forEach((id, value) -> setOption(node, id, value));
                PortModel out = route(b, node.getOutputsById().get(v.port()));
                wire(b.graph(), v.stage() == Stage.FRAGMENT ? b.color() : b.positionIn(), out);
            } catch (Throwable t) {
                fail(v.node().getSimpleName(), v.label(), "graph construction threw " + t, null);
                return;
            }
            build(v.node().getSimpleName(), v.label(), b);
        }

        private void run(BlockVariant v) {
            variants++;
            Bare b;
            try {
                b = bare();
                addBlock(b.graph(), v.stage() == Stage.FRAGMENT ? b.graph().getFragmentStageModel()
                        : b.graph().getVertexStageModel(), v.block());
            } catch (Throwable t) {
                fail(v.block().getSimpleName(), v.label(), "block insertion threw " + t, null);
                return;
            }
            build(v.block().getSimpleName(), v.label(), b);
        }

        /** Compile, build the material (GPU-validated), draw once. */
        private void build(String node, String label, Bare b) {
            CompiledShaderGraph compiled;
            try {
                compiled = new ShaderGraphCompiler(b.graph()).compile();
            } catch (Throwable t) {
                fail(node, label, "the compiler threw " + t, null);
                return;
            }
            if (compiled.hasStageErrors()) {
                stageRestricted++;
                return;
            }
            hashes.putIfAbsent(compiled.contentHash(), label);
            if (!pipelines.add(compiled.contentHash())) {
                covered.add(node); // same GLSL as a variant already drawn
                return;
            }
            RenderTypeGraphMaterial material;
            try {
                material = RenderTypeFactory.createMaterial(compiled);
            } catch (Throwable t) {
                fail(node, label, "createMaterial threw " + t, compiled);
                return;
            }
            if (material == null) {
                fail(node, label, "the backend rejected the pipeline (driver/shaderc message in the log)", compiled);
                return;
            }
            if (IrisCompat.ENABLED) irisCheck(node, label, compiled, material);
            try {
                canvas.clear(0xFF000000);
                canvas.draw(material, vc -> quad(vc, -1, -1, 1, 1, 0.5f, -1));
                covered.add(node);
            } catch (Throwable t) {
                fail(node, label, "drawing threw " + t, compiled);
            } finally {
                material.close();
            }
        }

        /** An injectable graph must have passed the Iris surface validation (vertex half too, if it displaces). */
        private void irisCheck(String node, String label, CompiledShaderGraph compiled, RenderTypeGraphMaterial material) {
            InjectionSnippet snippet = compiled.injectionSnippet();
            if (snippet == null) {
                irisPassthrough.add(node);
                return;
            }
            irisChecked++;
            int id = material.irisSurfaceId();
            if (id == 0) {
                fail(node, label, "Iris injection: the surface fails standalone GL validation (driver log in the game log)", compiled);
                return;
            }
            boolean displaces = !snippet.legacyVertexBlock()
                    && (snippet.vertexPositionExpr() != null || snippet.vertexNormalExpr() != null);
            if (!displaces) return;
            for (var surface : IrisSurfaceRegistry.snapshot()) {
                if (surface.id() == id && !surface.hasVertex()) {
                    fail(node, label, "Iris injection: the vertex displacement fails standalone GL validation", compiled);
                }
            }
        }

        private void fail(String node, String label, String why, CompiledShaderGraph compiled) {
            failedNodes.add(node);
            StringBuilder sb = new StringBuilder(label).append(": ").append(why);
            if (compiled != null) {
                sb.append("\n--- vertex ---\n").append(compiled.vertexSource())
                        .append("\n--- fragment ---\n").append(compiled.fragmentSource());
            }
            failures.add(sb.toString());
        }
    }

    /** Route an output into something a stage block can take: opaque types go through their consumer node. */
    private static PortModel route(Bare b, PortModel out) {
        GlslType type = GlslType.of(out.getDataTypeHandle());
        if (type == null) return out; // DYNAMIC: a float vector of the node's inferred width
        return switch (type) {
            case MAT4 -> through(b, Mat4TransformNode.class, "m", out, "out");
            case SAMPLER2D -> through(b, SamplerTexture2DNode.class, "sampler", out, "color");
            case GRADIENT -> through(b, SampleGradientNode.class, "gradient", out, "color");
            case CURVE -> through(b, SampleCurveNode.class, "curve", out, "value");
            default -> out;
        };
    }

    private static PortModel through(Bare b, Class<? extends Node> consumer, String in, PortModel value, String out) {
        NodeModel node = addNode(b.graph(), consumer);
        wire(b.graph(), node.getInputsById().get(in), value);
        return node.getOutputsById().get(out);
    }

    private static List<String> outputPorts(Class<? extends Node> cls, Map<String, Object> options) {
        try {
            NodeModel node = addNode(bare().graph(), cls);
            options.forEach((id, value) -> setOption(node, id, value));
            return new ArrayList<>(node.getOutputsById().keySet());
        } catch (Throwable t) {
            return List.of();
        }
    }

    /** The option assignments to sweep: every combination when that is few enough, else one at a time. */
    private static List<Map<String, Object>> optionAssignments(Class<? extends Node> cls) {
        Map<String, List<Object>> values = new LinkedHashMap<>();
        try {
            NodeModel probe = addNode(bare().graph(), cls);
            for (var option : probe.getNodeOptions()) {
                // NodeOption#getDataType is not populated, so go by the default value's runtime type.
                Object current = option.tryGetValue(Object.class).result().orElse(null);
                List<Object> candidates = candidates(probe, option.id, current);
                if (candidates.size() > 1) values.put(option.id, candidates);
            }
        } catch (Throwable t) {
            return List.of(Map.of());
        }
        long product = 1;
        for (List<Object> v : values.values()) product *= v.size();
        List<Map<String, Object>> result = new ArrayList<>();
        if (product <= MAX_COMBINATIONS) {
            result.add(new LinkedHashMap<>());
            for (var entry : values.entrySet()) {
                List<Map<String, Object>> next = new ArrayList<>();
                for (Map<String, Object> partial : result) {
                    for (Object value : entry.getValue()) {
                        Map<String, Object> m = new LinkedHashMap<>(partial);
                        m.put(entry.getKey(), value);
                        next.add(m);
                    }
                }
                result = next;
            }
        } else {
            result.add(Map.of());
            for (var entry : values.entrySet()) {
                for (Object value : entry.getValue()) result.add(Map.of(entry.getKey(), value));
            }
        }
        return result;
    }

    private static List<Object> candidates(NodeModel probe, String optionId, Object current) {
        if (current instanceof Boolean) return List.of(false, true);
        if (current instanceof Enum<?> e) return List.of((Object[]) e.getDeclaringClass().getEnumConstants());
        if (current instanceof String && probe instanceof ICustomNodeModel custom
                && custom.getNode() instanceof INodeDescription described) {
            return new ArrayList<>(described.optionChoices(optionId));
        }
        return List.of(); // colours, textures, gradients, curves: the default is the one variant
    }


}
