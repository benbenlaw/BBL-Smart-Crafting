package com.benbenlaw.smartcrafting.event;

import com.benbenlaw.smartcrafting.SmartCrafting;
import com.benbenlaw.smartcrafting.networking.SendOpenSmartCraftingMenuToServer;
import com.benbenlaw.smartcrafting.util.KeyBinds;
import com.benbenlaw.smartcrafting.util.PortableTableUtil;
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
        tryOpenMenu();
    }

    @SubscribeEvent
    public static void onOpenSmartCraftingMenuButtonPressedMouse(InputEvent.MouseButton.Post event) {
        tryOpenMenu();
    }

    private static void tryOpenMenu() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        if (KeyBinds.OPEN_SMART_CRAFTING_MENU_HOTKEY.consumeClick()) {
            if (PortableTableUtil.hasPortableSmartCraftingTable(player)) {
                ClientPacketDistributor.sendToServer(new SendOpenSmartCraftingMenuToServer(player.blockPosition()));
            } else {
                player.sendSystemMessage(Component.translatable("message.smartcrafting.no_portable_table"));
            }
        }
    }
}
