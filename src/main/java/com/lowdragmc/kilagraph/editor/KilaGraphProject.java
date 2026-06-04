package com.lowdragmc.kilagraph.editor;

import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.project.ProjectType;
import com.lowdragmc.lowdraglib2.editor.resource.ColorsResource;
import com.lowdragmc.lowdraglib2.editor.resource.IRendererResource;
import com.lowdragmc.lowdraglib2.editor.resource.Resources;
import com.lowdragmc.lowdraglib2.editor.resource.TexturesResource;
import com.lowdragmc.lowdraglib2.editor.resource.UIResource;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;

/**
 * The {@link IProject} type managed by the KilaGraph test editor. Hosts a
 * {@link BlueprintGraphResource} group plus the common LDLib2 resources (colors / textures /
 * renderers / UI) so the editor's Resources panel looks familiar.
 *
 * <p>Persisted as {@code *.kilagraph.nbt} via LDLib2's standard project save/load.</p>
 */
public class KilaGraphProject implements IProject {
    public static final ProjectType TYPE =
            ProjectType.of(IGuiTexture.EMPTY, "project.kilagraph", ".kilagraph.nbt", KilaGraphProject::new);

    private final Resources resources;

    public KilaGraphProject() {
        this.resources = Resources.of(
                ColorsResource.INSTANCE,
                TexturesResource.INSTANCE,
                IRendererResource.INSTANCE,
                UIResource.INSTANCE,
                BlueprintGraphResource.INSTANCE
        );
    }

    @Override
    public ProjectType getProjectType() {
        return TYPE;
    }

    @Override
    public Resources getResources() {
        return resources;
    }

    @Override
    public void serializeProject(@NotNull ValueOutput output) {
    }

    @Override
    public void deserializeProject(@NotNull ValueInput input) {
    }
}
