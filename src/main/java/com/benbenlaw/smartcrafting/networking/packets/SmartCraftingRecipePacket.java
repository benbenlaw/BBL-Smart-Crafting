package com.benbenlaw.smartcrafting.networking.packets;

import com.benbenlaw.smartcrafting.event.VanillaRecipeCache;
import com.benbenlaw.smartcrafting.networking.payload.SmartCraftingRecipePayload;
import com.benbenlaw.smartcrafting.screen.SmartCraftingMenu;
import com.benbenlaw.smartcrafting.screen.SmartCraftingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public record SmartCraftingRecipePacket() {

    public static final SmartCraftingRecipePacket INSTANCE = new SmartCraftingRecipePacket();

    public static SmartCraftingRecipePacket get() {
        return INSTANCE;
    }


    // Resolve off-thread (not GUI)
    public void handle(final SmartCraftingRecipePayload payload, IPayloadContext context) {
        List<RecipeHolder<? extends Recipe<?>>> resolvedRecipes = new ArrayList<>();

        java.util.Map<Identifier, net.minecraft.world.item.ItemStack> bases = new java.util.HashMap<>();
        for (int i = 0; i < payload.recipeIds().size() && i < payload.smithingBases().size(); i++) {
            if (!payload.smithingBases().get(i).isEmpty()) {
                bases.put(payload.recipeIds().get(i), payload.smithingBases().get(i));
            }
        }
        java.util.Map<Identifier, java.util.List<net.minecraft.world.item.ItemStack>> intermediates = new java.util.HashMap<>();
        for (int i = 0; i < payload.recipeIds().size() && i < payload.intermediates().size(); i++) {
            if (!payload.intermediates().get(i).isEmpty()) {
                intermediates.put(payload.recipeIds().get(i), payload.intermediates().get(i));
            }
        }

        // The live maps only ever grow during a refresh, they are swapped for the staged ones once it finishes
        if (payload.reset()) {
            VanillaRecipeCache.stagingSmithingBases = new java.util.HashMap<>();
            VanillaRecipeCache.stagingIntermediates = new java.util.HashMap<>();
        }
        VanillaRecipeCache.stagingSmithingBases.putAll(bases);
        VanillaRecipeCache.stagingIntermediates.putAll(intermediates);
        VanillaRecipeCache.smithingBases.putAll(bases);
        VanillaRecipeCache.intermediates.putAll(intermediates);
        if (payload.last()) {
            VanillaRecipeCache.smithingBases = VanillaRecipeCache.stagingSmithingBases;
            VanillaRecipeCache.intermediates = VanillaRecipeCache.stagingIntermediates;
        }

        for (Identifier id : payload.recipeIds()) {
            if (VanillaRecipeCache.cachedCraftingRecipes.containsKey(id)) {
                CraftingRecipe recipe = VanillaRecipeCache.cachedCraftingRecipes.get(id);
                ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
                resolvedRecipes.add(new RecipeHolder<>(key, recipe));
            } else if (VanillaRecipeCache.cachedStonecutterRecipes.containsKey(id)) {
                StonecutterRecipe recipe = VanillaRecipeCache.cachedStonecutterRecipes.get(id);
                ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
                resolvedRecipes.add(new RecipeHolder<>(key, recipe));
            } else if (VanillaRecipeCache.cachedSmithingRecipes.containsKey(id)) {
                SmithingRecipe recipe = VanillaRecipeCache.cachedSmithingRecipes.get(id);
                ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
                resolvedRecipes.add(new RecipeHolder<>(key, recipe));
            }
        }

        // Apply to screen if open, otherwise store in pending
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null &&
                    mc.player.containerMenu instanceof SmartCraftingMenu menu &&
                    mc.screen instanceof SmartCraftingScreen screen) {
                screen.receiveRecipes(resolvedRecipes, payload.reset(), payload.last());
            } else if (payload.reset() || SmartCraftingScreen.pendingRecipes == null) {
                SmartCraftingScreen.pendingRecipes = new ArrayList<>(resolvedRecipes);
            } else {
                SmartCraftingScreen.pendingRecipes.addAll(resolvedRecipes);
            }
        });
    }


}
