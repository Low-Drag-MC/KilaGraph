package com.lowdragmc.kilagraph.rendertype.compiler;

/**
 * Implemented by fragment-stage blocks. When the fragment stage is compiled, each block reads its
 * inputs (via {@code ctx}) and writes its contribution into the shared {@link FragmentOutputs}.
 */
public interface IFragmentOutputBlock {

    /** Read inputs and contribute to the fragment stage's semantic outputs. */
    void emitFragment(ShaderCompileContext ctx, FragmentOutputs out);
}
