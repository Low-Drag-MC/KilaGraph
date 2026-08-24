package com.lowdragmc.kilagraph.test.gametest;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.test.gametest.blueprint.BitwiseNodeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.CacheGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ComparisonNodeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ConvertNodeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.DeepGraphGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.DeterminismGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.DifferentialGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.EntityNodeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ErrorPathGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ExecCombinationsGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ExecDriverGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ExecIntegrationGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ExecLoopsGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ExecPrimitivesGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ExecRuntimeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ExecSemanticsGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ExecVarInteractionGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ExecutorBenchGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ExecutorBenchShapesGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ExecutorEdgeCaseGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.FunctionCallGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.InfoNodeBenchGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.InfoNodeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.IntrinsicParityGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.KGGraphBuilderGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.KGTypeHandlesGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.LazinessGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ListNodeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.LogicNodeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.MapNodeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.MathNodeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.McActionGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.McContainerGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.McCoverageGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.McDataGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.McDecomposeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.McGeometryGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.McIdTagGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.McInfoBlockGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.McIntegrationGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.McInteractionGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.McMiscGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.McPotionGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.McRecipeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.McRedstoneGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.McStructureGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.McWorldEntityGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.McWorldQueryGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.MixedWorkloadGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.NbtNodeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.NbtPipelineGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.OptionalNodeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.PreparedGraphGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.SetVarGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.StepDebuggerGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.StringNodeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.SubgraphExecGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.SubgraphGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.VariableGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.VectorNodeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.WirePortalGameTest;
import com.lowdragmc.kilagraph.test.gametest.rendertypegraph.RenderTypeGraphGameTest;
import com.lowdragmc.kilagraph.test.gametest.rendertypegraph.ShaderCompilerGameTest;
import com.lowdragmc.kilagraph.test.gametest.rendertypegraph.ShaderSubgraphGameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Consumer;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Central registration entrypoint for all KilaGraph GameTests. Mirrors LDLib2's
 * {@code NodeGraphGameTests}. Each test group's class exposes {@code registerFunctions()} and
 * {@code register(event, environment)}; this class calls both during mod init / RegisterGameTestsEvent.
 *
 * <p>Why GameTests instead of JUnit: many code paths the executor walks (variable creation,
 * subgraph instantiation, NBT serialization) load Minecraft classes (registries, NBT codecs,
 * Identifier) that aren't available in a plain JUnit run. GameTests run under a real
 * GameTestServer, mirroring LDLib2's own test convention.</p>
 */
public final class KGGameTests {

