package com.benbenlaw.smartcrafting.event;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = "smartcrafting")
public class RecipeEvent {

    @SubscribeEvent
    public static void onDataPackSync(OnDatapackSyncEvent event) {
        event.sendRecipes(RecipeType.CRAFTING);
        event.sendRecipes(RecipeType.STONECUTTING);
    }

    @SubscribeEvent
    public static void onRecipeReceived(RecipesReceivedEvent event) {
        RecipeMap recipeMap = event.getRecipeMap();

        //Crafting Recipes
        Collection<RecipeHolder<CraftingRecipe>> craftingRecipes = recipeMap.byType(RecipeType.CRAFTING);
        Map<Identifier, CraftingRecipe> craftingRecipeMap = new HashMap<>();

        for (RecipeHolder<CraftingRecipe> holder : craftingRecipes) {
            craftingRecipeMap.put(holder.id().identifier(), holder.value());
        }
        VanillaRecipeCache.setCachedCraftingRecipes(craftingRecipeMap);

        //Stonecutting Recipes
        Collection<RecipeHolder<StonecutterRecipe>> stonecuttingRecipes = recipeMap.byType(RecipeType.STONECUTTING);

        Map<Identifier, StonecutterRecipe> stonecuttingRecipeMap = new HashMap<>();
        for (RecipeHolder<StonecutterRecipe> holder : stonecuttingRecipes) {
            stonecuttingRecipeMap.put(holder.id().identifier(), holder.value());
        }
        VanillaRecipeCache.setCachedStonecutterRecipes(stonecuttingRecipeMap);

     }
}
