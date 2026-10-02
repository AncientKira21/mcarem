package com.ancientkira.mca.network.c2s;

import com.ancientkira.mca.MCA;
import com.ancientkira.mca.entity.VillagerLike;
import com.ancientkira.mca.network.HandleablePayload;
import com.ancientkira.mca.network.Network;
import com.ancientkira.mca.network.s2c.GetFamilyResponse;
import com.ancientkira.mca.server.world.data.PlayerSaveData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.stream.Stream;

public record GetFamilyRequest() implements HandleablePayload {
    public static final CustomPacketPayload.Type<GetFamilyRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("get_family_request"));
    public static final StreamCodec<FriendlyByteBuf, GetFamilyRequest> STREAM_CODEC = StreamCodec.unit(new GetFamilyRequest());

    @Override
    public void handleServer(ServerPlayer player) {
        CompoundTag familyData = new CompoundTag();
        PlayerSaveData playerData = PlayerSaveData.get(player);
        Stream.concat(
                        playerData.getFamilyEntry().getAllRelatives(4),
                        playerData.getPartnerUUID().stream()
                ).distinct()
                .map(uuid -> player.level().getEntity(uuid))
                .filter(e -> e instanceof VillagerLike<?>)
                .limit(100)
                .forEach(e -> {
                    TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.registryAccess());
                    e.saveWithoutId(output);
                    CompoundTag nbt = output.buildResult();
                    nbt.remove("Brain");
                    nbt.remove("Memories");
                    nbt.remove("Inventory");
                    familyData.put(e.getUUID().toString(), nbt);
                });
        Network.sendToPlayer(new GetFamilyResponse(familyData), player);
    }

    @Override
    public Type<GetFamilyRequest> type() {
        return TYPE;
    }
}
