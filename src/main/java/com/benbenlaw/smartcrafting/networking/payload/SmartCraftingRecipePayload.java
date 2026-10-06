package com.benbenlaw.smartcrafting.networking.payload;

import com.benbenlaw.smartcrafting.SmartCrafting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * smithingBases is parallel to recipeIds: the exact base stack (with enchantments etc.) that the
 * server would use for a smithing recipe, or empty for every other recipe.
 * last is true for the final batch of a refresh.
 * reset is true for the first batch of a refresh (replaces the client list), false for batches that add to it.
 * intermediates is also parallel to recipeIds: the items that recursive crafting makes along the way.
 */
public record SmartCraftingRecipePayload(List<Identifier> recipeIds, List<ItemStack> smithingBases, List<List<ItemStack>> intermediates, boolean reset, boolean last) implements CustomPacketPayload {

    public static final Type<SmartCraftingRecipePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SmartCrafting.MOD_ID, "smart_crafting_recipe"));

    @Override
    public Type<SmartCraftingRecipePayload> type() {
        return TYPE;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, SmartCraftingRecipePayload> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()),
            SmartCraftingRecipePayload::recipeIds,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC,
            SmartCraftingRecipePayload::smithingBases,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.apply(ByteBufCodecs.list()),
            SmartCraftingRecipePayload::intermediates,
            ByteBufCodecs.BOOL,
            SmartCraftingRecipePayload::reset,
            ByteBufCodecs.BOOL,
            SmartCraftingRecipePayload::last,
            SmartCraftingRecipePayload::new
    );
}
