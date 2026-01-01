package com.benbenlaw.smartcrafting.util;

import com.benbenlaw.smartcrafting.SmartCrafting;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;


public class KeyBinds {

    public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(SmartCrafting.identifier("smartcrafting"));

    public static final String OPEN_SMART_CRAFTING_MENU = "key.smartcrafting.open_smart_crafting_menu";

    public static final KeyMapping OPEN_SMART_CRAFTING_MENU_HOTKEY =
            new KeyMapping(OPEN_SMART_CRAFTING_MENU, KeyConflictContext.UNIVERSAL, KeyModifier.NONE, InputConstants.Type.KEYSYM, InputConstants.KEY_C, KEY_CATEGORY);

}

