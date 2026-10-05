package com.ancientkira.mca.network.c2s;

import com.ancientkira.mca.MCA;
import com.ancientkira.mca.network.HandleablePayload;
import com.ancientkira.mca.server.world.data.VillageManager;
import com.ancientkira.mca.server.world.data.Building;
import com.ancientkira.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public record ConfirmBuildingPolymorphMessage(BlockPos source, ReportBuildingMessage.Action action,
                                              int targetBuildingId, String chosenType) implements HandleablePayload {
    public static final CustomPacketPayload.Type<ConfirmBuildingPolymorphMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("confirm_building_polymorph"));

    private static final StreamCodec<FriendlyByteBuf, BlockPos> BLOCK_POS_CODEC = StreamCodec.of(
            (buf, pos) -> buf.writeBlockPos(pos), buf -> buf.readBlockPos()
    );

    public static final StreamCodec<FriendlyByteBuf, ConfirmBuildingPolymorphMessage> STREAM_CODEC = StreamCodec.composite(
            BLOCK_POS_CODEC, ConfirmBuildingPolymorphMessage::source,
            ByteBufCodecs.idMapper(i -> ReportBuildingMessage.Action.values()[i], ReportBuildingMessage.Action::ordinal),
            ConfirmBuildingPolymorphMessage::action,
            ByteBufCodecs.VAR_INT, ConfirmBuildingPolymorphMessage::targetBuildingId,
            ByteBufCodecs.STRING_UTF8, ConfirmBuildingPolymorphMessage::chosenType,
            ConfirmBuildingPolymorphMessage::new
    );

    public ConfirmBuildingPolymorphMessage(BlockPos source, boolean strictScan, String chosenType) {
        this(source, strictScan ? ReportBuildingMessage.Action.ADD_ROOM : ReportBuildingMessage.Action.ADD, -1, chosenType);
    }

    @Override
    public void handleServer(ServerPlayer player) {
        VillageManager villages = VillageManager.get((ServerLevel) player.level());
        try {
            Building.validationResult result = switch (action) {
                case ADD, ADD_BUILDING -> villages.processBuilding(source, true, false, chosenType);
                case ADD_ROOM -> villages.commitRoomAddition(villages.analyzeRoomAddition(source, targetBuildingId), chosenType,
                        Village.RoomScanMode.ADD_ROOM, targetBuildingId);
                case UPDATE_ROOM -> villages.commitRoomUpdate(villages.analyzeRoomUpdate(source, targetBuildingId),
                        chosenType, targetBuildingId);
                case ADD_FLOOR -> villages.commitRoomAddition(
                        villages.analyzeAttachedRoom(source, Village.RoomScanMode.ADD_FLOOR, targetBuildingId),
                        chosenType, Village.RoomScanMode.ADD_FLOOR, targetBuildingId);
                case ADD_BASEMENT -> villages.commitRoomAddition(
                        villages.analyzeAttachedRoom(source, Village.RoomScanMode.ADD_BASEMENT, targetBuildingId),
                        chosenType, Village.RoomScanMode.ADD_BASEMENT, targetBuildingId);
                default -> Building.validationResult.INVALID_TYPE;
            };
            String successKey = switch (action) {
                case ADD, ADD_BUILDING -> "blueprint.buildingAdded";
                case ADD_ROOM -> "blueprint.roomAdded";
                case UPDATE_ROOM -> "blueprint.roomUpdated";
                case ADD_FLOOR -> "blueprint.floorAdded";
                case ADD_BASEMENT -> "blueprint.basementAdded";
                default -> null;
            };
            String key = result == Building.validationResult.SUCCESS && successKey != null
                    ? successKey : "blueprint.scan." + result.name().toLowerCase(Locale.ENGLISH);
            player.sendSystemMessage(Component.translatable(key), true);
        } finally {
            GetVillageRequest.sendResponse(player);
        }
    }

    @Override
    public CustomPacketPayload.Type<ConfirmBuildingPolymorphMessage> type() {
        return TYPE;
    }
}
