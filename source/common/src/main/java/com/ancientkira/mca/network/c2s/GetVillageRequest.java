package com.ancientkira.mca.network.c2s;

import com.ancientkira.mca.MCA;
import com.ancientkira.mca.network.HandleablePayload;
import com.ancientkira.mca.network.Network;
import com.ancientkira.mca.network.s2c.GetVillageFailedResponse;
import com.ancientkira.mca.network.s2c.GetVillageResponse;
import com.ancientkira.mca.resources.Rank;
import com.ancientkira.mca.resources.Tasks;
import com.ancientkira.mca.server.world.data.GraveyardManager;
import com.ancientkira.mca.server.world.data.Village;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.Set;

public record GetVillageRequest() implements HandleablePayload {
    public static final CustomPacketPayload.Type<GetVillageRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("get_village_request"));
    public static final StreamCodec<FriendlyByteBuf, GetVillageRequest> STREAM_CODEC = StreamCodec.unit(new GetVillageRequest());

    @Override
    public void handleServer(ServerPlayer player) {
        Optional<Village> village = Village.findNearest(player);
        if (village.isPresent()) {
            GraveyardManager.get(player.level()).reportToVillageManager(player);
            village.get().updateMaxPopulation();
            int reputation = village.get().getReputation(player);
            boolean isVillage = village.get().isVillage();
            Rank rank = Tasks.getRank(village.get(), player);
            Set<String> ids = Tasks.getCompletedIds(village.get(), player);
            Network.sendToPlayer(new GetVillageResponse(village.get(), rank, reputation, isVillage, ids), player);
        } else {
            Network.sendToPlayer(new GetVillageFailedResponse(), player);
        }
    }

    @Override
    public Type<GetVillageRequest> type() {
        return TYPE;
    }
}
