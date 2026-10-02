package com.ancientkira.mca.network.c2s;

import com.ancientkira.mca.MCA;
import com.ancientkira.mca.network.HandleablePayload;
import com.ancientkira.mca.server.world.data.VillageManager;
import com.ancientkira.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public record ConfirmBuildingPolymorphMessage(BlockPos source, boolean strictScan, String chosenType) implements HandleablePayload {
    public static final CustomPacketPayload.Type<ConfirmBuildingPolymorphMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("confirm_building_polymorph"));

    private static final StreamCodec<FriendlyByteBuf, BlockPos> BLOCK_POS_CODEC = StreamCodec.of(
            (buf, pos) -> buf.writeBlockPos(pos), buf -> buf.readBlockPos()
    );

    public static final StreamCodec<FriendlyByteBuf, ConfirmBuildingPolymorphMessage> STREAM_CODEC = StreamCodec.composite(
            BLOCK_POS_CODEC, ConfirmBuildingPolymorphMessage::source,
            ByteBufCodecs.BOOL, ConfirmBuildingPolymorphMessage::strictScan,
            ByteBufCodecs.STRING_UTF8, ConfirmBuildingPolymorphMessage::chosenType,
            ConfirmBuildingPolymorphMessage::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        VillageManager villages = VillageManager.get((ServerLevel) player.level());
        Building.validationResult result = villages.processBuilding(source, true, strictScan, chosenType);
        player.sendSystemMessage(Component.translatable("blueprint.scan." + result.name().toLowerCase(Locale.ENGLISH)), true);
    }

    @Override
    public CustomPacketPayload.Type<ConfirmBuildingPolymorphMessage> type() {
        return TYPE;
    }
}
