package com.benbenlaw.smartcrafting.compat;

import dev.ftb.mods.ftbchunks.api.Protection;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.fml.ModList;

public class FTBChunksCompat {

    public static boolean canAccess(ServerPlayer player, BlockPos pos) {
        if (!ModList.get().isLoaded("ftbchunks")) return true;
        return Impl.canAccess(player, pos);
    }

    private static class Impl {
        static boolean canAccess(ServerPlayer player, BlockPos pos) {
            return !ClaimedChunkManagerImpl.getInstance().shouldPreventInteraction(player, InteractionHand.MAIN_HAND, pos, Protection.EDIT_AND_INTERACT_BLOCK, null);
        }
    }
}
