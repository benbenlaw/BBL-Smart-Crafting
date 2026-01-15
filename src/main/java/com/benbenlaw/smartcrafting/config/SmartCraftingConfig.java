package com.benbenlaw.smartcrafting.config;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class SmartCraftingConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<Integer> storageRangeCheck;
    public static final ModConfigSpec.ConfigValue<Integer> stonecutterRangeCheck;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> validStorageBlocks;

    static {
        BUILDER.push("Smart Crafting Config");

        storageRangeCheck = BUILDER
                .comment("The range in blocks to check for storage blocks when crafting. Higher valves will take more time, Default is 5.")
                .defineInRange("storageRangeCheck", 5, 1, 64);

        stonecutterRangeCheck = BUILDER
                .comment("The range in blocks to check for stonecutter blocks when crafting. Higher valves will take more time, Default is 5.")
                .defineInRange("stonecutterRangeCheck", 5, 1, 64);


        validStorageBlocks = BUILDER
                .comment("List of allowed storage blocks that the smart crafting table can use.")
                .defineList("Allowed Storage Blocks",
                        List.of("minecraft:chest"),
                        obj -> obj instanceof String);

        BUILDER.pop();
        SPEC = BUILDER.build();

    }
}
