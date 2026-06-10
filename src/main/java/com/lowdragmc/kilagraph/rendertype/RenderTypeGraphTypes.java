package com.lowdragmc.kilagraph.rendertype;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandleHelpers;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.utils.LDLibExtraCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

/**
 * The custom {@link TypeHandle}s for RenderTypeGraph shader values. The vector types use JOML
 * {@code Vector2f/3f/4f} so that unconnected ports get LDLib2's built-in inline XYZ(W) editors
 * (registered {@code Vector*fAccessor} + codecs) for free. {@code MAT4} has no editable value yet
 * (empty {@code Mat4Value}); {@code SAMPLER2D} is an opaque sampler handle.
 */
public final class RenderTypeGraphTypes {
    public static final TypeHandle VEC2 = TypeHandleHelpers.customType(Vector2f.class, "KG_VEC2", "Vec2");
    public static final TypeHandle VEC3 = TypeHandleHelpers.customType(Vector3f.class, "KG_VEC3", "Vec3");
    public static final TypeHandle VEC4 = TypeHandleHelpers.customType(Vector4f.class, "KG_VEC4", "Vec4");
    public static final TypeHandle MAT4 = TypeHandleHelpers.customType(Mat4Value.class, "KG_MAT4", "Mat4");
    public static final TypeHandle SAMPLER2D = TypeHandleHelpers.customType(Sampler2DValue.class, "KG_SAMPLER2D", "Sampler2D");

    static {
        // A custom object type's constant starts at its registered default-value supplier (else null).
        // LDLib2's Vector*fAccessor dereferences the value with no null-check when building an editor,
        // so without these every vec port / Constant node / variable NPEs on open. Registered here at
        // class init (before any constant is created) so Constant#setTypeHandle resolves a non-null value
        // for every surface centrally — no per-port default needed.
        TypeHandleHelpers.setCustomDefaultValue(VEC2, Vector2f::new);
        TypeHandleHelpers.setCustomDefaultValue(VEC3, Vector3f::new);
        TypeHandleHelpers.setCustomDefaultValue(VEC4, Vector4f::new);
        TypeHandleHelpers.setCustomDefaultValue(SAMPLER2D, Sampler2DValue::defaultValue);
        // The SAMPLER2D configurator (custom/atlas picker + sampler params + preview) is client-only;
        // the lambda references it lazily so the compiler/headless path never loads the UI class.
        TypeHandleHelpers.setCustomConfigurable(SAMPLER2D, (valueConfigurable, typeHandle) ->
                com.lowdragmc.kilagraph.rendertype.gui.Sampler2DConfigurator.build(valueConfigurable));
    }

    /** Node-palette type-picker handles shared by RenderTypeGraph and ShaderFunctionGraph. */
    public static final List<TypeHandle> SUPPORT_TYPES = List.of(
            TypeHandles.BOOL, TypeHandles.INT, TypeHandles.FLOAT, TypeHandles.STRING,
            VEC2, VEC3, VEC4, MAT4, SAMPLER2D);

    /**
     * Blackboard-variable types (GLSL-representable only — STRING excluded). {@link TypeHandles#COLOR}
     * is offered alongside {@code VEC4}: it carries the built-in ARGB color-picker configurator but
     * compiles to a {@code vec4} (see {@link com.lowdragmc.kilagraph.rendertype.compiler.GlslType#of}),
     * so it's the ergonomic way to declare a color uniform without editing raw xyzw.
     */
    public static final List<TypeHandle> VARIABLE_SUPPORT_TYPES = List.of(
            TypeHandles.BOOL, TypeHandles.INT, TypeHandles.FLOAT,
            VEC2, VEC3, VEC4, TypeHandles.COLOR, MAT4, SAMPLER2D);

    /**
     * Types offered as draggable "Constant" nodes in the item library — scalars only. Vectors come from
     * the Vec2/3/4 assembly nodes, colors from the Color node, and textures from the Texture node (each
     * is a dedicated node with its own configurator), so vec/mat/sampler constants would be redundant.
     */
    public static final List<TypeHandle> CONSTANT_SUPPORT_TYPES = List.of(
            TypeHandles.BOOL, TypeHandles.INT, TypeHandles.FLOAT);

    private RenderTypeGraphTypes() {}

    public record Mat4Value() {}

    /** How a {@link Sampler2DValue}'s {@code location} is picked in the configurator (binding is identical). */
    public enum SamplerMode implements StringRepresentable {
        CUSTOM("custom"), ATLAS("atlas");
        private final String name;
        SamplerMode(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }

    /** Texture filtering (maps to {@code com.mojang.blaze3d.textures.FilterMode}). */
    public enum SamplerFilter implements StringRepresentable {
        NEAREST("nearest"), LINEAR("linear");
        private final String name;
        SamplerFilter(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }

    /** Texture address/wrap mode (maps to {@code com.mojang.blaze3d.textures.AddressMode}). */
    public enum SamplerAddress implements StringRepresentable {
        REPEAT("repeat"), CLAMP("clamp");
        private final String name;
        SamplerAddress(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }

    /**
     * A SAMPLER2D value: which texture to bind ({@code location} — a texture file id in CUSTOM mode or an
     * atlas id in ATLAS mode; binding is identical, the mode only changes the picker UI) plus the GPU
     * sampler parameters. Serialized via {@link #CODEC} (registered as an accessor at mod init so it
     * round-trips in graph NBT).
     */
    public record Sampler2DValue(String location, SamplerMode mode, SamplerFilter filter,
                                 SamplerAddress address, boolean mipmap) {
        public static Sampler2DValue defaultValue() {
            return new Sampler2DValue("", SamplerMode.CUSTOM, SamplerFilter.NEAREST, SamplerAddress.CLAMP, false);
        }

        public Sampler2DValue withLocation(String location) {
            return new Sampler2DValue(location, mode, filter, address, mipmap);
        }
    }

    public static final Codec<Sampler2DValue> SAMPLER2D_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("location", "").forGetter(Sampler2DValue::location),
            LDLibExtraCodecs.enumCodec(SamplerMode.class, SamplerMode.CUSTOM)
                    .optionalFieldOf("mode", SamplerMode.CUSTOM).forGetter(Sampler2DValue::mode),
            LDLibExtraCodecs.enumCodec(SamplerFilter.class, SamplerFilter.NEAREST)
                    .optionalFieldOf("filter", SamplerFilter.NEAREST).forGetter(Sampler2DValue::filter),
            LDLibExtraCodecs.enumCodec(SamplerAddress.class, SamplerAddress.CLAMP)
                    .optionalFieldOf("address", SamplerAddress.CLAMP).forGetter(Sampler2DValue::address),
            Codec.BOOL.optionalFieldOf("mipmap", false).forGetter(Sampler2DValue::mipmap)
    ).apply(i, Sampler2DValue::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Sampler2DValue> SAMPLER2D_STREAM_CODEC =
            StreamCodec.of(
                    (buf, v) -> {
                        buf.writeUtf(v.location());
                        buf.writeVarInt(v.mode().ordinal());
                        buf.writeVarInt(v.filter().ordinal());
                        buf.writeVarInt(v.address().ordinal());
                        buf.writeBoolean(v.mipmap());
                    },
                    buf -> new Sampler2DValue(
                            buf.readUtf(),
                            SamplerMode.values()[buf.readVarInt()],
                            SamplerFilter.values()[buf.readVarInt()],
                            SamplerAddress.values()[buf.readVarInt()],
                            buf.readBoolean()));
}
