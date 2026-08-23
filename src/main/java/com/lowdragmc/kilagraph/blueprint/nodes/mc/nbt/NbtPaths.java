package com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.NbtPathArgument;
import org.jetbrains.annotations.Nullable;

/**
 * Shared path parsing for the two NBT path nodes.
 *
 * <h2>Why the game's parser and not a homemade one</h2>
 * {@link NbtPathArgument} is what {@code /data get} uses, so a path that works in a command works here and
 * a player can copy one straight across. It also brings the parts nobody would think to write themselves:
 * list wildcards ({@code Items[]}), predicate filters ({@code Items[{Slot:0b}]}) and root matching.
 *
 * <h2>A bad path is data, not a bug</h2>
 * The path is typed into a text field, so a syntax error in it reports on an {@code ok} port instead of
 * escaping as an exception — the same rule the regex nodes and the registry lookups follow.
 *
 * <p>Which is why the catch is wider than {@link CommandSyntaxException}, the one the signature promises.
 * A path cut off mid-token, {@code Inventory[}, makes the game's parser read past the end of the string
 * and throw {@link StringIndexOutOfBoundsException} instead — it checks for a closing bracket before
 * checking that there is any character left to read. Half-typed paths are precisely what a text field
 * produces, so narrowing this to the declared exception would leave the commonest mistake crashing the
 * graph.
 */
final class NbtPaths {

    private NbtPaths() {
    }

    /** The parsed path, or null when {@code path} is empty or not valid path syntax. */
    @Nullable
    static NbtPathArgument.NbtPath parse(@Nullable String path) {
        if (path == null || path.isEmpty()) return null;
        try {
            return NbtPathArgument.NbtPath.of(path);
        } catch (CommandSyntaxException | RuntimeException e) {
            return null;
        }
    }
}
