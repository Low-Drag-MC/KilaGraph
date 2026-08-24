package com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt;

import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;

/**
 * The kinds of value an NBT get/set node can read or write, and the {@link TypeHandle} each maps
 * to for the node's value port.
 */
public enum NbtValueType {
    STRING, INT, LONG, FLOAT, DOUBLE, BOOL, COMPOUND;

    /**
     * The names offered by the option dropdown, in the order they should appear.
     *
     * <p>{@code STRING} leads because it is the default and the one a graph reaches for when it does not
     * yet know what is in the tag.</p>
     */
    public static final List<String> CHOICES =
            List.of("STRING", "INT", "LONG", "FLOAT", "DOUBLE", "BOOL", "COMPOUND");

    /** TypeHandle of the value port for this kind. */
    public TypeHandle portType() {
        return switch (this) {
            case INT -> TypeHandles.INT;
            case LONG -> TypeHandles.LONG;
            case FLOAT -> TypeHandles.FLOAT;
            case DOUBLE -> TypeHandles.DOUBLE;
            case BOOL -> TypeHandles.BOOL;
            case COMPOUND -> KGTypeHandles.NBT_COMPOUND;
            default -> TypeHandles.STRING;
        };
    }

    /** What a read produces when there is nothing to read: the zero of this kind, never null. */
    public Object defaultValue() {
        return switch (this) {
            case INT -> 0;
            case LONG -> 0L;
            case FLOAT -> 0f;
            case DOUBLE -> 0d;
            case BOOL -> false;
            case COMPOUND -> new CompoundTag();
            default -> "";
        };
    }

    /**
     * {@code tag} read as this kind, or {@link #defaultValue()} when it cannot be.
     *
     * <p>Numbers go through {@link NumericTag} rather than a cast, so an int read as a double works —
     * NBT stores whichever width was written and a graph should not have to know which. {@code BOOL}
     * follows the game and calls anything non-zero true.
     *
     * <p>26.1's {@code Tag.asInt()}/{@code asString()} views say the same thing, but each answers an
     * {@code Optional} — an allocation per read, on a node a graph may evaluate every tick. The type
     * test costs nothing and reads the value directly.</p>
     *
     * <p>{@code STRING} is the exception that accepts everything: a string tag gives its contents and
     * anything else its SNBT text, which is what makes it usable for looking at a value whose shape
     * you do not know yet.</p>
     */
    public Object fromTag(@Nullable Tag tag) {
        if (tag == null) return defaultValue();
        return switch (this) {
            case INT -> tag instanceof NumericTag n ? n.intValue() : 0;
            case LONG -> tag instanceof NumericTag n ? n.longValue() : 0L;
            case FLOAT -> tag instanceof NumericTag n ? n.floatValue() : 0f;
            case DOUBLE -> tag instanceof NumericTag n ? n.doubleValue() : 0d;
            case BOOL -> tag instanceof NumericTag n && n.byteValue() != 0;
            case COMPOUND -> tag instanceof CompoundTag c ? c : new CompoundTag();
            default -> tag instanceof StringTag s ? s.value() : tag.toString();
        };
    }
}
