package com.benbenlaw.smartcrafting.event;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
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

    public static Map<Identifier, java.util.List<net.minecraft.world.item.ItemStack>> intermediates = new HashMap<>();

    public static Map<Identifier, net.minecraft.world.item.ItemStack> stagingSmithingBases = new HashMap<>();
    public static Map<Identifier, java.util.List<net.minecraft.world.item.ItemStack>> stagingIntermediates = new HashMap<>();

    //Smithing
    public static Map<Identifier, net.minecraft.world.item.ItemStack> smithingBases = new HashMap<>();

    public static Map<Identifier, SmithingRecipe> cachedSmithingRecipes = new HashMap<>();

    public static void setCachedSmithingRecipes(Map<Identifier, SmithingRecipe> recipes) {
        cachedSmithingRecipes = recipes;
    }

}
