package com.lowdragmc.kilagraph.rendertype.nodes.artistic.normal;

import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;

import java.util.List;

/**
 * The shared <b>Output Space</b> option for the normal-<em>producing</em> Artistic nodes (Normal From
 * Height / Normal From Texture) — Unity's option of the same name. Both nodes compute a tangent-space
 * normal; this decides whether they hand it back raw or push it through the surface's tangent basis first.
 *
 * <p>Offering <b>world</b> is what turns those nodes from a dead end into something you can light: without
 * it every graph has to wire a Transform node on the end by hand. Tangent stays the default, so existing
 * graphs are unaffected.</p>
 */
public final class NormalSpaces {
    private NormalSpaces() {}

    public static final String OPTION = "space";
    public static final List<String> SPACES = List.of("tangent", "world");

    public static String label(String space) {
        return "world".equals(space) ? "World" : "Tangent";
    }

    public static List<String> optionChoices(String optionId) {
        return OPTION.equals(optionId) ? SPACES : List.of();
    }

    /**
     * {@code tangentSpaceNormal} expressed in the node's chosen output space: itself for {@code tangent},
     * or run through {@link ShaderCompileContext#tangentToSpace(String, ShaderExpr)} for {@code world}.
     */
    public static ShaderExpr toChosenSpace(ShaderNode node, ShaderCompileContext ctx, ShaderExpr tangentSpaceNormal) {
        if (!"world".equals(choice(node))) return tangentSpaceNormal;
        return ctx.tangentToSpace("world", tangentSpaceNormal);
    }

    private static String choice(ShaderNode node) {
        INodeOption opt = node.getNodeOptionById(OPTION);
        Object raw = opt == null ? null : opt.tryGetValue(Object.class).result().orElse(null);
        return raw instanceof String s && SPACES.contains(s) ? s : "tangent";
    }
}
