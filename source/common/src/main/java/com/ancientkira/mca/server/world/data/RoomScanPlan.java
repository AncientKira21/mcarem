package com.ancientkira.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.Optional;

public record RoomScanPlan(
        Optional<Building> building,
        Village.RoomScanMode mode,
        int targetBuildingId,
        int prospectiveFloorNumber,
        BlockPos interactionSource,
        BlockPos scanSeed
) {
    private static final int NO_TARGET_BUILDING = -1;
    private static final int NO_PROSPECTIVE_FLOOR = Integer.MIN_VALUE;

    public RoomScanPlan {
        building = building == null ? Optional.empty() : building;
        interactionSource = interactionSource.immutable();
        scanSeed = scanSeed.immutable();
    }

    public static RoomScanPlan addBuilding(BlockPos source) {
        return new RoomScanPlan(Optional.empty(), Village.RoomScanMode.ADD_BUILDING, NO_TARGET_BUILDING,
                NO_PROSPECTIVE_FLOOR, source, source);
    }

    static RoomScanPlan updateRoom(Building room, BlockPos source) {
        return new RoomScanPlan(Optional.of(room), Village.RoomScanMode.UPDATE_ROOM, room.getId(),
                room.getFloorNumber(), source, source);
    }

    static RoomScanPlan addRoom(Building parent, BlockPos source) {
        return new RoomScanPlan(Optional.ofNullable(parent), Village.RoomScanMode.ADD_ROOM,
                parent == null ? NO_TARGET_BUILDING : parent.getId(),
                parent == null ? 0 : parent.getFloorNumber(), source, source);
    }

    static RoomScanPlan attachment(int targetBuildingId, int floorNumber, BlockPos source) {
        Village.RoomScanMode mode = floorNumber < 0
                ? Village.RoomScanMode.ADD_BASEMENT
                : Village.RoomScanMode.ADD_FLOOR;
        return new RoomScanPlan(Optional.empty(), mode, targetBuildingId, floorNumber, source, source);
    }

    public Optional<Building> functionalRoom() {
        return mode == Village.RoomScanMode.UPDATE_ROOM ? building : Optional.empty();
    }
}
