package com.lowdragmc.kilagraph.test.gametest;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.test.gametest.blueprint.CacheGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ComparisonNodeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ConvertNodeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ExecCombinationsGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ExecIntegrationGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ExecLoopsGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ExecPrimitivesGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ExecRuntimeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ExecSemanticsGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.InfoNodeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.ListNodeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.LogicNodeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.MapNodeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.MathNodeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.McDataGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.McWorldQueryGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.OptionalNodeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.SetVarGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.StringNodeGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.SubgraphGameTest;
import com.lowdragmc.kilagraph.test.gametest.blueprint.VariableGameTest;
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

/**
 * Central registration entrypoint for all KilaGraph GameTests. Mirrors LDLib2's
 * {@code NodeGraphGameTests}. Each test group's class exposes {@code registerFunctions()} and
 * {@code register(event, environment)}; this class calls both during mod init / RegisterGameTestsEvent.
 *
 * <p>Why GameTests instead of JUnit: many code paths the executor walks (variable creation,
 * subgraph instantiation, NBT serialization) load Minecraft classes (registries, NBT codecs,
 * ResourceLocation) that aren't available in a plain JUnit run. GameTests run under a real
 * GameTestServer, mirroring LDLib2's own test convention.</p>
 */
public final class KGGameTests {

    private static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
            DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, Kilagraph.MODID);
    private static boolean initialized = false;

    private KGGameTests() {}

    public static void init(IEventBus eventBus) {
        if (initialized) return;
        initialized = true;

        // Function registry is global; environment assignment is split in registerGameTests below.
        registerBlueprintFunctions();
        registerRenderTypeGraphFunctions();

        TEST_FUNCTIONS.register(eventBus);
        eventBus.addListener(KGGameTests::registerGameTests);
    }

    private static void registerBlueprintFunctions() {
        VariableGameTest.registerFunctions();
        SubgraphGameTest.registerFunctions();
        OptionalNodeGameTest.registerFunctions();
        LogicNodeGameTest.registerFunctions();
        ComparisonNodeGameTest.registerFunctions();
        ConvertNodeGameTest.registerFunctions();
        MathNodeGameTest.registerFunctions();
        StringNodeGameTest.registerFunctions();
        ListNodeGameTest.registerFunctions();
        MapNodeGameTest.registerFunctions();
        ExecRuntimeGameTest.registerFunctions();
        ExecPrimitivesGameTest.registerFunctions();
        SetVarGameTest.registerFunctions();
        ExecLoopsGameTest.registerFunctions();
        ExecSemanticsGameTest.registerFunctions();
        ExecIntegrationGameTest.registerFunctions();
        ExecCombinationsGameTest.registerFunctions();
        CacheGameTest.registerFunctions();
        McDataGameTest.registerFunctions();
        InfoNodeGameTest.registerFunctions();
        McWorldQueryGameTest.registerFunctions();
    }

    private static void registerRenderTypeGraphFunctions() {
        RenderTypeGraphGameTest.registerFunctions();
        ShaderCompilerGameTest.registerFunctions();
        ShaderSubgraphGameTest.registerFunctions();
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
        // Reuse LDLib2's empty structure — node-graph tests don't need any world blocks.
        return new TestData<>(
                environment,
                Identifier.fromNamespaceAndPath("ldlib2", structurePath),
                20,
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

        registerBlueprintTests(event, blueprintEnvironment);
        registerRenderTypeGraphTests(event, renderTypeGraphEnvironment);
    }

    private static void registerBlueprintTests(
            RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> environment
    ) {
        VariableGameTest.register(event, environment);
        SubgraphGameTest.register(event, environment);
        OptionalNodeGameTest.register(event, environment);
        LogicNodeGameTest.register(event, environment);
        ComparisonNodeGameTest.register(event, environment);
        ConvertNodeGameTest.register(event, environment);
        MathNodeGameTest.register(event, environment);
        StringNodeGameTest.register(event, environment);
        ListNodeGameTest.register(event, environment);
        MapNodeGameTest.register(event, environment);
        ExecRuntimeGameTest.register(event, environment);
        ExecPrimitivesGameTest.register(event, environment);
        SetVarGameTest.register(event, environment);
        ExecLoopsGameTest.register(event, environment);
        ExecSemanticsGameTest.register(event, environment);
        ExecIntegrationGameTest.register(event, environment);
        ExecCombinationsGameTest.register(event, environment);
        CacheGameTest.register(event, environment);
        McDataGameTest.register(event, environment);
        InfoNodeGameTest.register(event, environment);
        McWorldQueryGameTest.register(event, environment);
    }

    private static void registerRenderTypeGraphTests(
            RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> environment
    ) {
        RenderTypeGraphGameTest.register(event, environment);
        ShaderCompilerGameTest.register(event, environment);
        ShaderSubgraphGameTest.register(event, environment);
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
