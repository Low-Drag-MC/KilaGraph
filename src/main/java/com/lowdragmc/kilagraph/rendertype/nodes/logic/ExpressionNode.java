package com.lowdragmc.kilagraph.rendertype.nodes.logic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.INodeValidator;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.gui.ExpressionConfigurator;
import com.lowdragmc.kilagraph.graph.util.NodeTooltipHelper;
import com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A user-authored GLSL node (Unity's "Custom Function", String mode): the user declares any number of typed
 * inputs and outputs plus a GLSL body, and the node compiles to a per-instance helper function called from
 * the shader. This is also the way to express real control flow — {@code if}/{@code for}/{@code while} — that
 * the flat-expression compiler can't represent as wires (the {@link BranchNode} is only a data-flow select).
 *
 * <p>The spec (inputs, outputs, body) lives in one STRING option as JSON, edited via
 * {@link ExpressionConfigurator}; changing it re-defines the node's ports automatically (the option framework
 * calls {@code defineNode}). Compilation registers
 * {@code void kg_expr_<uid>(in <T> <in>…, out <T> <out>…) { <body> }} via {@code ctx.function(...)} and calls
 * it with the input expressions and freshly-declared out temps — so the body reads each input by its port
 * name and writes each output by its port name. All existing GLSL types are available
 * (FLOAT/INT/BOOL/VEC2/3/4/MAT4 for either side; SAMPLER2D for inputs only — it can't be an {@code out}).</p>
 */
@NodeAttribute(name = "rt_expression", group = "rendertype_logic", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ExpressionNode extends ShaderNode implements INodeValidator {

    public static final String OPTION = "spec";

    @Override
    protected @Nullable Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_expression.tooltip");
    }

    /** Wider than a normal node so the inputs/outputs row + GLSL body editor have room. */
    @Override
    public float getNodeWidth() {
        return 200f;
    }

    /** One declared port: a GLSL identifier {@code name} and a {@link GlslType} name {@code type}. */
    public record PortSpec(String name, String type) {}

    /** The full node spec: ordered inputs + outputs + the GLSL body. */
    public record ExpressionSpec(List<PortSpec> inputs, List<PortSpec> outputs, String body) {}

    // NB: an output named "out" (or any GLSL reserved word) would emit `out float out` — illegal. The
    // default uses a safe name; user-typed reserved names produce a compile error surfaced in the preview.
    public static final ExpressionSpec DEFAULT_SPEC = new ExpressionSpec(
            List.of(new PortSpec("a", "FLOAT")), List.of(new PortSpec("result", "FLOAT")), "result = a;");
    public static final String DEFAULT_JSON = toJson(DEFAULT_SPEC);

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption(OPTION, TypeHandles.STRING)
                .withDefaultValue(DEFAULT_JSON)
                .withDisplayName(Component.empty())
                .withTooltips(Tooltips.of("kg.node.rt_expression.option.spec.tooltip"))
                .withConfigurable((vc, t) -> ExpressionConfigurator.build(vc)).build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        Defined defined = defined(currentSpec());
        // Inputs are wired (or read a constant node) — never an inline port editor. A reused PortModel whose
        // type changes (FLOAT→VEC3) across defineNode keeps its old value-typed configurator (e.g. a
        // NumberConfigurator), which then ClassCasts the new Vector3f value at screenTick. Disabling the
        // inline editor sidesteps that entirely (and matches how these are meant to be used).
        for (PortSpec p : defined.inputs) context.addInputPort(p.name(), handleOf(p.type())).withoutConfigurator().build();
        for (PortSpec p : defined.outputs) context.addOutputPort(p.name(), handleOf(p.type()));
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        Defined defined = defined(currentSpec());
        String fn = "kg_expr_" + sanitize(ctx.node().getUid().toString());

        // void kg_expr_x(in T a, out T out) { <body> }
        StringBuilder params = new StringBuilder();
        for (PortSpec p : defined.inputs) {
            if (!params.isEmpty()) params.append(", ");
            params.append("in ").append(glslType(p.type()).glsl()).append(' ').append(p.name());
        }
        for (PortSpec p : defined.outputs) {
            if (!params.isEmpty()) params.append(", ");
            params.append("out ").append(glslType(p.type()).glsl()).append(' ').append(p.name());
        }
        ctx.function(fn, "void " + fn + "(" + params + ") {\n" + currentSpec().body() + "\n}");

        // Gather call args: input expressions (converted to the param type) + declared out temps.
        List<String> args = new ArrayList<>();
        for (PortSpec p : defined.inputs) {
            args.add(ctx.convert(ctx.input(p.name()), glslType(p.type())).code());
        }
        List<ShaderExpr> outVars = new ArrayList<>();
        for (PortSpec p : defined.outputs) {
            GlslType gt = glslType(p.type());
            ShaderExpr o = ctx.temp(gt, zero(gt)); // a uniquely-named, declared lvalue for the out param
            outVars.add(o);
            args.add(o.code());
        }
        ctx.line(fn + "(" + String.join(", ", args) + ");");

        for (int i = 0; i < defined.outputs.size(); i++) {
            ctx.output(defined.outputs.get(i).name(), outVars.get(i));
        }
    }

    @Override
    protected String previewOutputPortId() {
        List<PortSpec> outs = defined(currentSpec()).outputs;
        return outs.isEmpty() ? null : outs.getFirst().name();
    }

    // ---- validation (surfaced in the editor's GraphLogger, keyed to this node) ----------------

    @Override
    public List<Component> validationErrors() {
        List<Component> errors = new ArrayList<>();
        ExpressionSpec spec = currentSpec();
        Set<String> seen = new LinkedHashSet<>();
        for (PortSpec p : spec.inputs()) checkPort(p, false, seen, errors);
        for (PortSpec p : spec.outputs()) checkPort(p, true, seen, errors);
        return errors;
    }

    /** Validate one port's name (legal GLSL identifier, not reserved, unique) + type (no sampler output). */
    private static void checkPort(PortSpec p, boolean isOutput, Set<String> seen, List<Component> errors) {
        String name = p.name() == null ? "" : p.name().trim();
        if (!isValidIdentifier(name)) {
            errors.add(Component.translatable("rendertypegraph.error.expression_invalid_name", name));
        } else if (RESERVED.contains(name)) {
            errors.add(Component.translatable("rendertypegraph.error.expression_reserved_name", name));
        } else if (!seen.add(name)) {
            errors.add(Component.translatable("rendertypegraph.error.expression_duplicate_name", name));
        }
        if (isOutput && "SAMPLER2D".equals(normalizeType(p.type()))) {
            errors.add(Component.translatable("rendertypegraph.error.expression_sampler_output", name));
        }
    }

    private static boolean isValidIdentifier(String s) {
        if (s == null || s.isEmpty() || !(Character.isLetter(s.charAt(0)) || s.charAt(0) == '_')) return false;
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) return false;
        }
        return true;
    }

    /** GLSL reserved words that can't be a parameter/identifier name (the ones a user is likely to hit). */
    private static final Set<String> RESERVED = Set.of(
            "in", "out", "inout", "uniform", "const", "attribute", "varying", "layout", "flat", "smooth",
            "void", "bool", "int", "uint", "float", "double",
            "vec2", "vec3", "vec4", "ivec2", "ivec3", "ivec4", "bvec2", "bvec3", "bvec4", "uvec2", "uvec3", "uvec4",
            "mat2", "mat3", "mat4", "sampler2D", "sampler3D", "samplerCube",
            "if", "else", "for", "while", "do", "return", "break", "continue", "discard", "switch", "case", "default",
            "true", "false", "struct", "precision", "highp", "mediump", "lowp", "main", "gl_Position", "gl_FragCoord");

    // ---- spec helpers ------------------------------------------------------------------------

    private ExpressionSpec currentSpec() {
        INodeOption opt = getNodeOptionById(OPTION);
        if (opt == null) return DEFAULT_SPEC;
        Object raw = opt.tryGetValue(Object.class).result().orElse(null);
        return raw instanceof String s && !s.isEmpty() ? fromJson(s) : DEFAULT_SPEC;
    }

    /** A spec with each port name sanitized to a legal GLSL identifier and deduped across in+out (so the
     *  generated function signature can't collide). Empty/duplicate names and SAMPLER2D outputs are dropped. */
    private record Defined(List<PortSpec> inputs, List<PortSpec> outputs) {}

    private static Defined defined(ExpressionSpec spec) {
        Set<String> used = new LinkedHashSet<>();
        List<PortSpec> ins = new ArrayList<>();
        for (PortSpec p : spec.inputs()) {
            String name = sanitize(p.name());
            if (name.isEmpty() || !used.add(name)) continue;
            ins.add(new PortSpec(name, normalizeType(p.type())));
        }
        List<PortSpec> outs = new ArrayList<>();
        for (PortSpec p : spec.outputs()) {
            String name = sanitize(p.name());
            String type = normalizeType(p.type());
            if (name.isEmpty() || !used.add(name) || "SAMPLER2D".equals(type)) continue; // sampler can't be `out`
            outs.add(new PortSpec(name, type));
        }
        return new Defined(ins, outs);
    }

    private static TypeHandle handleOf(String type) {
        return switch (normalizeType(type)) {
            case "INT" -> TypeHandles.INT;
            case "BOOL" -> TypeHandles.BOOL;
            case "VEC2" -> RenderTypeGraphTypes.VEC2;
            case "VEC3" -> RenderTypeGraphTypes.VEC3;
            case "VEC4" -> RenderTypeGraphTypes.VEC4;
            case "MAT4" -> RenderTypeGraphTypes.MAT4;
            case "SAMPLER2D" -> RenderTypeGraphTypes.SAMPLER2D;
            default -> TypeHandles.FLOAT;
        };
    }

    private static GlslType glslType(String type) {
        try {
            return GlslType.valueOf(normalizeType(type));
        } catch (IllegalArgumentException e) {
            return GlslType.FLOAT;
        }
    }

    /** The valid GLSL types a port may declare, surfaced to the configurator's type pickers. */
    public static final List<String> INPUT_TYPES = List.of("FLOAT", "INT", "BOOL", "VEC2", "VEC3", "VEC4", "MAT4", "SAMPLER2D");
    public static final List<String> OUTPUT_TYPES = List.of("FLOAT", "INT", "BOOL", "VEC2", "VEC3", "VEC4", "MAT4");

    private static String normalizeType(String type) {
        return type == null ? "FLOAT" : type.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** Reduce to a legal GLSL identifier body ([A-Za-z_][A-Za-z0-9_]*), collapsing illegal runs to '_'. */
    private static String sanitize(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            sb.append((c < 128 && (Character.isLetterOrDigit(c) || c == '_')) ? c : '_');
        }
        String s = sb.toString().replaceAll("_+", "_");
        if (s.isEmpty() || s.equals("_")) return "";
        if (Character.isDigit(s.charAt(0))) s = "_" + s; // identifiers can't start with a digit
        return s;
    }

    // ---- JSON round-trip (manual, no record reflection) --------------------------------------

    public static String toJson(ExpressionSpec spec) {
        JsonObject root = new JsonObject();
        root.add("inputs", portsToJson(spec.inputs()));
        root.add("outputs", portsToJson(spec.outputs()));
        root.addProperty("body", spec.body());
        return root.toString();
    }

    private static JsonArray portsToJson(List<PortSpec> ports) {
        JsonArray arr = new JsonArray();
        for (PortSpec p : ports) {
            JsonObject o = new JsonObject();
            o.addProperty("name", p.name());
            o.addProperty("type", p.type());
            arr.add(o);
        }
        return arr;
    }

    public static ExpressionSpec fromJson(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            List<PortSpec> inputs = portsFromJson(root.getAsJsonArray("inputs"));
            List<PortSpec> outputs = portsFromJson(root.getAsJsonArray("outputs"));
            String body = root.has("body") ? root.get("body").getAsString() : "";
            return new ExpressionSpec(inputs, outputs, body);
        } catch (RuntimeException e) {
            return DEFAULT_SPEC;
        }
    }

    private static List<PortSpec> portsFromJson(JsonArray arr) {
        List<PortSpec> ports = new ArrayList<>();
        if (arr == null) return ports;
        for (var el : arr) {
            JsonObject o = el.getAsJsonObject();
            String name = o.has("name") ? o.get("name").getAsString() : "";
            String type = o.has("type") ? o.get("type").getAsString() : "FLOAT";
            ports.add(new PortSpec(name, type));
        }
        return ports;
    }

    private static String zero(GlslType type) {
        return switch (type) {
            case FLOAT -> "0.0";
            case INT -> "0";
            case BOOL -> "false";
            case VEC2 -> "vec2(0.0)";
            case VEC3 -> "vec3(0.0)";
            case VEC4 -> "vec4(0.0)";
            case MAT4 -> "mat4(1.0)";
            case SAMPLER2D, GRADIENT, CURVE -> "0"; // unreachable (opaque outputs are dropped)
        };
    }

    @Override
    public String glslExample() {
        return """
                // the default spec compiles to
                void kg_expr_1f3a(in float a, out float result) {
                    result = a;
                }
                kg_expr_1f3a(<a>, kg_tmp0);""";
    }
}
