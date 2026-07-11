package com.lowdragmc.kilagraph.mixin.iris;

import com.lowdragmc.lowdraglib2.Platform;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gates the {@code kilagraph.iris.mixins.json} config so its Iris-targeting mixins are only applied when
 * Iris (or its Forge fork Oculus) is actually present. Without this guard, Mixin would error trying to
 * transform a class that isn't on the classpath. References no Iris type itself.
 *
 * <p>The config is {@code required:false} / {@code defaultRequire:0}: if a future Iris/MC version renames a
 * target class or injection point, the mixin silently doesn't land instead of hard-crashing startup. The
 * runtime detects that and degrades loudly — {@link #wasApplied} feeds the handshake in
 * {@code IrisCompat.warnOnceIfSeamsMissing}, and a reload that never reaches the injector trips the
 * behavioural {@code seamDead} latch (covers the require=0 injection-point-miss case, where the class
 * transform still "applies" with zero injections).</p>
 */
public class IrisMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Simple names of our mixins whose class transform completed ({@code postApply} fired). Static because
     *  the mixin service instantiates the plugin, not us. */
    private static final Set<String> APPLIED = ConcurrentHashMap.newKeySet();

    private boolean irisLoaded;

    @Override
    public void onLoad(String mixinPackage) {
        irisLoaded = detectIris();
        LOGGER.info("[KilaGraph][Iris] mixin plugin loaded, irisDetected={}", irisLoaded);
    }

    /** Robust detection: prefer the FML mod list, but fall back to a classpath probe in case the mod list
     *  isn't populated yet at mixin-bootstrap time. */
    @SuppressWarnings("removal") // LoadingModList.get() is the documented early-bootstrap accessor
    private static boolean detectIris() {
        try {
            boolean inModList = LoadingModList.get().getMods().stream()
                    .anyMatch(m -> "iris".equals(m.getModId()) || "oculus".equals(m.getModId()));
            if (inModList) return true;
        } catch (Throwable ignored) {
            // mod list not ready — fall through to the classpath probe
        }
        try {
            Class.forName("net.irisshaders.iris.pipeline.programs.ShaderCreator", false,
                    IrisMixinPlugin.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Kill-switch: read the property directly (this runs at mixin-bootstrap; must not load IrisCompat,
        // which reads the same property for the runtime paths).
        if (!irisLoaded || Boolean.getBoolean("kilagraph.iris.disabled")) return false;
        // MixinGlCommandEncoder only works around a dev-only Iris crash (#3137) — skip it outside dev.
        // NB: mixinClassName is OUR mixin (the 2nd arg); targetClassName is the Iris class being transformed.
        boolean apply = !mixinClassName.endsWith("MixinGlCommandEncoder") || Platform.isDevEnv();
        LOGGER.debug("[KilaGraph][Iris] shouldApplyMixin {} -> {} (apply={})", mixinClassName, targetClassName, apply);
        return apply;
    }

    /** Whether the mixin with this simple class name (e.g. {@code "MixinShaderCreator"}) applied to its
     *  target. NB: with {@code defaultRequire:0} an applied transform can still have matched zero injection
     *  points — that failure mode is caught behaviourally instead (see the class javadoc). */
    public static boolean wasApplied(String simpleName) {
        return APPLIED.contains(simpleName);
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        APPLIED.add(mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1));
    }
}
