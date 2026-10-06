package com.benbenlaw.smartcrafting.networking.packets;

import com.benbenlaw.smartcrafting.SmartCrafting;
import com.benbenlaw.smartcrafting.util.PlayerData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

public record SyncSortTypeClient(String sortType) implements CustomPacketPayload {

    public static final Type<SyncSortTypeClient> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SmartCrafting.MOD_ID,"sync_sort_type_clieny"));

    public static final IPayloadHandler<SyncSortTypeClient> HANDLER = (pkt, ctx) -> {
        Player player = ctx.player();
        PlayerData.setSortType(player, pkt.sortType);
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncSortTypeClient> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SyncSortTypeClient::sortType,
            SyncSortTypeClient::new
    );


    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
