package com.benbenlaw.smartcrafting.block;

import com.benbenlaw.smartcrafting.SmartCrafting;
import com.benbenlaw.smartcrafting.block.custom.SmartCraftingTableBlock;
import com.benbenlaw.smartcrafting.item.SmartCraftingItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class SmartCraftingBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SmartCrafting.MOD_ID);


    public static final DeferredBlock<Block> SMART_CRAFTING_TABLE = registerBlock("smart_crafting_table",
        () -> new SmartCraftingTableBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
                .noOcclusion().setId(createID("smart_crafting_table"))));


    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
        DeferredBlock<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
        SmartCraftingItems.ITEMS.registerItem(name, (properties) -> new BlockItem(block.get(), properties.useBlockDescriptionPrefix()));
    }

    public static ResourceKey<Block> createID(String name) {
        return ResourceKey.create(Registries.BLOCK, SmartCrafting.identifier(name));
    }


}