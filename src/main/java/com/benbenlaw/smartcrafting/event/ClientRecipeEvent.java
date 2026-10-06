package com.benbenlaw.smartcrafting.event;

import com.benbenlaw.smartcrafting.SmartCrafting;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = SmartCrafting.MOD_ID, value = Dist.CLIENT)
public class ClientRecipeEvent {

    @SubscribeEvent
    public static void onRecipeReceived(RecipesReceivedEvent event) {
        RecipeMap recipeMap = event.getRecipeMap();

        Map<Identifier, CraftingRecipe> craftingRecipes = new HashMap<>();
        for (RecipeHolder<CraftingRecipe> holder : recipeMap.byType(RecipeType.CRAFTING)) {
            craftingRecipes.put(holder.id().identifier(), holder.value());
        }
        VanillaRecipeCache.setCachedCraftingRecipes(craftingRecipes);

        Map<Identifier, StonecutterRecipe> stonecutterRecipes = new HashMap<>();
        for (RecipeHolder<StonecutterRecipe> holder : recipeMap.byType(RecipeType.STONECUTTING)) {
            stonecutterRecipes.put(holder.id().identifier(), holder.value());
        }
        VanillaRecipeCache.setCachedStonecutterRecipes(stonecutterRecipes);

        Map<Identifier, SmithingRecipe> smithingRecipes = new HashMap<>();
        for (RecipeHolder<SmithingRecipe> holder : recipeMap.byType(RecipeType.SMITHING)) {
            smithingRecipes.put(holder.id().identifier(), holder.value());
        }
        VanillaRecipeCache.setCachedSmithingRecipes(smithingRecipes);
    }
}
