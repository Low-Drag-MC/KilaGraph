package com.lowdragmc.kilagraph.test.uitest;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

/** Smoke test: the full editor ({@code /ldlib2_screen_test kilagraph_editor}) opens and lays out. */
@LDLRegisterClient(name = "kg_editor", group = "kilagraph", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public class EditorOpensScenario implements UIScenario {

    @Override
    public void configure(ScenarioOptions options) {
        options.defaultSettleMs(80).tags("kilagraph", "editor").guiScale(2);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreenTest("kilagraph_editor")
                .awaitModularUI()
                .settleMs(400)
                .check("the blueprint node registry is populated",
                        ctx -> !BlueprintGraph.NODE_REGISTRY.getNodeClasses().isEmpty())
                .check("the rendertype node registry is populated",
                        ctx -> !RenderTypeGraph.NODE_REGISTRY.getNodeClasses().isEmpty())
                .frames(10)
                .screenshot("01_editor")
                .closeScreen();
    }
}
