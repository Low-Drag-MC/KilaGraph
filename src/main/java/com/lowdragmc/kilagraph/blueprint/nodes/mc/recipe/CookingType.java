package com.lowdragmc.kilagraph.blueprint.nodes.mc.recipe;

import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;

/**
 * Which of the four cooking blocks {@code mc_smelting_result} asks about.
 *
 * <h2>One node with an option, not four nodes</h2>
 * All four take a single item and give back a result, an experience value and a time — the same three
 * questions with a different table behind them. Splitting them into separate nodes would put the same
 * documentation in four places and make "try the blast furnace, else the furnace" a rewiring job rather
 * than a flipped option.
 */
public enum CookingType {
    SMELTING, BLASTING, SMOKING, CAMPFIRE;

    /** The names offered by the option dropdown, furnace first because it is the general case. */
    public static final List<String> CHOICES = List.of("SMELTING", "BLASTING", "SMOKING", "CAMPFIRE");

    /**
     * The game's recipe table for this block.
     *
     * <p>The cast is the price of the four constants having four unrelated generic parameters
     * ({@code RecipeType<SmeltingRecipe>}, {@code RecipeType<BlastingRecipe>} and so on) despite every one
     * of them being a {@link AbstractCookingRecipe} over the same input type. It is sound: each of the four
     * classes extends {@code AbstractCookingRecipe}, and the recipe manager only ever reads out of the
     * table, so no wrongly-typed recipe can be put in through this reference.</p>
     */
    @SuppressWarnings("unchecked")
    RecipeType<AbstractCookingRecipe> recipeType() {
        return (RecipeType<AbstractCookingRecipe>) (RecipeType<?>) switch (this) {
            case BLASTING -> RecipeType.BLASTING;
            case SMOKING -> RecipeType.SMOKING;
            case CAMPFIRE -> RecipeType.CAMPFIRE_COOKING;
            default -> RecipeType.SMELTING;
        };
    }
}
