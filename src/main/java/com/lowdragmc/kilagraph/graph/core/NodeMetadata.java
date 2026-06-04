package com.lowdragmc.kilagraph.graph.core;

import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.IOptionBuilder;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.IInputPortBuilder;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.IOutputPortBuilder;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortCapacity;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortDirection;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflection-derived description of an {@link AnnotatedNode} class. One instance per Node class
 * (cached in {@link #CACHE}). Trivial fields with concrete Java types declare {@link InputPort},
 * {@link OutputPort}, {@link ExecInputPort}, {@link ExecOutputPort}, or {@link Option}; anything
 * the annotation surface cannot express belongs in
 * {@link AnnotatedNode#onDefinePorts(IPortDefinitionContext)} / overrides.
 */
final class NodeMetadata {

    enum Kind { INPUT_PORT, OUTPUT_PORT, OPTION }

    record FieldDef(
            Field field,
            Kind kind,
            String id,
            String display,
            PortCapacity capacity,
            TypeHandle typeHandle,
            boolean execFlow
    ) {}

    static final Map<Class<?>, NodeMetadata> CACHE = new ConcurrentHashMap<>();

    private final List<FieldDef> defs;

    private NodeMetadata(List<FieldDef> defs) { this.defs = defs; }

    static NodeMetadata scan(Class<?> nodeClass) {
        var list = new ArrayList<FieldDef>();
        for (Class<?> c = nodeClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                FieldDef def = scanField(f);
                if (def != null) list.add(def);
            }
        }
        return new NodeMetadata(List.copyOf(list));
    }

    private static FieldDef scanField(Field f) {
        InputPort      in      = f.getAnnotation(InputPort.class);
        OutputPort     out     = f.getAnnotation(OutputPort.class);
        ExecInputPort  execIn  = f.getAnnotation(ExecInputPort.class);
        ExecOutputPort execOut = f.getAnnotation(ExecOutputPort.class);
        Option         opt     = f.getAnnotation(Option.class);

        int present = (in != null ? 1 : 0) + (out != null ? 1 : 0) + (execIn != null ? 1 : 0)
                + (execOut != null ? 1 : 0) + (opt != null ? 1 : 0);
        if (present == 0) return null;
        if (present > 1) {
            throw new IllegalStateException("Field " + f + " has more than one KilaGraph annotation");
        }

        f.setAccessible(true);

        if (in != null) {
            String id = in.name().isEmpty() ? f.getName() : in.name();
            return new FieldDef(f, Kind.INPUT_PORT, id, in.display(), in.capacity(),
                    KGTypeHandles.handleFor(f.getGenericType()), false);
        }
        if (out != null) {
            String id = out.name().isEmpty() ? f.getName() : out.name();
            return new FieldDef(f, Kind.OUTPUT_PORT, id, out.display(), out.capacity(),
                    KGTypeHandles.handleFor(f.getGenericType()), false);
        }
        if (execIn != null) {
            String id = execIn.name().isEmpty() ? f.getName() : execIn.name();
            return new FieldDef(f, Kind.INPUT_PORT, id, execIn.display(), PortCapacity.SINGLE,
                    TypeHandles.EXECUTION_FLOW, true);
        }
        if (execOut != null) {
            String id = execOut.name().isEmpty() ? f.getName() : execOut.name();
            return new FieldDef(f, Kind.OUTPUT_PORT, id, execOut.display(), PortCapacity.MULTIPLE,
                    TypeHandles.EXECUTION_FLOW, true);
        }
        // opt != null — LDLib2 forbids UNKNOWN / EXEC / MISSING here; concrete types only.
        String id = opt.name().isEmpty() ? f.getName() : opt.name();
        return new FieldDef(f, Kind.OPTION, id, opt.display(), PortCapacity.NONE,
                KGTypeHandles.handleFor(f.getGenericType()), false);
    }

    void applyOptions(IOptionDefinitionContext ctx, AnnotatedNode node) {
        for (FieldDef d : defs) {
            if (d.kind != Kind.OPTION) continue;
            IOptionBuilder<?> b = ctx.addOption(d.id, d.typeHandle);
            if (!d.display.isEmpty()) b.withDisplayName(Component.literal(d.display));
            if (hasAccessor(d.field.getGenericType())) {
                Object def = readFieldValue(d.field, node);
                if (def != null) b.withDefaultValue(def);
                b.withFieldContext(d.field, node);
            } else {
                // No registered accessor → no UI editor + no serialisable embedded constant.
                // The Java field initialiser still serves as evaluate()'s fallback default.
                b.withoutConfigurator();
            }
        }
    }

    void applyPorts(IPortDefinitionContext ctx, AnnotatedNode node) {
        for (FieldDef d : defs) {
            if (d.kind == Kind.OPTION) continue;
            if (d.kind == Kind.INPUT_PORT) {
                IInputPortBuilder<?> b = ctx.addInputPort(d.id, d.typeHandle);
                if (!d.display.isEmpty()) b.withDisplayName(Component.literal(d.display));
                if (hasAccessor(d.field.getGenericType())) {
                    Object def = readFieldValue(d.field, node);
                    if (def != null) b.withDefaultValue(def);
                    b.withFieldContext(d.field, node);
                } else {
                    b.withoutConfigurator();
                }
            } else {
                IOutputPortBuilder<?> b = ctx.addOutputPort(d.id, d.typeHandle);
                if (!d.display.isEmpty()) b.withDisplayName(Component.literal(d.display));
            }
        }
    }

    /**
     * Probe LDLib2's {@link AccessorRegistries} for the given type. Used to decide whether the
     * field's value can be (a) serialised into the embedded constant and (b) rendered by the
     * default field configurator. {@code List}, {@code TypeHandle}, custom domain objects, etc.
     * fail this probe; the caller falls back to {@code withoutConfigurator()} for those.
     */
    private static boolean hasAccessor(Type type) {
        try {
            AccessorRegistries.findByType(type);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Object readFieldValue(Field f, Object owner) {
        try {
            return f.get(owner);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read field " + f, e);
        }
    }

    List<FieldDef> getDefs() { return defs; }
}
