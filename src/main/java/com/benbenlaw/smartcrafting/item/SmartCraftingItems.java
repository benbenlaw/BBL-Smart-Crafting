package com.benbenlaw.smartcrafting.item;

import com.benbenlaw.smartcrafting.SmartCrafting;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class SmartCraftingItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SmartCrafting.MOD_ID);

    public static final DeferredItem<Item> PORTABLE_SMART_CRAFTING_TABLE = ITEMS.register("portable_smart_crafting_table",
            () -> new PortableSmartCraftingTableItem(new Item.Properties().setId(createID("portable_smart_crafting_table"))));


    public static ResourceKey<Item> createID(String name) {
        return ResourceKey.create(Registries.ITEM, SmartCrafting.identifier(name));
    }
}
