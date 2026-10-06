package com.benbenlaw.smartcrafting.networking.packets;

import com.benbenlaw.smartcrafting.SmartCrafting;
import com.benbenlaw.smartcrafting.util.PlayerData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

import java.util.List;

public record SyncFavoriteRecipesClient(List<String> favorites) implements CustomPacketPayload {

    public static final Type<SyncFavoriteRecipesClient> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SmartCrafting.MOD_ID, "favorite_recipes_sync_client"));

    public static final IPayloadHandler<SyncFavoriteRecipesClient> HANDLER = (pkt, ctx) -> PlayerData.setFavorites(ctx.player(), pkt.favorites());

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncFavoriteRecipesClient> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SyncFavoriteRecipesClient::favorites,
            SyncFavoriteRecipesClient::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
