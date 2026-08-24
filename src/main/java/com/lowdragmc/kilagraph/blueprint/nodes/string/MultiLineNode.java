package com.lowdragmc.kilagraph.blueprint.nodes.string;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.util.KGTextConfigurators;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import net.minecraft.network.chat.Component;

/**
 * A string literal authored in a multi-line text area rather than the one-line field a STRING
 * constant gives you. The value is an ordinary string with {@code \n} between the lines, so
 * everything downstream — Split, Format, Send Message, an XML/JSON payload — takes it unchanged.
 *
 * <p>Declared imperatively because the point of the node is the editor: the {@code text} option is
 * a plain STRING whose configurator is swapped for a {@code TextArea}.</p>
 */
@NodeAttribute(name = "string_multiline", group = "string", graphTypes = BlueprintGraph.class)
public class MultiLineNode extends AnnotatedNode {
    private static final String OPTION = "text";

    @OutputPort public String out;

    @Override
    protected void onDefineExtraOptions(IOptionDefinitionContext context) {
        context.addOption(OPTION, TypeHandles.STRING)
                .withDefaultValue("")
                // No label: the text area wants the full node width, and "text" beside it says nothing
                // the node title doesn't already.
                .withDisplayName(Component.empty())
                .withConfigurable(KGTextConfigurators.multiLineText(""))
                .build();
    }

    @Override
    public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", ctx.getOption(OPTION, String.class, ""));
    }
}
