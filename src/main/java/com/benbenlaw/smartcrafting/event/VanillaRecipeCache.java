package com.benbenlaw.smartcrafting.event;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class VanillaRecipeCache {

    //Crafting
    public static Map<Identifier, CraftingRecipe> cachedCraftingRecipes = new HashMap<>();

    public static void setCachedCraftingRecipes(Map<Identifier, CraftingRecipe> recipes) {
        cachedCraftingRecipes = recipes;
    }

    public static Collection<CraftingRecipe> getCachedCraftingRecipes() {
        return cachedCraftingRecipes.values();
    }

    //Stonecutter
    public static Map<Identifier, StonecutterRecipe> cachedStonecutterRecipes = new HashMap<>();

    public static void setCachedStonecutterRecipes(Map<Identifier, StonecutterRecipe> recipes) {
        cachedStonecutterRecipes = recipes;
    }

    public static Collection<StonecutterRecipe> getCachedStonecutterRecipes() {
        return cachedStonecutterRecipes.values();
    }

}
