package com.benbenlaw.smartcrafting.event;

import com.benbenlaw.smartcrafting.SmartCrafting;
import com.benbenlaw.smartcrafting.item.SmartCraftingItems;
import com.benbenlaw.smartcrafting.networking.SendOpenSmartCraftingMenuToServer;
import com.benbenlaw.smartcrafting.util.KeyBinds;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

@EventBusSubscriber(modid = SmartCrafting.MOD_ID, value = Dist.CLIENT)
public class OpenCraftingMenuEvent {

    @SubscribeEvent
    public static void onOpenSmartCraftingMenuButtonPressed(InputEvent.Key event) {

        Player player = Minecraft.getInstance().player;

        if (KeyBinds.OPEN_SMART_CRAFTING_MENU_HOTKEY.consumeClick()) {
            assert player != null;
            if (hasPortableSmartCraftingTable(player)) {
                ClientPacketDistributor.sendToServer(new SendOpenSmartCraftingMenuToServer(player.blockPosition()));
            } else {
                player.displayClientMessage(Component.translatable("message.smartcrafting.no_portable_table"), false);
            }
        }
    }

    @SubscribeEvent
    public static void onOpenSmartCraftingMenuButtonPressedMouse(InputEvent.MouseButton.Post event) {

        Player player = Minecraft.getInstance().player;

        if (KeyBinds.OPEN_SMART_CRAFTING_MENU_HOTKEY.consumeClick()) {
            assert player != null;
            if (hasPortableSmartCraftingTable(player)) {
                ClientPacketDistributor.sendToServer(new SendOpenSmartCraftingMenuToServer(player.blockPosition()));
            } else {
                player.displayClientMessage(Component.translatable("message.smartcrafting.no_portable_table"), false);
            }
        }
    }

    private static boolean hasPortableSmartCraftingTable(Player player) {

        if (player.getInventory().getNonEquipmentItems().stream().anyMatch(itemStack -> itemStack.getItem() == SmartCraftingItems.PORTABLE_SMART_CRAFTING_TABLE.get())) {
            return true;
        }

        /// Re add curios support later when curios is updated
        /*
        if (ModList.get().isLoaded("curios")) {
            var curioApi = CuriosApi.getCuriosInventory(player);
            if (curioApi.isPresent()) {
                return curioApi.get().findFirstCurio(SmartCraftingItems.PORTABLE_SMART_CRAFTING_TABLE.get()).isPresent();
            }
        }
         */

        return false;
    }
}
