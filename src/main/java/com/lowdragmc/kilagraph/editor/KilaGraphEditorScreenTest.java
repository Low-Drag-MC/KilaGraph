package com.lowdragmc.kilagraph.editor;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.test.ui.IScreenTest;
import net.minecraft.world.entity.player.Player;

/**
 * Dev-only screen test that opens {@link KilaGraphEditor} in a {@link ModularUI}. Triggered from
 * the client via {@code /ldlib2_screen_test kilagraph_editor}.
 *
 * <p>LDLib2 only initialises its {@code screen_test} registry under {@code Platform.isDevEnv()},
 * so this class has no effect in a production build — exactly what we want for a test entry.</p>
 */
@LDLRegisterClient(name = "kilagraph_editor", registry = "ldlib2:screen_test")
public class KilaGraphEditorScreenTest implements IScreenTest {

    public KilaGraphEditorScreenTest() {}

    @Override
    public ModularUI createUI(Player entityPlayer) {
        var root = new EditorWindow(KilaGraphEditor::new);
        return new ModularUI(UI.of(root))
                .shouldCloseOnEsc(false)
                .shouldCloseOnKeyInventory(false);
    }
}
