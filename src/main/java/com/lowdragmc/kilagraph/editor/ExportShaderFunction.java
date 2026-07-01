package com.lowdragmc.kilagraph.editor;

import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.GraphElementModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.GraphModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.VariableNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.ModifierFlags;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableScope;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.wire.WireModel;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a standalone, reusable {@link ShaderFunctionGraph} from a selection of nodes in a host graph —
 * <b>non-destructively</b> (the source graph is left untouched). This is the model-level half of the editor's
 * "Export as Shader Function" action; it mirrors the non-destructive front of
 * {@code GraphModel.extractSelectionToLocalSubgraph} (copy the nodes, turn each crossing wire into a
 * READ/WRITE boundary variable) but pastes into a fresh graph instead of an inline local subgraph and never
 * deletes the originals or creates an outer subgraph node.
 *
 * <p>Kept UI-free so it is unit-testable in a headless GameTest; the view wires it up + persists the result as
 * a resource. The resulting graph, once saved, is draggable as an EXTERNAL subgraph (the inline/resolve path
 * already exists) and the shader compiler inlines it.</p>
 */
public final class ExportShaderFunction {
    private ExportShaderFunction() {}

    /** A wire with exactly one selected endpoint. {@code fromSelected} = the output (from) side is selected,
     *  i.e. the cluster produces this value (→ a WRITE/output variable); otherwise it consumes one (→ READ). */
    private record Crossing(WireModel wire, boolean fromSelected) {}

    /**
     * Build a new {@link ShaderFunctionGraph} from {@code selection} (nodes of {@code source}). Returns
     * {@code null} if the selection has no copiable nodes. Does not modify {@code source}.
     */
    @Nullable
    public static ShaderFunctionGraph build(GraphModel source, List<? extends GraphElementModel> selection,
                                            HolderLookup.Provider provider) {
        List<AbstractNodeModel> nodes = new ArrayList<>();
        for (var element : selection) {
            if (element instanceof AbstractNodeModel n && n.isCopiable()) nodes.add(n);
        }
        if (nodes.isEmpty()) return null;

        Set<UUID> selectedUids = new HashSet<>();
        for (var n : nodes) selectedUids.add(n.getUid());

        // Crossing wires: one endpoint selected. From-selected → output of the function (WRITE), else input (READ).
        List<Crossing> crossing = new ArrayList<>();
        Set<WireModel> seenWires = new HashSet<>();
        for (var n : nodes) {
            for (var wire : n.getConnectedWires()) {
                if (wire == null || !seenWires.add(wire)) continue;
                PortModel from = wire.getFromPort();
                PortModel to = wire.getToPort();
                if (from == null || to == null || from.getNodeModel() == null || to.getNodeModel() == null) continue;
                boolean fromSel = selectedUids.contains(from.getNodeModel().getUid());
                boolean toSel = selectedUids.contains(to.getNodeModel().getUid());
                if (fromSel == toSel) continue; // fully internal or unrelated
                crossing.add(new Crossing(wire, fromSel));
            }
        }

        // Centroid (node positions only) so the pasted cluster sits around the origin of the new graph.
        Vector2f centroid = new Vector2f();
        for (var n : nodes) centroid.add(n.getPosition());
        if (!nodes.isEmpty()) centroid.div(nodes.size());

        ShaderFunctionGraph target = new ShaderFunctionGraph();
        GraphModel tgt = target.graphModel;

        // One LOCAL READ/WRITE variable per crossing wire (the function's interface).
        Map<Crossing, VariableDeclarationModel> crossingVars = new HashMap<>();
        int in = 0, out = 0;
        for (var c : crossing) {
            ModifierFlags mod = c.fromSelected ? ModifierFlags.WRITE : ModifierFlags.READ;
            String name = c.fromSelected ? "out" + (++out) : "in" + (++in);
            var type = c.fromSelected ? c.wire.getFromPort().getDataTypeHandle() : c.wire.getToPort().getDataTypeHandle();
            var vdm = tgt.createGraphVariableDeclaration(type, name, mod, VariableScope.LOCAL,
                    null, Integer.MAX_VALUE, null, null, null);
            if (vdm != null) crossingVars.put(c, vdm);
        }

        // Copy the selected nodes into the new graph.
        var copyData = source.copyElements(nodes, provider);
        var pasted = tgt.pasteElementsWithMap(copyData, new Vector2f(-centroid.x, -centroid.y));
        Map<UUID, AbstractNodeModel> oldToNew = pasted.oldToNewNodeMap();

        // Wire each boundary variable's node to the matching pasted internal port.
        for (var c : crossing) {
            var vdm = crossingVars.get(c);
            if (vdm == null) continue;
            var internalOldNode = c.fromSelected ? c.wire.getFromPort().getNodeModel() : c.wire.getToPort().getNodeModel();
            var internalPortName = c.fromSelected ? c.wire.getFromPort().getUniqueName() : c.wire.getToPort().getUniqueName();
            var pastedNode = oldToNew.get(internalOldNode.getUid());
            if (pastedNode == null) continue;
            var pastedPort = GraphModel.findPortByUniqueName(pastedNode, internalPortName);
            if (pastedPort == null) continue;

            float dx = c.fromSelected ? 80f : -80f;
            var pos = new Vector2f(pastedNode.getPosition().x + dx, pastedNode.getPosition().y);
            VariableNodeModel varNode = tgt.createVariableNode(vdm, pos, null, null);
            PortModel varPort = c.fromSelected ? varNode.getInputPort() : varNode.getOutputPort();
            if (varPort == null) continue;
            if (c.fromSelected) {
                tgt.createWire(varPort, pastedPort);   // WRITE var input <- internal output
            } else {
                tgt.createWire(pastedPort, varPort);    // internal input <- READ var output
            }
        }
        return target;
    }

    /** Serialize a function graph to the resource {@link CompoundTag} the ShaderFunction resource round-trips
     *  (just the graph model — function graphs carry no extra settings). */
    public static CompoundTag serialize(ShaderFunctionGraph graph) {
        // 1.21.1: no TagValueOutput — LDLib2-1.21's graph model serializes directly to a CompoundTag.
        return graph.graphModel.serializeNBT(Platform.getFrozenRegistry());
    }
}
