package com.lowdragmc.kilagraph.graph.type;

import java.util.UUID;

/**
 * Value carried by a {@link KGTypeHandles#NODE_REF} wire: an opaque reference to another node in
 * the same graph, identified by its UID.
 *
 * <p>Used by the {@code Cache} / {@code CacheClear} pair. {@code Cache} emits a {@code NodeRef} to
 * itself on its {@code ref} output; {@code CacheClear} consumes it (in practice it reads the wire
 * topology rather than the value, so the {@code NodeRef} value mostly exists so the wire is
 * type-compatible). Backed by the {@link KGTypeHandles#NODE_REF} handle, which — like LIST/MAP —
 * has no {@code AccessorRegistries} entry, so ports of this type take the {@code withoutConfigurator}
 * path (no embedded constant; value comes purely from the wire).</p>
 */
public record NodeRef(UUID nodeUid) {
}
