package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The step between writing a node and being able to use one: that it is in the registry under the id
 * it declares, and that both language files name it.
 *
 * <p><b>Neither failure shows up in a behaviour test.</b> A node class whose annotation never reached
 * the {@code AutoRegistry} scan still evaluates perfectly when a test constructs it directly — it
 * simply cannot be dropped on a canvas. A node with no lang entry renders as its raw id, which looks
 * like a typo rather than a missing key. So both are asked of the whole registry here, once, rather
 * than being left to whoever remembers.</p>
 */
public final class NodeRegistrationGameTest {
    private static final String RECENT_IN_LIBRARY = "node_registration_recent_nodes_in_library";
    private static final String ID_OWNERSHIP = "node_registration_every_node_owns_its_id";
    private static final String NAMED_IN_BOTH = "node_registration_named_in_both_languages";
    private static final String SAME_KEYS = "node_registration_languages_cover_the_same_keys";

    /**
     * The nodes v26.1.0.15 added. A list rather than a count of the registry, because the interesting
     * failure is "the one just written is missing", and a total would only move when it did.
     */
    private static final String[] RECENT_NODES = {
            "vector_to_vec2", "vector_to_vec3", "vector_to_vec4",

            "math_wrap", "math_snap", "math_step", "math_smoothstep", "math_inverse_lerp",
            "math_delta_angle", "math_move_towards", "math_nearly_equals", "math_wave",

            "vector_direction_to", "vector_set_length", "vector_slerp", "vector_perpendicular",
            "vector_wrap",

            "quat_identity", "quat_from_axis_angle", "quat_from_euler", "quat_to_euler",
            "quat_from_to", "quat_multiply", "quat_inverse", "quat_normalize", "quat_slerp",
            "quat_rotate_vector", "quat_angle_between", "quat_to_vec4", "quat_from_vec4",
    };

