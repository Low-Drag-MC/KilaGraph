package com.lowdragmc.kilagraph.rendertype.preview;

import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.runtime.KGPipelines;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeFactory;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import org.jetbrains.annotations.Nullable;

/**
 * The material a live preview draws, rebuilt without stalling the editor: a changed graph's pipeline compiles in
 * the background while the last material keeps drawing. One build runs at a time; edits made meanwhile wait for it,
 * and only the latest of them is built next — dragging a value doesn't queue a compile per frame. Render thread.
 */
public final class PreviewMaterialSlot implements AutoCloseable {

    /** What draws; {@code null} until the first build is done. */
    @Nullable private RenderTypeGraphMaterial material;
    /** The build in flight. */
    @Nullable private CompiledShaderGraph building;
    /** The latest request, built once {@link #building} is done. */
    @Nullable private CompiledShaderGraph requested;
    private boolean failed;

    /** The material to draw now: the last one built. */
    @Nullable
    public RenderTypeGraphMaterial material() {
        return material;
    }

    /** Whether the last build didn't compile (the material before it keeps drawing). */
    public boolean failed() {
        return failed;
    }

    /** Ask for {@code compiled}'s material, built by the next {@link #update}s; the current one draws meanwhile. */
    public void request(CompiledShaderGraph compiled) {
        requested = compiled;
    }

    /** Take a finished build, then start the latest request. Call each frame before drawing. */
    public void update() {
        while (true) {
            if (building != null) {
                if (RenderTypeFactory.buildInBackground(building) == KGPipelines.State.COMPILING) return;
                RenderTypeGraphMaterial built = RenderTypeFactory.createPreviewMaterial(building); // null (logged): failed
                if (built != null) {
                    if (material != null) material.close();
                    material = built;
                }
                failed = built == null;
                building = null;
            }
            if (requested == null) return;
            CompiledShaderGraph next = requested;
            requested = null;
            if (material != null && material.contentHash().equals(next.contentHash())) {
                // GLSL unchanged (pipeline reused), but a value-only edit (texture / sampler params / uniform default)
                // may have changed the baked defaults — re-bake them onto the existing material.
                material.refreshDefaults(next);
                failed = false;
                return;
            }
            // Started by the next pass of the loop — and taken at once when already compiled or refused.
            building = next;
        }
    }

    @Override
    public void close() {
        if (building != null) RenderTypeFactory.abandonBuild(building);
        building = null;
        requested = null;
        if (material != null) material.close();
        material = null;
    }
}