    private static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
            DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, Kilagraph.MODID);
    private static boolean initialized = false;

    private KGGameTests() {}

    /**
     * One test group: a class's two registration halves, named once.
     *
     * <p>They used to be two hand-kept lists of the same 60-odd class names, 130 lines apart — and
     * nothing made them agree. Omitting either half fails quietly: a function nothing runs, or a test
     * with no function behind it. Pairing them makes that impossible to write.</p>
     *
     * <p>There is no interface to implement because the halves are {@code static} methods; a pair of
     * method references is the lightest thing that ties them together.</p>
     */
    private record Group(Runnable functions,
                         BiConsumer<RegisterGameTestsEvent, Holder<TestEnvironmentDefinition<?>>> tests) {
    }

    /** Every blueprint test group. @see Group */
    private static final List<Group> BLUEPRINT = List.of(
            new Group(VariableGameTest::registerFunctions, VariableGameTest::register),
            new Group(SubgraphGameTest::registerFunctions, SubgraphGameTest::register),
            new Group(OptionalNodeGameTest::registerFunctions, OptionalNodeGameTest::register),
            new Group(LogicNodeGameTest::registerFunctions, LogicNodeGameTest::register),
            new Group(ComparisonNodeGameTest::registerFunctions, ComparisonNodeGameTest::register),
            new Group(ConvertNodeGameTest::registerFunctions, ConvertNodeGameTest::register),
            new Group(MathNodeGameTest::registerFunctions, MathNodeGameTest::register),
            new Group(StringNodeGameTest::registerFunctions, StringNodeGameTest::register),
            new Group(ListNodeGameTest::registerFunctions, ListNodeGameTest::register),
            new Group(MapNodeGameTest::registerFunctions, MapNodeGameTest::register),
            new Group(ExecRuntimeGameTest::registerFunctions, ExecRuntimeGameTest::register),
            new Group(ExecPrimitivesGameTest::registerFunctions, ExecPrimitivesGameTest::register),
            new Group(SetVarGameTest::registerFunctions, SetVarGameTest::register),
            new Group(ExecLoopsGameTest::registerFunctions, ExecLoopsGameTest::register),
            new Group(ExecSemanticsGameTest::registerFunctions, ExecSemanticsGameTest::register),
            new Group(ExecIntegrationGameTest::registerFunctions, ExecIntegrationGameTest::register),
            new Group(ExecCombinationsGameTest::registerFunctions, ExecCombinationsGameTest::register),
            new Group(CacheGameTest::registerFunctions, CacheGameTest::register),
            new Group(McDataGameTest::registerFunctions, McDataGameTest::register),
            new Group(InfoNodeGameTest::registerFunctions, InfoNodeGameTest::register),
            new Group(McWorldQueryGameTest::registerFunctions, McWorldQueryGameTest::register),
            new Group(BitwiseNodeGameTest::registerFunctions, BitwiseNodeGameTest::register),
            new Group(NbtNodeGameTest::registerFunctions, NbtNodeGameTest::register),
            new Group(EntityNodeGameTest::registerFunctions, EntityNodeGameTest::register),
            new Group(ExecVarInteractionGameTest::registerFunctions, ExecVarInteractionGameTest::register),
            new Group(SubgraphExecGameTest::registerFunctions, SubgraphExecGameTest::register),
            new Group(StepDebuggerGameTest::registerFunctions, StepDebuggerGameTest::register),
            new Group(WirePortalGameTest::registerFunctions, WirePortalGameTest::register),
            new Group(DeepGraphGameTest::registerFunctions, DeepGraphGameTest::register),
            new Group(DeterminismGameTest::registerFunctions, DeterminismGameTest::register),
            new Group(DifferentialGameTest::registerFunctions, DifferentialGameTest::register),
            new Group(ErrorPathGameTest::registerFunctions, ErrorPathGameTest::register),
            new Group(ExecDriverGameTest::registerFunctions, ExecDriverGameTest::register),
            new Group(ExecutorBenchGameTest::registerFunctions, ExecutorBenchGameTest::register),
            new Group(ExecutorBenchShapesGameTest::registerFunctions, ExecutorBenchShapesGameTest::register),
            new Group(ExecutorEdgeCaseGameTest::registerFunctions, ExecutorEdgeCaseGameTest::register),
            new Group(FunctionCallGameTest::registerFunctions, FunctionCallGameTest::register),
            new Group(InfoNodeBenchGameTest::registerFunctions, InfoNodeBenchGameTest::register),
            new Group(IntrinsicParityGameTest::registerFunctions, IntrinsicParityGameTest::register),
            new Group(KGGraphBuilderGameTest::registerFunctions, KGGraphBuilderGameTest::register),
            new Group(KGTypeHandlesGameTest::registerFunctions, KGTypeHandlesGameTest::register),
            new Group(LazinessGameTest::registerFunctions, LazinessGameTest::register),
            new Group(McActionGameTest::registerFunctions, McActionGameTest::register),
            new Group(McContainerGameTest::registerFunctions, McContainerGameTest::register),
            new Group(McCoverageGameTest::registerFunctions, McCoverageGameTest::register),
            new Group(McDecomposeGameTest::registerFunctions, McDecomposeGameTest::register),
            new Group(McGeometryGameTest::registerFunctions, McGeometryGameTest::register),
            new Group(McIdTagGameTest::registerFunctions, McIdTagGameTest::register),
            new Group(McInfoBlockGameTest::registerFunctions, McInfoBlockGameTest::register),
            new Group(McIntegrationGameTest::registerFunctions, McIntegrationGameTest::register),
            new Group(McInteractionGameTest::registerFunctions, McInteractionGameTest::register),
            new Group(McMiscGameTest::registerFunctions, McMiscGameTest::register),
            new Group(McPotionGameTest::registerFunctions, McPotionGameTest::register),
            new Group(McRecipeGameTest::registerFunctions, McRecipeGameTest::register),
            new Group(McRedstoneGameTest::registerFunctions, McRedstoneGameTest::register),
            new Group(McStructureGameTest::registerFunctions, McStructureGameTest::register),
            new Group(McWorldEntityGameTest::registerFunctions, McWorldEntityGameTest::register),
            new Group(MixedWorkloadGameTest::registerFunctions, MixedWorkloadGameTest::register),
            new Group(NbtPipelineGameTest::registerFunctions, NbtPipelineGameTest::register),
            new Group(PreparedGraphGameTest::registerFunctions, PreparedGraphGameTest::register),
            new Group(VectorNodeGameTest::registerFunctions, VectorNodeGameTest::register));

    /** Every render-type-graph test group. @see Group */
    private static final List<Group> RENDER_TYPE_GRAPH = List.of(
            new Group(RenderTypeGraphGameTest::registerFunctions, RenderTypeGraphGameTest::register),
            new Group(ShaderCompilerGameTest::registerFunctions, ShaderCompilerGameTest::register),
            new Group(ShaderSubgraphGameTest::registerFunctions, ShaderSubgraphGameTest::register));

    public static void init(IEventBus eventBus) {
        if (initialized) return;
        initialized = true;

        // Function registry is global; environment assignment is split in registerGameTests below.
        BLUEPRINT.forEach(g -> g.functions().run());
        RENDER_TYPE_GRAPH.forEach(g -> g.functions().run());

        TEST_FUNCTIONS.register(eventBus);
        eventBus.addListener(KGGameTests::registerGameTests);
    }

    public static DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> registerFunction(
            String path, Consumer<GameTestHelper> function) {
        return TEST_FUNCTIONS.register(path, () -> function);
    }

    public static ResourceKey<Consumer<GameTestHelper>> functionKey(String path) {
        return ResourceKey.create(Registries.TEST_FUNCTION, id(path));
    }

    public static TestData<Holder<TestEnvironmentDefinition<?>>> defaultTestData(
            Holder<TestEnvironmentDefinition<?>> environment, String structurePath) {
        return defaultTestData(environment, structurePath, 20);
    }

    /**
     * As {@link #defaultTestData(Holder, String)} but with an explicit tick budget — {@code maxTicks}
     * is the third {@code TestData} component. The deep-graph, differential and benchmark tests run
     * far longer than one second and are failed by the default 20 as "timed out", which reads like a
     * hang rather than a budget.
     */
    public static TestData<Holder<TestEnvironmentDefinition<?>>> defaultTestData(
            Holder<TestEnvironmentDefinition<?>> environment, String structurePath, int maxTicks) {
        // Reuse LDLib2's empty structure — node-graph tests don't need any world blocks.
        return new TestData<>(
                environment,
                Identifier.fromNamespaceAndPath("ldlib2", structurePath),
                maxTicks,
                0,
                true,
                Rotation.NONE,
                false,
                1,
                1,
                false,
                0
        );
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Kilagraph.MODID, path);
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> blueprintEnvironment = event.registerEnvironment(
                id("blueprint"),
                new TestEnvironmentDefinition.AllOf()
        );
        Holder<TestEnvironmentDefinition<?>> renderTypeGraphEnvironment = event.registerEnvironment(
                id("rendertypegraph"),
                new TestEnvironmentDefinition.AllOf()
        );

        BLUEPRINT.forEach(g -> g.tests().accept(event, blueprintEnvironment));
        RENDER_TYPE_GRAPH.forEach(g -> g.tests().accept(event, renderTypeGraphEnvironment));
    }

    public static void registerFunctionTest(
            RegisterGameTestsEvent event,
            String path,
            ResourceKey<Consumer<GameTestHelper>> functionKey,
            TestData<Holder<TestEnvironmentDefinition<?>>> testData
    ) {
        event.registerTest(id(path), new FunctionGameTestInstance(functionKey, testData));
    }
}
