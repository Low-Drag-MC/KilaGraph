package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.rendertype.compiler.InstanceAttribute;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The per-instance vertex layout (binding 1, step rate 1) of a graph's Instance Data attributes. */
public final class KGInstanceLayout {

    private final Map<String, InstanceAttribute> attributes = new LinkedHashMap<>();
    private final Map<String, Integer> offsets = new LinkedHashMap<>();
    private final VertexFormat format;

    private KGInstanceLayout(List<InstanceAttribute> attributes) {
        VertexFormat.Builder builder = VertexFormat.builder(1);
        int offset = 0;
        for (InstanceAttribute attribute : attributes) {
            this.attributes.put(attribute.name(), attribute);
            offsets.put(attribute.name(), offset);
            GpuFormat column = GpuFormat.valueOf(attribute.type().gpuFormat);
            for (int i = 0; i < attribute.type().columns; i++) {
                builder.addAttribute(attribute.attribName(i), column);
            }
            offset += attribute.type().bytes();
        }
        format = builder.build();
    }

    /** {@code null} when the graph reads no per-instance data. */
    @Nullable
    public static KGInstanceLayout of(List<InstanceAttribute> attributes) {
        return attributes.isEmpty() ? null : new KGInstanceLayout(attributes);
    }

    public VertexFormat format() {
        return format;
    }

    public int stride() {
        return format.getVertexSize();
    }

    /** The attribute an Instance Data node named {@code name} reads, or {@code null}. */
    @Nullable
    public InstanceAttribute attribute(String name) {
        return attributes.get(name);
    }

    public List<InstanceAttribute> attributes() {
        return List.copyOf(attributes.values());
    }

    int offset(String name) {
        return offsets.get(name);
    }
}
