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

        for (Identifier id : payload.recipeIds()) {
            if (VanillaRecipeCache.cachedCraftingRecipes.containsKey(id)) {
                CraftingRecipe recipe = VanillaRecipeCache.cachedCraftingRecipes.get(id);
                ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
                resolvedRecipes.add(new RecipeHolder<>(key, recipe));
            } else if (VanillaRecipeCache.cachedStonecutterRecipes.containsKey(id)) {
                StonecutterRecipe recipe = VanillaRecipeCache.cachedStonecutterRecipes.get(id);
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
                screen.setClientRecipes(resolvedRecipes);
            } else {
                SmartCraftingScreen.pendingRecipes = resolvedRecipes;
            }
        });
    }


}
