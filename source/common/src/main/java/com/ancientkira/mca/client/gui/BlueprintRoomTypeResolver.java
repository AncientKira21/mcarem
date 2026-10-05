package com.ancientkira.mca.client.gui;

import com.ancientkira.mca.resources.BuildingTypes;
import com.ancientkira.mca.resources.data.BuildingType;
import com.ancientkira.mca.server.world.data.Building;
import com.ancientkira.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class BlueprintRoomTypeResolver {
    private final Village village;

    private BlueprintRoomTypeResolver(Village village) {
        this.village = village;
    }

    static BlueprintRoomTypeResolver create(Village village) {
        return new BlueprintRoomTypeResolver(village);
    }

    Context resolve(Building room) {
        if (room == null || village == null) {
            return new Context(room, room, Map.of(), Map.of(), room == null ? null : room.getBuildingType(), List.of());
        }

        Building mainRoom = village.getMainRoom(room).orElse(room);
        List<Building> contributors = List.of();
        Map<Identifier, List<BlockPos>> inherited = Map.of();
        if (room.isInheritanceEnabled() && mainRoom.isInheritanceEnabled()) {
            contributors = village.getBuildings().values().stream()
                    .filter(Building::isStrictScan)
                    .filter(candidate -> candidate.getStructureId() == room.getStructureId())
                    .filter(candidate -> candidate.getId() != room.getId())
                    .filter(Building::isInheritanceEnabled)
                    .filter(candidate -> !candidate.getBlocks().isEmpty())
                    .sorted(Comparator.comparingInt(Building::getId))
                    .toList();
            if (room.getId() == mainRoom.getId()) {
                inherited = merge(contributors);
            }
        }

        Map<Identifier, List<BlockPos>> effective = merge(room.getBlocks(), inherited);
        BuildingType type = room.getBuildingType();
        if (room.getId() == mainRoom.getId() && !room.isTypeForced() && !inherited.isEmpty()) {
            type = matchingTypes(effective).stream().findFirst().orElse(type);
        }
        return new Context(room, mainRoom, room.getBlocks(), inherited, type, contributors);
    }

    private static List<BuildingType> matchingTypes(Map<Identifier, List<BlockPos>> blocks) {
        List<BuildingType> matches = new ArrayList<>();
        for (BuildingType type : BuildingTypes.getInstance()) {
            if (type.grouped()) {
                continue;
            }
            Map<Identifier, List<BlockPos>> available = type.getGroups(blocks);
            boolean matchesType = type.getGroups().entrySet().stream()
                    .allMatch(entry -> available.getOrDefault(entry.getKey(), List.of()).size() >= entry.getValue());
            if (matchesType) {
                matches.add(type);
            }
        }
        matches.sort(Comparator.comparingInt(BuildingType::priority).reversed().thenComparing(BuildingType::name));
        return matches.stream()
                .filter(type -> type.visible() || type.name().equals("house"))
                .filter(type -> !type.name().equals("blocked") && !type.name().equals("building"))
                .filter(type -> !type.name().equals("house")
                        || matches.stream().noneMatch(candidate -> candidate.name().equals("big_house")))
                .toList();
    }

    private static Map<Identifier, List<BlockPos>> merge(List<Building> buildings) {
        Map<Identifier, LinkedHashSet<BlockPos>> result = new LinkedHashMap<>();
        for (Building building : buildings) {
            building.getBlocks().forEach((type, positions) ->
                    result.computeIfAbsent(type, ignored -> new LinkedHashSet<>()).addAll(positions));
        }
        return immutableSets(result);
    }

    private static Map<Identifier, List<BlockPos>> merge(Map<Identifier, List<BlockPos>> first,
                                                           Map<Identifier, List<BlockPos>> second) {
        Map<Identifier, LinkedHashSet<BlockPos>> result = new LinkedHashMap<>();
        first.forEach((type, positions) -> result.put(type, new LinkedHashSet<>(positions)));
        second.forEach((type, positions) -> result.computeIfAbsent(type, ignored -> new LinkedHashSet<>()).addAll(positions));
        return immutableSets(result);
    }

    private static Map<Identifier, List<BlockPos>> immutableSets(Map<Identifier, LinkedHashSet<BlockPos>> blocks) {
        Map<Identifier, List<BlockPos>> result = new LinkedHashMap<>();
        blocks.forEach((type, positions) -> result.put(type, List.copyOf(positions)));
        return Map.copyOf(result);
    }

    record Context(Building room, Building mainRoom, Map<Identifier, List<BlockPos>> ownBlocks,
                   Map<Identifier, List<BlockPos>> inheritedBlocks, BuildingType presentationType,
                   List<Building> contributors) {
        boolean isMainRoom() {
            return room != null && mainRoom != null && room.getId() == mainRoom.getId();
        }

        boolean contributesToMain() {
            return room != null && !isMainRoom() && room.isInheritanceEnabled()
                    && mainRoom != null && mainRoom.isInheritanceEnabled();
        }
    }
}