    public static void registerFunctions() {
        KGGameTests.registerFunction(RECENT_IN_LIBRARY, NodeRegistrationGameTest::theRecentNodesAreInTheLibrary);
        KGGameTests.registerFunction(ID_OWNERSHIP, NodeRegistrationGameTest::everyRegisteredNodeOwnsItsId);
        KGGameTests.registerFunction(NAMED_IN_BOTH, NodeRegistrationGameTest::everyRegisteredNodeIsNamedInBothLanguages);
        KGGameTests.registerFunction(SAME_KEYS, NodeRegistrationGameTest::theTwoLanguagesCoverTheSameKeys);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        var data = KGGameTests.defaultTestData(environment);
        for (String p : new String[]{RECENT_IN_LIBRARY, ID_OWNERSHIP, NAMED_IN_BOTH, SAME_KEYS}) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), data);
        }
    }

    private NodeRegistrationGameTest() {
    }

    public static void theRecentNodesAreInTheLibrary(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        for (String id : RECENT_NODES) {
            var holder = BlueprintGraph.NODE_REGISTRY.get(id);
            if (holder == null || holder.clazz() == null) {
                failures.add(id + " is not registered — it cannot be dropped on a canvas");
                continue;
            }
            NodeAttribute attribute = holder.clazz().getAnnotation(NodeAttribute.class);
            if (attribute == null || !id.equals(attribute.name())) {
                failures.add(id + " resolves to " + holder.clazz().getSimpleName()
                        + ", which declares " + (attribute == null ? "nothing" : attribute.name()));
            }
        }
        if (!failures.isEmpty()) {
            helper.fail(failures.size() + " missing: " + String.join(" | ", failures));
            return;
        }

        if (RECENT_NODES.length != 30) {
            helper.fail("expected 30 recent nodes, the list has " + RECENT_NODES.length);
            return;
        }
        helper.succeed();
    }

    /**
     * Two node classes declaring the same {@code name} is a silent overwrite: one of them simply
     * stops existing, and only the other's tests are there to notice.
     */
    public static void everyRegisteredNodeOwnsItsId(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, Class<? extends Node>> e : registeredNodes().entrySet()) {
            var holder = BlueprintGraph.NODE_REGISTRY.get(e.getKey());
            if (holder == null || !e.getValue().equals(holder.clazz())) {
                failures.add(e.getKey() + " is declared by " + e.getValue().getName()
                        + " but resolves to "
                        + (holder == null || holder.clazz() == null ? "nothing" : holder.clazz().getName()));
            }
        }
        if (!failures.isEmpty()) {
            helper.fail(failures.size() + " id clash(es): " + String.join(" | ", failures));
            return;
        }
        helper.succeed();
    }

    public static void everyRegisteredNodeIsNamedInBothLanguages(GameTestHelper helper) {
        JsonObject en = lang(helper, "en_us");
        JsonObject zh = lang(helper, "zh_cn");
        if (en == null || zh == null) return;

        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, Class<? extends Node>> e : registeredNodes().entrySet()) {
            String id = e.getKey();
            NodeAttribute attribute = e.getValue().getAnnotation(NodeAttribute.class);
            check(failures, en, "en_us", id, "display name");
            check(failures, zh, "zh_cn", id, "display name");
            check(failures, en, "en_us", "kg.node." + id + ".tooltip", "tooltip");
            check(failures, zh, "zh_cn", "kg.node." + id + ".tooltip", "tooltip");
            if (attribute == null) continue;

            // A group is a path: "ui/element" needs both segments named, or the library tree shows
            // a raw id for the parent folder.
            for (String segment : attribute.group().split("/")) {
                if (segment.isEmpty()) continue;
                check(failures, en, "en_us", segment, "group segment of " + id);
                check(failures, zh, "zh_cn", segment, "group segment of " + id);
            }
        }
        if (!failures.isEmpty()) {
            helper.fail(failures.size() + " untranslated: "
                    + String.join(" | ", failures.subList(0, Math.min(12, failures.size()))));
            return;
        }
        helper.succeed();
    }

    /**
     * The two files describe the same set of keys. Catches the half of a translation that was added
     * to one and not the other — which reads as a working game in one language and raw keys in the
     * other, and which no per-node check above would see for a key that is not a node id.
     */
    public static void theTwoLanguagesCoverTheSameKeys(GameTestHelper helper) {
        JsonObject en = lang(helper, "en_us");
        JsonObject zh = lang(helper, "zh_cn");
        if (en == null || zh == null) return;

        List<String> onlyEn = new ArrayList<>();
        List<String> onlyZh = new ArrayList<>();
        for (String k : en.keySet()) {
            if (!zh.has(k)) onlyEn.add(k);
        }
        for (String k : zh.keySet()) {
            if (!en.has(k)) onlyZh.add(k);
        }
        if (!onlyEn.isEmpty() || !onlyZh.isEmpty()) {
            helper.fail(onlyEn.size() + " key(s) only in en_us "
                    + onlyEn.subList(0, Math.min(8, onlyEn.size()))
                    + ", " + onlyZh.size() + " only in zh_cn "
                    + onlyZh.subList(0, Math.min(8, onlyZh.size())));
            return;
        }
        helper.succeed();
    }

    private static Map<String, Class<? extends Node>> registeredNodes() {
        Map<String, Class<? extends Node>> byId = new LinkedHashMap<>();
        for (Class<? extends Node> cls : BlueprintGraph.NODE_REGISTRY.getNodeClasses()) {
            NodeAttribute attribute = cls.getAnnotation(NodeAttribute.class);
            if (attribute != null && !attribute.name().isEmpty()) {
                byId.put(attribute.name(), cls);
            }
        }
        return byId;
    }

    private static void check(List<String> failures, JsonObject lang, String file,
                              String key, String what) {
        if (!lang.has(key) || lang.get(key).getAsString().isBlank()) {
            String entry = file + ": " + key + " (" + what + ")";
            if (!failures.contains(entry)) failures.add(entry);
        }
    }

    private static JsonObject lang(GameTestHelper helper, String code) {
        String path = "/assets/" + Kilagraph.MODID + "/lang/" + code + ".json";
        try (InputStream in = NodeRegistrationGameTest.class.getResourceAsStream(path)) {
            if (in == null) {
                helper.fail("no lang file on the classpath at " + path);
                return null;
            }
            return JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            helper.fail("could not read " + path + ": " + e);
            return null;
        }
    }
}
