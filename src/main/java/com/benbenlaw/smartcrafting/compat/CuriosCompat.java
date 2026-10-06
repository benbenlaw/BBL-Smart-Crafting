package com.benbenlaw.smartcrafting.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;

public class CuriosCompat {

    public static boolean hasCurio(Player player, Item item) {
        if (!ModList.get().isLoaded("curios")) return false;
        return Impl.hasCurio(player, item);
    }

    private static class Impl {
        static boolean hasCurio(Player player, Item item) {
            return CuriosApi.getCuriosInventory(player)
                    .map(inventory -> inventory.findFirstCurio(item).isPresent())
                    .orElse(false);
        }
    }
}
