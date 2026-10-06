package com.benbenlaw.smartcrafting.networking.packets;

import com.benbenlaw.smartcrafting.SmartCrafting;
import com.benbenlaw.smartcrafting.screen.SmartCraftingMenu;
import com.benbenlaw.smartcrafting.util.PlayerData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

public record SyncRecursive(boolean recursive) implements CustomPacketPayload {

    public static final Type<SyncRecursive> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SmartCrafting.MOD_ID, "sync_recursive"));

    public static final IPayloadHandler<SyncRecursive> HANDLER = (pkt, ctx) -> {
        ServerPlayer player = (ServerPlayer) ctx.player();
        PlayerData.setRecursive(player, pkt.recursive);
        if (player.containerMenu instanceof SmartCraftingMenu menu) {
            menu.updateValidRecipes();
        }
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncRecursive> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncRecursive::recursive,
            SyncRecursive::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
