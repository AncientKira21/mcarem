package com.ancientkira.mca.network.c2s;

import com.ancientkira.mca.MCA;
import com.ancientkira.mca.entity.ai.relationship.Gender;
import com.ancientkira.mca.network.HandleablePayload;
import com.ancientkira.mca.network.Network;
import com.ancientkira.mca.network.s2c.BabyNameResponse;
import com.ancientkira.mca.resources.Names;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record BabyNameRequest(Gender gender) implements HandleablePayload {
    public static final CustomPacketPayload.Type<BabyNameRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("baby_name_request"));
    public static final StreamCodec<FriendlyByteBuf, BabyNameRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, r -> r.gender().ordinal(),
            i -> new BabyNameRequest(Gender.byId(i))
    );

    @Override
    public void handleServer(ServerPlayer player) {
        String name = Names.pickCitizenName(gender);
        Network.sendToPlayer(new BabyNameResponse(name), player);
    }

    @Override
    public CustomPacketPayload.Type<BabyNameRequest> type() {
        return TYPE;
    }
}
