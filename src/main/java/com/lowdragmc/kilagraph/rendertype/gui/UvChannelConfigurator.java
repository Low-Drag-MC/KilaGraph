package com.lowdragmc.kilagraph.rendertype.gui;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.UvChannel;
import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.accessors.EnumAccessor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.IFieldValueConfigurable;

import java.util.List;

/**
 * The editor configurator for a {@code UV} value ({@link UvChannel}): a UV0/UV1/UV2 dropdown shown on an
 * unconnected uv port. Registered via {@code TypeHandleHelpers.setCustomConfigurable(UV, …)}; client-only
 * and referenced lazily so the headless compiler never loads it (mirrors {@code Sampler2DConfigurator}).
 */
public final class UvChannelConfigurator {
    private UvChannelConfigurator() {}

    public static IConfigurable build(IFieldValueConfigurable vc) {
        return IConfigurable.create(group -> group.addConfigurator(EnumAccessor.create(
                "", List.of(UvChannel.values()),
                () -> vc.getValue() instanceof UvChannel u ? u : UvChannel.UV0,
                vc::setValue,
                UvChannel.UV0, true)));
    }
}
