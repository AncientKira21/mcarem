package com.ancientkira.mca.network.s2c;

import com.ancientkira.mca.ClientProxy;
import com.ancientkira.mca.MCA;
import com.ancientkira.mca.network.HandleablePayload;
import com.ancientkira.mca.network.c2s.ReportBuildingMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public record BuildingPolymorphMessage(List<String> matchingTypes, BlockPos scanPos,
                                       ReportBuildingMessage.Action action, int targetBuildingId) implements HandleablePayload {
    public static final CustomPacketPayload.Type<BuildingPolymorphMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("building_polymorph"));

    private static final StreamCodec<FriendlyByteBuf, BlockPos> BLOCK_POS_CODEC = StreamCodec.of(
            (buf, pos) -> buf.writeBlockPos(pos), buf -> buf.readBlockPos()
    );

    public static final StreamCodec<FriendlyByteBuf, BuildingPolymorphMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), BuildingPolymorphMessage::matchingTypes,
            BLOCK_POS_CODEC, BuildingPolymorphMessage::scanPos,
            ByteBufCodecs.idMapper(i -> ReportBuildingMessage.Action.values()[i], ReportBuildingMessage.Action::ordinal),
            BuildingPolymorphMessage::action,
            ByteBufCodecs.VAR_INT, BuildingPolymorphMessage::targetBuildingId,
            BuildingPolymorphMessage::new
    );

    public BuildingPolymorphMessage(List<String> matchingTypes, BlockPos scanPos, boolean isRoom) {
        this(matchingTypes, scanPos, isRoom ? ReportBuildingMessage.Action.ADD_ROOM : ReportBuildingMessage.Action.ADD, -1);
    }

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleBuildingPolymorph(this);
    }

    @Override
    public CustomPacketPayload.Type<BuildingPolymorphMessage> type() {
        return TYPE;
    }
}
