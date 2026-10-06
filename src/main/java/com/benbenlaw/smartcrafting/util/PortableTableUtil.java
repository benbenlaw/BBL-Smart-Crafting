package com.benbenlaw.smartcrafting.util;

import com.benbenlaw.smartcrafting.compat.CuriosCompat;
import com.benbenlaw.smartcrafting.item.SmartCraftingItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class PortableTableUtil {

    public static boolean hasPortableSmartCraftingTable(Player player) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(SmartCraftingItems.PORTABLE_SMART_CRAFTING_TABLE.get())) {
                return true;
            }
        }

        return CuriosCompat.hasCurio(player, SmartCraftingItems.PORTABLE_SMART_CRAFTING_TABLE.get());
    }
}
