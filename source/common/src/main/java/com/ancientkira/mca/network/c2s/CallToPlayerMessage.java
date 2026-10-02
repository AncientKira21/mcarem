package com.ancientkira.mca.network.c2s;

import com.ancientkira.mca.MCA;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.network.HandleablePayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public record CallToPlayerMessage(UUID uuid) implements HandleablePayload {
    public static final CustomPacketPayload.Type<CallToPlayerMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("call_to_player"));
    public static final StreamCodec<FriendlyByteBuf, CallToPlayerMessage> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CallToPlayerMessage::uuid,
            CallToPlayerMessage::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        Entity e = player.level().getEntity(uuid);
        if (e instanceof VillagerEntityMCA v) {
            if (v.isSleeping()) {
                v.stopSleeping();
            }
            v.stopRiding();
            v.setPos(player.getX(), player.getY(), player.getZ());
        }
    }

    @Override
    public CustomPacketPayload.Type<CallToPlayerMessage> type() {
        return TYPE;
    }
}
