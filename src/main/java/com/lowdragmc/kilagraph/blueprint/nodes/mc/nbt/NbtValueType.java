package com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt;

import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;

/**
 * The kinds of value an NBT get/set node can read or write, and the {@link TypeHandle} each maps
 * to for the node's value port.
 */
public enum NbtValueType {
    STRING, INT, LONG, FLOAT, DOUBLE, BOOL, COMPOUND;

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
}
