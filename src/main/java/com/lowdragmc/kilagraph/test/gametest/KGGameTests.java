package com.lowdragmc.kilagraph.test.gametest;

import com.lowdragmc.kilagraph.Kilagraph;
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

        // Each group registers its functions here. Add a line per group as it lands; the matching
        // call goes into registerGameTests below.
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
        ExecIntegrationGameTest.registerFunctions();

        TEST_FUNCTIONS.register(eventBus);
        eventBus.addListener(KGGameTests::registerGameTests);
    }

    static DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> registerFunction(
            String path, Consumer<GameTestHelper> function) {
        return TEST_FUNCTIONS.register(path, () -> function);
    }

    static ResourceKey<Consumer<GameTestHelper>> functionKey(String path) {
        return ResourceKey.create(Registries.TEST_FUNCTION, id(path));
    }

    static TestData<Holder<TestEnvironmentDefinition<?>>> defaultTestData(
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
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                id("nodegraph"),
                new TestEnvironmentDefinition.AllOf()
        );
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
        ExecIntegrationGameTest.register(event, environment);
    }

    static void registerFunctionTest(
            RegisterGameTestsEvent event,
            String path,
            ResourceKey<Consumer<GameTestHelper>> functionKey,
            TestData<Holder<TestEnvironmentDefinition<?>>> testData
    ) {
        event.registerTest(id(path), new FunctionGameTestInstance(functionKey, testData));
    }
}
