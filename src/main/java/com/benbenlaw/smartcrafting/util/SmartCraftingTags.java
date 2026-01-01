package com.benbenlaw.smartcrafting.util;

import com.benbenlaw.smartcrafting.SmartCrafting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class SmartCraftingTags {

    public static class Blocks {
        public static final TagKey<Block> WHITELISTED_STORAGE = TagKey.create(
                BuiltInRegistries.BLOCK.key(), SmartCrafting.identifier("whitelisted_storage")
        );
    }
}
