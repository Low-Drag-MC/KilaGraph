package com.lowdragmc.kilagraph.graph.type;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;

import java.lang.reflect.Type;

/**
 * KilaGraph's {@link CustomGraphModelImpl} subclass. Relaxes {@code canAssignTo} so that:
 * <ul>
 *   <li>{@code EXECUTION_FLOW} stays strictly typed (EXEC ↔ EXEC only).</li>
 *   <li>{@code UNKNOWN} connects to anything non-EXEC (CastNode and similar fall back to a
 *       runtime check via {@code EvalContext.coerce}).</li>
 *   <li>Any {@link Number} → any other {@link Number} (Int↔Float, Long↔Double, ...).</li>
 *   <li>Anything (non-EXEC) → {@link String} (via {@code Object.toString()} at evaluate time).</li>
 * </ul>
 *
 * <p>The runtime side of these rules lives in
 * {@link com.lowdragmc.kilagraph.graph.exec.EvalContext#coerce}. The graph-model side only
 * controls whether the editor will accept the wire; mismatched types within these relaxations
 * are deferred to per-node logic.</p>
 */
public class KGGraphModel extends CustomGraphModelImpl {

    public KGGraphModel(Graph graph) {
        super(graph);
    }

    @Override
    public boolean canAssignTo(PortModel destination, PortModel source) {
        TypeHandle dst = destination.getDataTypeHandle();
        TypeHandle src = source.getDataTypeHandle();

        if (dst.equals(TypeHandles.EXECUTION_FLOW) || src.equals(TypeHandles.EXECUTION_FLOW)) {
            return dst.equals(TypeHandles.EXECUTION_FLOW) && src.equals(TypeHandles.EXECUTION_FLOW);
        }
        if (dst.equals(TypeHandles.UNKNOWN) || src.equals(TypeHandles.UNKNOWN)) {
            return true;
        }

        Type dstType = destination.getPortDataType();
        Type srcType = source.getPortDataType();

        // Any Number → any Number.
        if (isNumber(dstType) && isNumber(srcType)) return true;

        // Anything → String.
        if (dstType == String.class) return true;

        return super.canAssignTo(destination, source);
    }

    private static boolean isNumber(Type t) {
        return t instanceof Class<?> c && Number.class.isAssignableFrom(c);
    }
}
