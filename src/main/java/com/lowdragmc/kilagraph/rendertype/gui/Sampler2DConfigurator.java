package com.lowdragmc.kilagraph.rendertype.gui;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.Sampler2DValue;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.SamplerAddress;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.SamplerFilter;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.SamplerMode;
import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.accessors.EnumAccessor;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorSelectorConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.SearchComponentConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.gui.texture.DynamicTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.IFieldValueConfigurable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The editor configurator for a {@code SAMPLER2D} value ({@link Sampler2DValue}). Registered via
 * {@code TypeHandleHelpers.setCustomConfigurable(SAMPLER2D, …)}; this class is client-only and loaded
 * lazily (only when the editor builds the configurator), so the compiler/headless path never touches it.
 *
 * <p>Layout: a <b>mode</b> selector (CUSTOM / ATLAS) with a mode-specific location picker — CUSTOM is a
 * free resource-location string, ATLAS is a searchable list of the loaded atlases (each rendered with a
 * {@link SpriteTexture} thumbnail). Then the GPU sampler params (filter / address / mipmap), and a live
 * preview thumbnail of the chosen texture.</p>
 */
public final class Sampler2DConfigurator {
    private Sampler2DConfigurator() {}

    public static IConfigurable build(IFieldValueConfigurable vc) {
        Supplier<Sampler2DValue> get = () -> {
            Object v = vc.getValue();
            return v instanceof Sampler2DValue s ? s : Sampler2DValue.defaultValue();
        };

        var mode = new ConfiguratorSelectorConfigurator<>(
                "kg.sampler.mode",
                () -> get.get().mode(),
                m -> {
                    var c = get.get();
                    vc.setValue(new Sampler2DValue(c.location(), m, c.filter(), c.address(), c.mipmap()));
                },
                SamplerMode.CUSTOM, true,
                List.of(SamplerMode.values()), SamplerMode::getSerializedName,
                (m, group) -> {
                    if (m == SamplerMode.ATLAS) {
                        group.addConfigurator(new SearchComponentConfigurator<>(
                                "kg.sampler.atlas",
                                () -> ResourceLocation.tryParse(get.get().location()),
                                id -> vc.setValue(get.get().withLocation(id == null ? "" : id.toString())),
                                firstAtlasId(), true,
                                Sampler2DConfigurator::searchAtlases,
                                id -> id == null ? "" : id.toString(),
                                UIElementProvider.iconText(
                                        id -> id == null ? IGuiTexture.EMPTY : SpriteTexture.of(id),
                                        id -> Component.literal(id == null ? "" : id.toString()))));
                    } else {
                        group.addConfigurator(new StringConfigurator(
                                "kg.sampler.texture",
                                () -> get.get().location(),
                                loc -> vc.setValue(get.get().withLocation(loc)),
                                "", true).setResourceLocation(true));
                    }
                });

        var filter = EnumAccessor.create(
                "kg.sampler.filter", List.of(SamplerFilter.values()),
                () -> get.get().filter(),
                f -> {
                    var c = get.get();
                    vc.setValue(new Sampler2DValue(c.location(), c.mode(), f, c.address(), c.mipmap()));
                },
                SamplerFilter.NEAREST, true);

        var address = EnumAccessor.create(
                "kg.sampler.address", List.of(SamplerAddress.values()),
                () -> get.get().address(),
                a -> {
                    var c = get.get();
                    vc.setValue(new Sampler2DValue(c.location(), c.mode(), c.filter(), a, c.mipmap()));
                },
                SamplerAddress.CLAMP, true);

        var mipmap = new BooleanConfigurator(
                "kg.sampler.mipmap",
                () -> get.get().mipmap(),
                mm -> {
                    var c = get.get();
                    vc.setValue(new Sampler2DValue(c.location(), c.mode(), c.filter(), c.address(), mm));
                },
                false, true);

        // Live preview of the chosen texture (re-evaluated each frame via DynamicTexture).
        var preview = new Configurator("kg.sampler.preview");
        preview.inlineContainer.addChild(new UIElement()
                .layout(l -> l.width(40).height(40))
                .style(s -> s.backgroundTexture(DynamicTexture.of(() -> {
                    ResourceLocation id = ResourceLocation.tryParse(get.get().location());
                    return id == null ? IGuiTexture.EMPTY : SpriteTexture.of(id);
                }))));

        return IConfigurable.create(group -> group.addConfigurators(mode, filter, address, mipmap, preview));
    }

    /**
     * The loaded atlases, matched against the search word. Yields each atlas's <b>texture location</b>
     * ({@link net.minecraft.client.renderer.texture.TextureAtlas#location()}, e.g.
     * {@code minecraft:textures/atlas/blocks.png}) — which is the id the atlas's GPU texture is
     * registered under, so it both binds (withTexture) and renders. The {@code forEach} key is the
     * atlas <em>config</em> id ({@code minecraft:items}), which is not a bindable/loadable texture.
     */
    private static void searchAtlases(String word, Consumer<ResourceLocation> found) {
        var mc = Minecraft.getInstance();
        if (mc == null) return;
        String lower = word.toLowerCase();
        List<ResourceLocation> ids = new ArrayList<>();
        // TODO(1.21-backport milestone 2): 1.21.1 has no Minecraft.getAtlasManager() to enumerate the loaded
        // atlases; offer the common atlas texture locations so the picker still works for the usual cases.
        ids.add(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
        ids.add(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_PARTICLES);
        for (ResourceLocation id : ids) {
            if (Thread.interrupted()) return;
            if (id.toString().toLowerCase().contains(lower)) found.accept(id);
        }
    }

    private static ResourceLocation firstAtlasId() {
        // A stable, commonly-present atlas as the search default (block textures).
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }
}
