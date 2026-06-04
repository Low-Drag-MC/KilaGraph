package com.lowdragmc.kilagraph.graph.exec;

import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;

import java.util.List;
import java.util.stream.Collectors;

public class CycleException extends RuntimeException {
    public CycleException(List<AbstractNodeModel> cyclePath) {
        super("Cycle detected during graph evaluation: " +
                cyclePath.stream().map(n -> n.getUid().toString()).collect(Collectors.joining(" -> ")));
    }
}
