package com.benbenlaw.smartcrafting.networking.packets;

import com.benbenlaw.smartcrafting.SmartCrafting;
import com.benbenlaw.smartcrafting.util.PlayerData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

public record SyncRecursiveClient(boolean recursive) implements CustomPacketPayload {

    public static final Type<SyncRecursiveClient> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SmartCrafting.MOD_ID, "sync_recursive_client"));

    public static final IPayloadHandler<SyncRecursiveClient> HANDLER = (pkt, ctx) -> PlayerData.setRecursive(ctx.player(), pkt.recursive);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncRecursiveClient> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncRecursiveClient::recursive,
            SyncRecursiveClient::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
