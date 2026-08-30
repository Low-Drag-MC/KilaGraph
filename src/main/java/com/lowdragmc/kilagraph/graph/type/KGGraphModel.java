package com.lowdragmc.kilagraph.graph.type;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Offer {@code EXECUTION_FLOW} in the blackboard variable-type picker (on top of the data types
     * from {@link Graph#getSupportTypes()}), so a subgraph can declare exec in/out variables — an
     * INPUT exec var becomes the subgraph node's exec-in pin, an OUTPUT one an exec-out pin. It is
     * deliberately <em>not</em> added to the data-type pickers (you don't cast to/collect exec-flow).
     */
    @Override
    public List<TypeHandle> getVariableSupportTypes() {
        List<TypeHandle> types = new ArrayList<>(super.getVariableSupportTypes());
        if (!types.contains(TypeHandles.EXECUTION_FLOW)) types.add(TypeHandles.EXECUTION_FLOW);
        return types;
    }

    /** Every vector pin: the three concrete widths, plus the any-width {@code VECTOR}. */
    private static boolean isVector(TypeHandle handle) {
        return KGTypeHandles.VECTOR.equals(handle) || KGTypeHandles.VEC2.equals(handle)
                || KGTypeHandles.VEC3.equals(handle) || KGTypeHandles.VEC4.equals(handle);
    }

    @Override
    public boolean canAssignTo(PortModel destination, PortModel source) {
        TypeHandle dst = destination.getDataTypeHandle();
        TypeHandle src = source.getDataTypeHandle();

        // Any vector into any vector pin. VECTOR accepts every width because that is what it means;
        // VEC2/VEC3/VEC4 accept every width because the operations behind them read whatever arrives
        // and truncate or zero-fill by their own documented rule (vector_cross reads the first three
        // of anything). Refusing a width here would make the editor enforce a rule the machine does
        // not have.
        if (isVector(dst) && isVector(src)) {
            return true;
        }

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
