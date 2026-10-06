package com.benbenlaw.smartcrafting.event;

import com.benbenlaw.smartcrafting.SmartCrafting;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

@EventBusSubscriber(modid = SmartCrafting.MOD_ID)
public class RecipeEvent {

    @SubscribeEvent
    public static void onDataPackSync(OnDatapackSyncEvent event) {
        event.sendRecipes(RecipeType.CRAFTING);
        event.sendRecipes(RecipeType.STONECUTTING);
        event.sendRecipes(RecipeType.SMITHING);
    }
}
