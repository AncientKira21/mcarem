package com.ancientkira.mca.network.c2s;

import com.ancientkira.mca.MCA;
import com.ancientkira.mca.network.HandleablePayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

public record FreePrisonerMessage(int villageId, UUID inmate) implements HandleablePayload {
    public static final Type<FreePrisonerMessage> TYPE = new Type<>(MCA.locate("free_prisoner"));
    public static final StreamCodec<FriendlyByteBuf, FreePrisonerMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, FreePrisonerMessage::villageId, UUIDUtil.STREAM_CODEC, FreePrisonerMessage::inmate, FreePrisonerMessage::new);
    @Override public Type<FreePrisonerMessage> type() { return TYPE; }
    @Override public void handleServer(ServerPlayer player) {
        var village = com.ancientkira.mca.server.world.data.VillageManager.get(player.level()).getOrEmpty(villageId).orElse(null);
        if (village == null || !village.isWithinBorder(player.blockPosition(), 128)
                || !(com.ancientkira.mca.util.McaPermissions.cheats(player)
                || com.ancientkira.mca.resources.Tasks.getRank(village, player).isAtLeast(com.ancientkira.mca.resources.Rank.MAYOR))) return;
        if (!com.ancientkira.mca.server.GolemPolice.free(player.level(), village, inmate)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("gui.blueprint.prison.no_exit"), true);
        }
        new GetVillageRequest().handleServer(player);
    }
}
