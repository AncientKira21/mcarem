package com.ancientkira.mca.network.c2s;

import com.ancientkira.mca.MCA;
import com.ancientkira.mca.network.HandleablePayload;
import com.ancientkira.mca.network.Network;
import com.ancientkira.mca.server.world.data.Building;
import com.ancientkira.mca.network.s2c.BuildingPolymorphMessage;
import com.ancientkira.mca.server.world.data.BuildingScanResult;
import com.ancientkira.mca.server.world.data.RoomScanPlan;
import com.ancientkira.mca.server.world.data.Village;
import com.ancientkira.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.OptionalInt;

public record ReportBuildingMessage(Action action, String data) implements HandleablePayload {
    public static final CustomPacketPayload.Type<ReportBuildingMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("report_building"));
    public static final StreamCodec<FriendlyByteBuf, ReportBuildingMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Action.values()[i], Action::ordinal), ReportBuildingMessage::action,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).map(o -> o.orElse(null), o -> o == null ? java.util.Optional.empty() : java.util.Optional.of(o)), ReportBuildingMessage::data,
            ReportBuildingMessage::new
    );

    private static final int BUILDING_LOOKUP_HORIZONTAL_MARGIN = 1;
    private static final int BUILDING_LOOKUP_VERTICAL_MARGIN = 2;

    public ReportBuildingMessage(Action action) {
        this(action, null);
    }

    @Override
    public void handleServer(ServerPlayer player) {
        VillageManager villages = VillageManager.get((ServerLevel) player.level());
        try {
            BlockPos source = player.blockPosition();
            int targetId = -1;
            if (action == Action.ADD_ROOM || action == Action.UPDATE_ROOM
                    || action == Action.ADD_FLOOR || action == Action.ADD_BASEMENT) {
                if (data == null && action != Action.ADD_ROOM) {
                    display(player, "blueprint.noBuilding");
                    return;
                }
                if (data != null) {
                    OptionalInt parsedTargetId = parseTargetBuildingId(data);
                    if (parsedTargetId.isEmpty() || parsedTargetId.getAsInt() < 0) {
                        display(player, "blueprint.noBuilding");
                        return;
                    }
                    targetId = parsedTargetId.getAsInt();
                }
            }
            switch (action) {
                case ADD, ADD_BUILDING -> handleBuildingOrFloorScan(villages, player, source, action);
                case ADD_ROOM -> handleRoomScan(villages, player,
                        villages.analyzeRoomAddition(source, targetId), action, targetId);
                case UPDATE_ROOM -> handleRoomScan(villages, player,
                        villages.analyzeRoomUpdate(source, targetId), action, targetId);
                case ADD_FLOOR, ADD_BASEMENT -> {
                    Village.RoomScanMode mode = action == Action.ADD_FLOOR
                            ? Village.RoomScanMode.ADD_FLOOR : Village.RoomScanMode.ADD_BASEMENT;
                    handleRoomScan(villages, player, villages.analyzeAttachedRoom(source, mode, targetId), action, targetId);
                }
                case DECLARE_WAR, CANCEL_WAR, RELEASE_VILLAGE, TOGGLE_FRIENDLY, START_INDEPENDENCE, MERGE_VILLAGE -> handleWar(villages, player, action == Action.CANCEL_WAR);
                case SET_POLICY -> {
                    String[] fields=data==null?new String[0]:data.split(":");
                    if(fields.length!=3)return;
                    Village village=villages.findNearestVillage(player).orElse(null);
                    if(village==null||village.getId()!=Integer.parseInt(fields[0]))return;
                    if(!canEditPolicy(player,village,fields[1]))return;
                    village.setPolicy(fields[1],Boolean.parseBoolean(fields[2]));
                }
                case AUTO_SCAN -> villages.findNearestVillage(player).ifPresent(Village::toggleAutoScan);
                case FULL_SCAN -> {
                    Village village = villages.findNearestVillage(player).orElse(null);
                    if (village == null) {
                        display(player, "blueprint.noBuilding");
                    } else {
                        Building.validationResult result = villages.fullScan(village);
                        displayResult(player, result, "blueprint.refreshed");
                    }
                }
                case FORCE_TYPE -> {
                    if (!villages.forceRoomType(source, data)) {
                        display(player, "blueprint.noBuilding");
                    }
                }
                case REMOVE -> displayEditResult(player, villages.removeBuilding(source), "blueprint.buildingRemoved");
                case REMOVE_ROOM -> displayEditResult(player, villages.removeRoom(source), "blueprint.roomRemoved");
                case SET_MAIN_ROOM -> {
                    VillageManager.BuildingEditResult result = villages.toggleMainRoom(source);
                    if (result == VillageManager.BuildingEditResult.SUCCESS) {
                        Village village = villages.findNearestVillage(source, Village.MERGE_MARGIN).orElse(null);
                        Building room = village == null ? null : village.getFunctionalRoomAt(source).orElse(null);
                        display(player, room != null && village.isMainRoomAutomatic(room)
                                ? "blueprint.mainRoomAutomatic" : "blueprint.mainRoomSet");
                    } else {
                        displayEditResult(player, result, null);
                    }
                }
                case SET_ROOM_INHERITANCE -> {
                    if (data != null && (data.equals("true") || data.equals("false"))) {
                        villages.setRoomInheritance(source, Boolean.parseBoolean(data));
                    }
                }
            }
        } finally {
            GetVillageRequest.sendResponse(player);
        }
    }

    private static boolean canEditPolicy(ServerPlayer player,Village village,String key){
        if(!java.util.Set.of("beds","graves","taxes").contains(key))return false;
        boolean privileged=player.isCreative() && player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        return privileged || key.equals("beds") && com.ancientkira.mca.resources.Tasks.getRank(village,player)
                .isAtLeast(com.ancientkira.mca.resources.Rank.MAYOR);
    }

    private void handleWar(VillageManager manager, ServerPlayer player, boolean cancel) {
        try {
            String[] parts = data == null ? new String[0] : data.split(":");
            if (parts.length != 3) { display(player, "mca.war.invalid"); return; }
            int sourceId = Integer.parseInt(parts[0]), targetId = Integer.parseInt(parts[1]), minutes = Integer.parseInt(parts[2]);
            Village origin = manager.findNearestVillage(player).orElse(null);
            if (origin == null || origin.getId() != sourceId) { display(player, "mca.war.invalid"); return; }
            var warfare = com.ancientkira.mca.server.VillageWarfare.get(player.level());
            if (action == Action.TOGGLE_FRIENDLY) {
                if (!com.ancientkira.mca.resources.Tasks.getRank(origin, player).isAtLeast(com.ancientkira.mca.resources.Rank.MAYOR)
                        || !warfare.toggleFriendly(origin, targetId)) display(player, "mca.war.invalid");
                return;
            }
            if(action==Action.START_INDEPENDENCE){
                if(com.ancientkira.mca.resources.Tasks.getRank(origin,player).isAtLeast(com.ancientkira.mca.resources.Rank.MAYOR))
                    warfare.startIndependence(origin,targetId,minutes);
                return;
            }
            if(action==Action.MERGE_VILLAGE){
                Village target=manager.getOrEmpty(targetId).orElse(null);
                boolean privileged=player.isCreative()&&player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
                if(target!=null && target!=origin && !warfare.isBusy(origin.getId())&&!warfare.isBusy(targetId)
                        && origin.getCenter().distSqr(target.getCenter())<=512*512
                        && (privileged || com.ancientkira.mca.resources.Tasks.getRank(origin,player).isAtLeast(com.ancientkira.mca.resources.Rank.MAYOR)
                            && com.ancientkira.mca.resources.Tasks.getRank(target,player).isAtLeast(com.ancientkira.mca.resources.Rank.MAYOR)))manager.merge(origin,target);
                return;
            }
            boolean success = action == Action.RELEASE_VILLAGE ? manager.releaseVillage(origin, targetId)
                    : cancel ? warfare.cancel(origin, targetId) : warfare.declare(origin, targetId, minutes);
            if (!success) display(player, "mca.war.invalid");
        } catch (NumberFormatException exception) { display(player, "mca.war.invalid"); }
    }

    private void handleBuildingOrFloorScan(VillageManager manager, ServerPlayer player, BlockPos source, Action action) {
        if (action == Action.ADD_BUILDING || action == Action.ADD) {
            Building.validationResult result = manager.scanStructure(source);
            displayResult(player, result, "blueprint.buildingAdded");
            if (result == Building.validationResult.SUCCESS) {
                Village village = manager.findNearestVillage(source, Village.MERGE_MARGIN).orElse(null);
                com.ancientkira.mca.server.world.data.Building room = village == null ? null : village.getBuildingAt(source).orElse(null);
                if (room != null && village.canRegisterOutpost(room)) {
                    Network.sendToPlayer(new BuildingPolymorphMessage(java.util.List.of("house", "pillager_outpost"),
                            source, Action.FORCE_TYPE, room.getStructureId()), player);
                }
            }
            return;
        }
        Village village = manager.findNearestVillage(source, Village.MERGE_MARGIN).orElse(null);
        RoomScanPlan plan = village == null ? RoomScanPlan.addBuilding(source) : village.getRoomScanPlan(source);
        if (plan.mode().isAttachment()) {
            BuildingScanResult roomScan = manager.analyzeAttachedRoom(source, plan.mode(), plan.targetBuildingId());
            if (roomScan.result() == Building.validationResult.SUCCESS) {
                handleRoomScan(manager, player, roomScan, plan.mode() == Village.RoomScanMode.ADD_FLOOR
                        ? Action.ADD_FLOOR : Action.ADD_BASEMENT, plan.targetBuildingId());
                return;
            }
        }
        handleBuildingScan(manager, player, manager.analyzeBuilding(source, false), action, -1);
    }

    private static OptionalInt parseTargetBuildingId(String value) {
        if (value == null) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }

    private void handleBuildingScan(VillageManager manager, ServerPlayer player, BuildingScanResult scan,
                                   Action action, int targetId) {
        if (scan.result() == Building.validationResult.SUCCESS && scan.isAmbiguous()) {
            Network.sendToPlayer(new BuildingPolymorphMessage(scan.matchingTypes(), scan.source(), action, targetId), player);
        } else {
            displayResult(player, manager.commitBuilding(scan, null), "blueprint.buildingAdded");
        }
    }

    private void handleRoomScan(VillageManager manager, ServerPlayer player, BuildingScanResult scan,
                                Action action, int targetId) {
        if (scan.result() == Building.validationResult.SUCCESS && scan.isAmbiguous()) {
            Network.sendToPlayer(new BuildingPolymorphMessage(scan.matchingTypes(), scan.source(), action, targetId), player);
            return;
        }
        Building.validationResult result;
        if (action == Action.UPDATE_ROOM) {
            result = manager.commitRoomUpdate(scan, null, targetId);
            displayResult(player, result, "blueprint.roomUpdated");
        } else {
            Village.RoomScanMode mode = switch (action) {
                case ADD_FLOOR -> Village.RoomScanMode.ADD_FLOOR;
                case ADD_BASEMENT -> Village.RoomScanMode.ADD_BASEMENT;
                case ADD_BUILDING, ADD -> Village.RoomScanMode.ADD_BUILDING;
                default -> Village.RoomScanMode.ADD_ROOM;
            };
            result = manager.commitRoomAddition(scan, null, mode, targetId);
            String successKey = switch (mode) {
                case ADD_BUILDING -> "blueprint.buildingAdded";
                case ADD_FLOOR -> "blueprint.floorAdded";
                case ADD_BASEMENT -> "blueprint.basementAdded";
                default -> "blueprint.roomAdded";
            };
            displayResult(player, result, successKey);
        }
    }

    private static void displayResult(ServerPlayer player, Building.validationResult result, String successKey) {
        String key = result == Building.validationResult.SUCCESS && successKey != null
                ? successKey : "blueprint.scan." + result.name().toLowerCase(Locale.ENGLISH);
        display(player, key);
    }

    private static void displayEditResult(ServerPlayer player, VillageManager.BuildingEditResult result, String successKey) {
        String key = switch (result) {
            case SUCCESS -> successKey;
            case NO_BUILDING -> "blueprint.noBuilding";
            case NO_ROOM -> "blueprint.noRoomOnFloor";
            case CANNOT_REMOVE_MAIN_ROOM -> "blueprint.cannotRemoveMainRoom";
        };
        if (key != null) {
            display(player, key);
        }
    }

    private static void display(ServerPlayer player, String translationKey) {
        player.sendSystemMessage(Component.translatable(translationKey), true);
    }

    @Override
    public CustomPacketPayload.Type<ReportBuildingMessage> type() {
        return TYPE;
    }

    public enum Action {
        AUTO_SCAN,
        ADD_ROOM,
        ADD,
        REMOVE,
        FORCE_TYPE,
        FULL_SCAN,
        REMOVE_ROOM,
        UPDATE_ROOM,
        SET_MAIN_ROOM,
        SET_ROOM_INHERITANCE,
        ADD_BUILDING,
        ADD_FLOOR,
        ADD_BASEMENT,
        DECLARE_WAR,
        CANCEL_WAR,
        RELEASE_VILLAGE,
        TOGGLE_FRIENDLY,
        SET_POLICY,
        START_INDEPENDENCE,
        MERGE_VILLAGE
    }
}
