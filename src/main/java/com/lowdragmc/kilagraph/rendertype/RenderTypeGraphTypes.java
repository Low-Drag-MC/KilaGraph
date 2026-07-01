package com.lowdragmc.kilagraph.rendertype;

import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandleHelpers;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.utils.LDLibExtraCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4fc;

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
    /**
     * A "dynamic" float-vector type for math/vector nodes: a single node accepts any float/vec input and
     * infers its output width at compile time (Unity's Dynamic Vector). Backed by {@code Float.class} so an
     * unconnected port gets the built-in inline float editor (a scalar default with no wire), yet its custom
     * identification keeps {@link com.lowdragmc.kilagraph.rendertype.compiler.GlslType#of} returning {@code
     * null} for it — so a DYNAMIC output is passed through uncast and the node tags its own inferred type.
     */
    public static final TypeHandle DYNAMIC = TypeHandleHelpers.customType(Float.class, "KG_DYNAMIC", "Dynamic");
    /**
     * A uv-coordinate type: wire-compatible with {@code VEC2} (compiles to {@code vec2}), but its
     * unconnected port carries a {@link UvChannel} picker (UV0/UV1/UV2). When unconnected the compiler
     * emits the chosen channel's interpolated mesh uv (see {@code ShaderGraphCompiler.pullInput} +
     * {@code meshUv(UvChannel)}), so a uv port "just works" with the mesh's texcoords by default.
     */
    public static final TypeHandle UV = TypeHandleHelpers.customType(UvChannel.class, "KG_UV", "UV");
    /**
     * A Unity-style gradient: a list of colour keys + independent alpha keys with a Blend/Fixed
     * interpolation mode ({@link GradientValue}, wrapping LDLib2's {@link GradientColor}). Like
     * {@code SAMPLER2D} it is an opaque wire type — in GLSL it is a {@code KG_Gradient} struct value
     * (see {@link com.lowdragmc.kilagraph.rendertype.compiler.GlslType#GRADIENT}); a {@code SampleGradient}
     * node turns it + a float position into a {@code vec4}. Carries a full gradient-bar editor.
     */
    public static final TypeHandle GRADIENT = TypeHandleHelpers.customType(GradientValue.class, "KG_GRADIENT", "Gradient");

    static {
        // A custom object type's constant starts at its registered default-value supplier (else null).
        // LDLib2's Vector*fAccessor dereferences the value with no null-check when building an editor,
        // so without these every vec port / Constant node / variable NPEs on open. Registered here at
        // class init (before any constant is created) so Constant#setTypeHandle resolves a non-null value
        // for every surface centrally — no per-port default needed.
        TypeHandleHelpers.setCustomDefaultValue(VEC2, Vector2f::new);
        TypeHandleHelpers.setCustomDefaultValue(VEC3, Vector3f::new);
        TypeHandleHelpers.setCustomDefaultValue(VEC4, Vector4f::new);
        // DYNAMIC resolves to Float, so it reuses the built-in float inline editor; give it the float default
        // (a scalar a user can type when the port is unwired) and the float port colour so it reads sensibly.
        TypeHandleHelpers.setCustomDefaultValue(DYNAMIC, () -> 0.0f);
        TypeHandleHelpers.setCustomColor(DYNAMIC, 0xFF10B4C5);
        TypeHandleHelpers.setCustomDefaultValue(SAMPLER2D, Sampler2DValue::defaultValue);
        // The SAMPLER2D configurator (custom/atlas picker + sampler params + preview) is client-only;
        // the lambda references it lazily so the compiler/headless path never loads the UI class.
        TypeHandleHelpers.setCustomConfigurable(SAMPLER2D, (valueConfigurable, typeHandle) ->
                com.lowdragmc.kilagraph.rendertype.gui.Sampler2DConfigurator.build(valueConfigurable));
        // UV: default to channel UV0, a teal-ish port colour, and a client-only UV0/UV1/UV2 dropdown.
        TypeHandleHelpers.setCustomDefaultValue(UV, () -> UvChannel.UV0);
        TypeHandleHelpers.setCustomColor(UV, 0xFF2EB8A6);
        TypeHandleHelpers.setCustomConfigurable(UV, (valueConfigurable, typeHandle) ->
                com.lowdragmc.kilagraph.rendertype.gui.UvChannelConfigurator.build(valueConfigurable));
        // GRADIENT: a fresh black->white gradient default, a magenta-ish port colour, and the client-only
        // gradient-bar editor (lazy reference so the compiler/headless path never loads the UI class).
        TypeHandleHelpers.setCustomDefaultValue(GRADIENT, GradientValue::defaultValue);
        TypeHandleHelpers.setCustomColor(GRADIENT, 0xFFE05CC0);
        TypeHandleHelpers.setCustomConfigurable(GRADIENT, (valueConfigurable, typeHandle) ->
                com.lowdragmc.kilagraph.rendertype.gui.GradientConfigurator.build(valueConfigurable));
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
            VEC2, VEC3, VEC4, TypeHandles.COLOR, MAT4, SAMPLER2D, GRADIENT);

    /**
     * Types offered as draggable "Constant" nodes in the item library — scalars only. Vectors come from
     * the Vec2/3/4 assembly nodes, colors from the Color node, and textures from the Texture node (each
     * is a dedicated node with its own configurator), so vec/mat/sampler constants would be redundant.
     */
    public static final List<TypeHandle> CONSTANT_SUPPORT_TYPES = List.of(
            TypeHandles.BOOL, TypeHandles.INT, TypeHandles.FLOAT);

    private RenderTypeGraphTypes() {}

    public record Mat4Value() {}

    /**
     * Which mesh uv channel a {@link #UV} port reads when unconnected. UV0 is the texture uv (a float
     * {@code vec2} attribute); UV1/UV2 are Minecraft's overlay/lightmap coords (ivec2, cast to vec2).
     */
    public enum UvChannel implements StringRepresentable {
        UV0("uv0"), UV1("uv1"), UV2("uv2");
        private final String name;
        UvChannel(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }

    public static final Codec<UvChannel> UV_CODEC =
            LDLibExtraCodecs.enumCodec(UvChannel.class, UvChannel.UV0);

    public static final StreamCodec<RegistryFriendlyByteBuf, UvChannel> UV_STREAM_CODEC =
            StreamCodec.of((buf, v) -> buf.writeVarInt(v.ordinal()),
                    buf -> UvChannel.values()[buf.readVarInt()]);

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
     * sampler parameters. Serialized via {@link #SAMPLER2D_CODEC} (registered as an accessor at mod init so it
     * round-trips in graph NBT).
     */
    public record Sampler2DValue(String location, SamplerMode mode, SamplerFilter filter,
                                 SamplerAddress address, boolean mipmap) {
        public static Sampler2DValue defaultValue() {
            return new Sampler2DValue("minecraft:textures/block/dirt.png", SamplerMode.CUSTOM, SamplerFilter.NEAREST, SamplerAddress.CLAMP, false);
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

    /** Gradient interpolation between adjacent keys: smooth {@code BLEND} (lerp) or stepped {@code FIXED}. */
    public enum BlendMode implements StringRepresentable {
        BLEND("blend"), FIXED("fixed");
        private final String name;
        BlendMode(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }

    /**
     * A {@link #GRADIENT} value: the {@link GradientColor} keys (colours + independent alphas) plus the
     * {@link BlendMode}. Serialized via {@link #GRADIENT_CODEC} (registered as an accessor at mod init so
     * it round-trips in graph NBT). {@code GradientColor} is mutable, so {@link #copy()} deep-copies.
     */
    public record GradientValue(GradientColor gradient, BlendMode mode) {
        public static GradientValue defaultValue() {
            return new GradientValue(new GradientColor(0xFF000000, 0xFFFFFFFF), BlendMode.BLEND);
        }

        public GradientValue copy() {
            return new GradientValue(gradient.copy(), mode);
        }

        public GradientValue withMode(BlendMode mode) {
            return new GradientValue(gradient.copy(), mode);
        }
    }

    /** Codec for LDLib2's {@link GradientColor} (its two key lists). Rebuilds via the mutable getters. */
    public static final Codec<GradientColor> GRADIENT_COLOR_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.list(LDLibExtraCodecs.VECTOR2F).fieldOf("a").forGetter(GradientColor::getAP),
            Codec.list(ExtraCodecs.VECTOR4F).fieldOf("rgb").forGetter(GradientColor::getRgbP)
    ).apply(i, (a, rgb) -> {
        GradientColor g = new GradientColor();
        g.getAP().clear();
        g.getAP().addAll(a);
        g.getRgbP().clear();
        g.getRgbP().addAll(rgb);
        return g;
    }));

    public static final Codec<GradientValue> GRADIENT_CODEC = RecordCodecBuilder.create(i -> i.group(
            GRADIENT_COLOR_CODEC.fieldOf("gradient").forGetter(GradientValue::gradient),
            LDLibExtraCodecs.enumCodec(BlendMode.class, BlendMode.BLEND)
                    .optionalFieldOf("mode", BlendMode.BLEND).forGetter(GradientValue::mode)
    ).apply(i, GradientValue::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, GradientValue> GRADIENT_STREAM_CODEC =
            StreamCodec.of(
                    (buf, v) -> {
                        var a = v.gradient().getAP();
                        var rgb = v.gradient().getRgbP();
                        buf.writeVarInt(a.size());
                        for (Vector2fc p : a) { buf.writeFloat(p.x()); buf.writeFloat(p.y()); }
                        buf.writeVarInt(rgb.size());
                        for (Vector4fc p : rgb) {
                            buf.writeFloat(p.x()); buf.writeFloat(p.y()); buf.writeFloat(p.z()); buf.writeFloat(p.w());
                        }
                        buf.writeVarInt(v.mode().ordinal());
                    },
                    buf -> {
                        GradientColor g = new GradientColor();
                        g.getAP().clear();
                        g.getRgbP().clear();
                        int na = buf.readVarInt();
                        for (int k = 0; k < na; k++) g.getAP().add(new Vector2f(buf.readFloat(), buf.readFloat()));
                        int nc = buf.readVarInt();
                        for (int k = 0; k < nc; k++) {
                            g.getRgbP().add(new Vector4f(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
                        }
                        return new GradientValue(g, BlendMode.values()[buf.readVarInt()]);
                    });
}
