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

public record InteractionCloseRequest(UUID villagerUUID) implements HandleablePayload {
    public static final CustomPacketPayload.Type<InteractionCloseRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("interaction_close_request"));
    public static final StreamCodec<FriendlyByteBuf, InteractionCloseRequest> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, InteractionCloseRequest::villagerUUID,
            InteractionCloseRequest::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        Entity v = player.level().getEntity(villagerUUID);
        if (v instanceof VillagerEntityMCA villager) {
            villager.getInteractions().stopInteracting();
        }
    }

    @Override
    public CustomPacketPayload.Type<InteractionCloseRequest> type() {
        return TYPE;
    }
}
