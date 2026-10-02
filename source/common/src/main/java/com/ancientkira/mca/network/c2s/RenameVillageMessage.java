package com.ancientkira.mca.network.c2s;

import com.ancientkira.mca.MCA;
import com.ancientkira.mca.network.HandleablePayload;
import com.ancientkira.mca.server.world.data.VillageManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record RenameVillageMessage(int id, String name) implements HandleablePayload {
    public static final CustomPacketPayload.Type<RenameVillageMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("rename_village"));
    public static final StreamCodec<FriendlyByteBuf, RenameVillageMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, RenameVillageMessage::id,
            ByteBufCodecs.STRING_UTF8, RenameVillageMessage::name,
            RenameVillageMessage::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        VillageManager.get(player.level()).getOrEmpty(id).ifPresent(v -> v.setName(name));
    }

    @Override
    public CustomPacketPayload.Type<RenameVillageMessage> type() {
        return TYPE;
    }
}
