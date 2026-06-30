package com.lowdragmc.kilagraph.rendertype.iris;

import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.lowdragmc.kilagraph.rendertype.runtime.ShaderUniformBlock;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlSampler;
import com.mojang.blaze3d.opengl.GlTexture;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL41;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Drives the per-draw state the injected {@code kg_surface} shading needs (see {@link IrisShaderInjector})
 * while a shaderpack owns the (shared) gbuffers program: the {@code kg_surface_id} discriminator <em>and</em>
 * the active material's {@code KG_Material}/managed UBOs, bound onto that program by raw GL.
 *
 * <p><b>Timing.</b> Iris pairs our {@code RenderPipeline} with the shaderpack's {@code GlProgram} and binds
 * that program lazily inside {@code GlCommandEncoder.trySetup} — <em>after</em> our {@code RenderType.draw}
 * "before drawIndexed" hook (where {@code GL_CURRENT_PROGRAM} is still 0). So {@code RenderTypeMixin} only
 * records the {@link #currentMaterial} around the draw; {@link #applyToBoundProgram()} (a {@code trySetup}
 * {@code @RETURN} hook) writes the id and binds the UBOs onto the actually-bound program, and
 * {@link #clearBoundProgram()} resets the id afterwards so an unrelated draw on the same program isn't
 * flagged as ours.</p>
 *
 * <p>The material's {@code KG_Material} UBO is bound to the injected per-id block {@code KG_Material_<id>}
 * ({@link IrisSurfaceRegistry#materialBlockName}); managed UBOs ({@code KG_Globals}/{@code KG_Transforms})
 * bind by their own (shared) names. We use <b>high</b> uniform-buffer binding points (near
 * {@code GL_MAX_UNIFORM_BUFFER_BINDINGS}) to avoid colliding with the shaderpack's own low-indexed blocks;
 * Iris rebinds its blocks per draw, so transiently assigning bindings on the shared program is safe.</p>
 */
public final class IrisSurfaceUniform {

    private static final String UNIFORM = "kg_surface_id";
    /** {@code kg_surface_id} location per GL program id (avoids a glGetUniformLocation every draw). */
    private static final Map<Integer, Integer> LOCATION = new HashMap<>();
    /** Uniform-block index per "program:blockName" (avoids a glGetUniformBlockIndex every draw). */
    private static final Map<String, Integer> BLOCK_INDEX = new HashMap<>();

    /** The material currently being drawn (null = not ours). Render-thread only. */
    @Nullable private static RenderTypeGraphMaterial currentMaterial;
    private static int currentSurfaceId = 0;

    /** Sampler uniform location per "program:samplerName" (avoids a glGetUniformLocation every draw). */
    private static final Map<String, Integer> SAMPLER_LOCATION = new HashMap<>();

    /** Highest legal UBO binding point + 1, queried once; our high binding points sit just below it. */
    private static int maxUboBindings = -1;
    /** Fragment texture-image-unit count, queried once; our sampler units sit just below it. */
    private static int maxTexImageUnits = -1;
    /** Reflected {@code GlBuffer.handle} (protected) — read once, then per buffer. */
    @Nullable private static Field handleField;

    private IrisSurfaceUniform() {}

    /** Record the material of the draw we're about to issue (set before {@code drawIndexed}). */
    public static void setCurrent(@Nullable RenderTypeGraphMaterial material) {
        currentMaterial = material;
        currentSurfaceId = material != null ? material.irisSurfaceId() : 0;
    }

    /** Write {@link #currentSurfaceId} + bind the material's UBOs onto the currently-bound program. Called at
     *  {@code trySetup} return, where the shaderpack program is bound. No-op when nothing is ours (the common
     *  case) so non-KilaGraph draws cost only an int check. */
    public static void applyToBoundProgram() {
        if (currentSurfaceId == 0) return; // nothing of ours drawing — leave the program untouched
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (program == 0) return;
        writeSurfaceId(program, currentSurfaceId);
        bindMaterialBlocks(program);
        bindMaterialSamplers(program);
    }

    /** Reset the discriminator on the bound program to 0 after our draw, so geometry sharing this program
     *  is not mistaken for ours. Called from the draw's "after drawIndexed" hook (program still bound). */
    public static void clearBoundProgram() {
        if (currentSurfaceId != 0) {
            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            if (program != 0) writeSurfaceId(program, 0);
        }
        currentSurfaceId = 0;
        currentMaterial = null;
    }

    private static void writeSurfaceId(int program, int value) {
        int location = location(program);
        if (location < 0) return; // program not injected (or optimised the uniform away)
        GL41.glProgramUniform1i(program, location, value);
    }

    /** Bind the active material's {@code KG_Material_<id>} + managed UBOs onto {@code program}. */
    private static void bindMaterialBlocks(int program) {
        RenderTypeGraphMaterial m = currentMaterial;
        if (m == null) return;
        int base = managedBindingBase();
        GpuBufferSlice material = m.irisMaterialSlice();
        if (material != null) {
            bindBlock(program, IrisSurfaceRegistry.materialBlockName(currentSurfaceId), material, base);
        }
        int bp = base + 1;
        for (ShaderUniformBlock block : m.irisUniformBlocks()) {
            GpuBufferSlice slice = block.slice();
            if (slice != null) bindBlock(program, block.uboName(), slice, bp++);
        }
    }

    /** Point block {@code blockName} of {@code program} at binding point {@code bp} and bind {@code slice}
     *  there. No-op if the program doesn't declare the block (e.g. a surface registered after this program
     *  was compiled — awaiting a shader reload). */
    private static void bindBlock(int program, String blockName, GpuBufferSlice slice, int bp) {
        int index = blockIndex(program, blockName);
        if (index == GL31.GL_INVALID_INDEX) return;
        int handle = bufferHandle(slice.buffer());
        if (handle == 0) return;
        GL31.glUniformBlockBinding(program, index, bp);
        GL30.glBindBufferRange(GL31.GL_UNIFORM_BUFFER, bp, handle, slice.offset(), slice.length());
    }

    /** Bind the active material's dynamic samplers (textures) onto {@code program} at high texture units, via
     *  <em>raw</em> GL. We deliberately bypass {@code GlStateManager}, which only tracks the low 12 units
     *  ({@code TEXTURES[0..11]}) and would throw {@code ArrayIndexOutOfBounds} on our high units; the high
     *  units it never tracks, so leaving textures bound there is invisible to it. We restore the active
     *  texture unit to its prior value so {@code GlStateManager}'s cached {@code activeTexture} stays
     *  consistent with real GL for the shaderpack's own subsequent draws. */
    private static void bindMaterialSamplers(int program) {
        RenderTypeGraphMaterial m = currentMaterial;
        if (m == null) return;
        int base = textureUnitBase();
        int prevActiveUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] next = {0};
        m.bindIrisSamplers((name, view, sampler) -> {
            int location = samplerLocation(program, name);
            if (location < 0) return; // program doesn't sample it (e.g. stale program awaiting reload)
            int unit = base + next[0]++;
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, ((GlTexture) view.texture()).glId());
            GL33.glBindSampler(unit, ((GlSampler) sampler).getId());
            GL41.glProgramUniform1i(program, location, unit);
        });
        if (next[0] > 0) GL13.glActiveTexture(prevActiveUnit); // keep real GL == GlStateManager's cached unit
    }

    private static int samplerLocation(int program, String name) {
        String key = program + ":" + name;
        Integer cached = SAMPLER_LOCATION.get(key);
        if (cached != null) return cached;
        int loc = GL20.glGetUniformLocation(program, name);
        SAMPLER_LOCATION.put(key, loc);
        return loc;
    }

    /** First texture unit for our samplers; kept high to dodge the shaderpack's own (low-indexed) samplers. */
    private static int textureUnitBase() {
        if (maxTexImageUnits < 0) maxTexImageUnits = GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS);
        return Math.max(0, maxTexImageUnits - 4);
    }

    private static int location(int program) {
        Integer cached = LOCATION.get(program);
        if (cached != null) return cached;
        int loc = GL20.glGetUniformLocation(program, UNIFORM);
        LOCATION.put(program, loc);
        return loc;
    }

    private static int blockIndex(int program, String blockName) {
        String key = program + ":" + blockName;
        Integer cached = BLOCK_INDEX.get(key);
        if (cached != null) return cached;
        int index = GL31.glGetUniformBlockIndex(program, blockName);
        BLOCK_INDEX.put(key, index);
        return index;
    }

    /** Binding point for the material block; managed blocks follow at +1, +2, … Kept high to dodge the
     *  shaderpack's own (low-indexed) uniform blocks on the shared program. */
    private static int managedBindingBase() {
        if (maxUboBindings < 0) maxUboBindings = GL11.glGetInteger(GL31.GL_MAX_UNIFORM_BUFFER_BINDINGS);
        return Math.max(0, maxUboBindings - 8);
    }

    /** The raw GL handle of a {@code GpuBuffer} (a {@code GlBuffer} on the GL backend), via cached reflection
     *  of its {@code protected final int handle}. 0 on failure (binding then skipped). */
    private static int bufferHandle(GpuBuffer buffer) {
        try {
            if (handleField == null) {
                Field f = Class.forName("com.mojang.blaze3d.opengl.GlBuffer").getDeclaredField("handle");
                f.setAccessible(true);
                handleField = f;
            }
            return handleField.getInt(buffer);
        } catch (Throwable t) {
            return 0;
        }
    }
}
