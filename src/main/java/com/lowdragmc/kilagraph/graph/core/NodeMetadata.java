package com.lowdragmc.kilagraph.graph.core;

import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.IOptionBuilder;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.IInputPortBuilder;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.IOutputPortBuilder;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortCapacity;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortConnectorUI;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.ITypeConfigurable;
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
 * Reflection-derived description of an annotated node class — {@link AnnotatedNode} or
 * {@link AnnotatedBlockNode}, which is why the apply methods take a plain {@code Node}: the field
 * scan needs an instance only to read declared defaults off it. One instance per Node class
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

    void applyOptions(IOptionDefinitionContext ctx, Node node) {
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

    void applyPorts(IPortDefinitionContext ctx, Node node) {
        // Emit exec-flow ports before data ports so {@code trigger}/{@code next} sit at the top of the
        // node — even when declared in a superclass (the field scan visits subclass fields first, so an
        // inherited exec port would otherwise land at the bottom).
        for (FieldDef d : defs) {
            if (d.kind != Kind.OPTION && d.execFlow) applyPort(ctx, node, d);
        }
        for (FieldDef d : defs) {
            if (d.kind != Kind.OPTION && !d.execFlow) applyPort(ctx, node, d);
        }
    }

    /**
     * ⚠️ <b>Every builder is {@code build()}ed here, before this returns.</b> A port takes its place
     * in the node's display order at the moment it is built — {@code PortBuilder.build()} is what
     * calls {@code NodeModel.addInputPort} — so building here is what makes {@link #applyPorts}'s
     * ordering a fact rather than a hope.
     *
     * <p>A builder left unbuilt is instead finished off by {@code PortDefinitionContext.finish()},
     * which runs <b>after</b> {@code onDefineDynamicPorts}. Today that still lands the declared
     * ports first, because {@code finish()} drains its pending list from the front and these were
     * created first — but only for as long as <i>no</i> dynamic hook builds a port of its own. The
     * first one that does jumps the whole declared block: adding a {@code .build()} to the rpc
     * call's argument loop reorders its pins to {@code [arg1, arg2, trigger, rpc]} without this.
     * Building eagerly costs nothing and removes the dependency on that.
     * {@code NodeContractGameTest.declaredPortsComeBeforeDynamicOnes} is what holds the invariant.
     */
    private void applyPort(IPortDefinitionContext ctx, Node node, FieldDef d) {
        if (d.kind == Kind.INPUT_PORT) {
            IInputPortBuilder<?> b = ctx.addInputPort(d.id, d.typeHandle);
            if (!d.display.isEmpty()) b.withDisplayName(Component.literal(d.display));
            if (d.execFlow) b.withConnectorUI(PortConnectorUI.FLOW);
            if (hasAccessor(d.field.getGenericType())) {
                Object def = readFieldValue(d.field, node);
                if (def != null) b.withDefaultValue(def);
                b.withFieldContext(d.field, node);
            } else if (!hasOwnConfigurable(d.typeHandle)) {
                // ⚠️ Only when the type brings NO editor of its own. "No accessor" answers whether
                // the DEFAULT field configurator can render the value — a type that registered its
                // own ITypeConfigurable has already answered otherwise, and silencing it here left
                // such a pin blank while the same handle drew fine on a blackboard variable.
                b.withoutConfigurator();
            }
            b.build();
        } else {
            IOutputPortBuilder<?> b = ctx.addOutputPort(d.id, d.typeHandle);
            if (!d.display.isEmpty()) b.withDisplayName(Component.literal(d.display));
            if (d.execFlow) b.withConnectorUI(PortConnectorUI.FLOW);
            b.build();
        }
    }

    /** Whether the type registered an {@code ITypeConfigurable} of its own — an editor no accessor is needed for. */
    private static boolean hasOwnConfigurable(TypeHandle typeHandle) {
        return typeHandle != null && typeHandle.resolveConfigurable() != ITypeConfigurable.DEFAULT;
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
