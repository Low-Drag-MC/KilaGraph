package com.lowdragmc.kilagraph.rendertype.runtime;

import org.jetbrains.annotations.Nullable;

/**
 * The shared {@code KG_Transforms} uniform block — coordinate-space matrices KilaGraph precomputes each
 * draw so shader nodes (notably the {@code Transform} node) can convert between Object / View / World /
 * Clip space without per-pixel {@code inverse()} calls.
 *
 * <pre>{@code
 * layout(std140) uniform KG_Transforms {
 *     mat4 IModelViewMat;  // view  -> object  = inverse(ModelViewMat)
 *     mat4 ViewMat;        // world -> view    (camera rotation)
 *     mat4 IViewMat;       // view  -> world   = inverse(ViewMat)
 *     mat4 IProjMat;       // clip  -> view    = inverse(ProjMat)
 * } kg_transforms;
 * }</pre>
 *
 * <p>TODO(1.21-backport milestone 2): the CPU-side matrix computation + GPU upload (backed by
 * {@code com.mojang.blaze3d.buffers.GpuBuffer} / {@code Std140Builder} and the LDLib2
 * {@code SceneCameraContext}) was removed for the compile-only milestone — those blaze3d APIs do not exist
 * in 1.21.1 and the LDLib2 scene-camera API differs. Only the version-independent GLSL declaration /
 * accessors are kept so the shader compiler still emits correct source. Reimplement the buffer upload +
 * camera sourcing against the 1.21.1 rendering model.</p>
 */
public final class KGTransformUniforms {

    public static final String UBO_NAME = "KG_Transforms";
    public static final String UBO_INSTANCE = "kg_transforms";

    /** The {@link ShaderUniformBlock} view a node registers via {@code ctx.useUniformBlock(...)}. */
    public static final ShaderUniformBlock BLOCK = new ShaderUniformBlock() {
        @Override public String uboName() { return UBO_NAME; }
        @Override public String declareGlsl() { return KGTransformUniforms.declareGlsl(); }
        @Override public void prepareUpload() { KGTransformUniforms.prepareUpload(); }
        @Override public Object slice() { return KGTransformUniforms.slice(); }
    };

    private KGTransformUniforms() {}

    /** GLSL declaration of the transforms block (must match the buffer layout exactly). */
    public static String declareGlsl() {
        return "layout(std140) uniform " + UBO_NAME + " {\n"
                + "    mat4 IModelViewMat;\n"
                + "    mat4 ViewMat;\n"
                + "    mat4 IViewMat;\n"
                + "    mat4 IProjMat;\n"
                + "} " + UBO_INSTANCE + ";\n";
    }

    /** GLSL accessor for a field, e.g. {@code kg_transforms.ViewMat}. */
    public static String accessor(String field) {
        return UBO_INSTANCE + "." + field;
    }

    // TODO(1.21-backport milestone 2): recompute the coordinate-space matrices + rebuild the GpuBuffer.
    public static void prepareUpload() {
        throw new UnsupportedOperationException("KG_Transforms UBO upload: 1.21.1 backport pending (milestone 2)");
    }

    // TODO(1.21-backport milestone 2): return type was com.mojang.blaze3d.buffers.GpuBufferSlice.
    @Nullable
    public static Object slice() {
        throw new UnsupportedOperationException("KG_Transforms UBO slice: 1.21.1 backport pending (milestone 2)");
    }
}
