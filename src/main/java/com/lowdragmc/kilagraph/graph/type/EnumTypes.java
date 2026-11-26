package com.lowdragmc.kilagraph.graph.type;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.GraphModel;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * The enums a node picks one of — the Enum node's type, and the type Equals and Not Equals may be given: the graph's
 * own types that are enums, and the ones it offers besides ({@link Source}).
 */
public final class EnumTypes {

    /**
     * A graph with enums to offer beyond its types — those of its function calls, say, which no probe of a node class
     * finds. Kept apart from {@code getSupportTypes} on purpose: every type there is also one a variable or a Select can
     * be, and an enum with no default shows its first value in their editors while holding null (the {@code DIRECTION}
     * note in {@link KGTypeHandles}).
     */
    public interface Source {
        List<TypeHandle> enumTypes();
    }

    private EnumTypes() {}

    /** The enums {@code node}'s graph offers: its types that are enums, then its {@link Source}'s. */
    public static List<TypeHandle> of(INode node) {
        return typesAndEnums(node).stream().filter(EnumTypes::isEnum).toList();
    }

    /** {@code node}'s graph's types, then the enums its {@link Source} adds. */
    public static List<TypeHandle> typesAndEnums(INode node) {
        var model = node.getNodeModel() == null ? null : node.getNodeModel().getGraphModel();
        List<TypeHandle> out = new ArrayList<>();
        if (model instanceof GraphModel graph) out.addAll(graph.getSupportTypes());
        if (model instanceof CustomGraphModelImpl custom && custom.getGraph() instanceof Source source) {
            for (TypeHandle type : source.enumTypes()) {
                if (!out.contains(type)) out.add(type);
            }
        }
        return out;
    }

    public static boolean isEnum(TypeHandle type) {
        return type.resolve() instanceof Class<?> c && c.isEnum();
    }

    /**
     * What an unwired input or an option of {@code type} starts at when the type has no default of its own: an enum's
     * first value, which is what its dropdown shows. Null otherwise — the type's own default, or its lack of one,
     * stands.
     */
    @Nullable
    public static Object firstValue(TypeHandle type) {
        if (type.getDefaultValue() != null) return null;
        if (type.resolve() instanceof Class<?> c && c.isEnum()) {
            Object[] values = c.getEnumConstants();
            return values.length > 0 ? values[0] : null;
        }
        return null;
    }
}
