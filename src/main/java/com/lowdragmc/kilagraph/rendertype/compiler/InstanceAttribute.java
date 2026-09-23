package com.lowdragmc.kilagraph.rendertype.compiler;

/**
 * A per-instance vertex attribute a graph reads through an Instance Data node: vertex binding 1, advancing once
 * per instance. A {@code mat4} spans four {@code vec4} attributes ({@code _0} … {@code _3}, its columns).
 */
public record InstanceAttribute(String name, Type type) {

    public enum Type {
        FLOAT(GlslType.FLOAT, "R32_FLOAT", 1, 1),
        VEC2(GlslType.VEC2, "RG32_FLOAT", 2, 1),
        VEC3(GlslType.VEC3, "RGB32_FLOAT", 3, 1),
        VEC4(GlslType.VEC4, "RGBA32_FLOAT", 4, 1),
        INT(GlslType.INT, "R32_SINT", 1, 1),
        MAT4(GlslType.MAT4, "RGBA32_FLOAT", 4, 4);

        public final GlslType glslType;
        /** Name of the {@code GpuFormat} of one column (resolved on the client). */
        public final String gpuFormat;
        /** 32-bit components per column. */
        public final int componentsPerColumn;
        public final int columns;

        Type(GlslType glslType, String gpuFormat, int componentsPerColumn, int columns) {
            this.glslType = glslType;
            this.gpuFormat = gpuFormat;
            this.componentsPerColumn = componentsPerColumn;
            this.columns = columns;
        }

        public int components() {
            return componentsPerColumn * columns;
        }

        public int bytes() {
            return components() * 4;
        }

        /** GLSL type of one column's {@code in} declaration. */
        public String columnGlsl() {
            return this == MAT4 ? "vec4" : glslType.glsl();
        }
    }

    /** The shader attribute name of column {@code column}. */
    public String attribName(int column) {
        return type.columns == 1 ? "kg_inst_" + name : "kg_inst_" + name + "_" + column;
    }
}
