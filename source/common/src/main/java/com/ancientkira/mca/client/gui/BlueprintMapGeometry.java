package com.ancientkira.mca.client.gui;

import com.ancientkira.mca.resources.data.BuildingType;
import com.ancientkira.mca.server.world.data.Building;
import com.ancientkira.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class BlueprintMapGeometry {
    private final List<MapFootprintLayer> footprintLayers;
    private final List<Integer> floorOrdinals;

    private BlueprintMapGeometry(List<MapFootprintLayer> footprintLayers, List<Integer> floorOrdinals) {
        this.footprintLayers = footprintLayers;
        this.floorOrdinals = floorOrdinals;
    }

    static BlueprintMapGeometry empty() {
        return new BlueprintMapGeometry(List.of(), List.of());
    }

    static BlueprintMapGeometry build(Village village) {
        return build(village, BlueprintRoomTypeResolver.create(village));
    }

    static BlueprintMapGeometry build(Village village, BlueprintRoomTypeResolver typeResolver) {
        if (village == null) {
            return empty();
        }

        List<MapFootprintLayer> layers = new ArrayList<>();
        List<Integer> floors = village.getFloorOrdinals();

        village.getBuildings().values().stream()
                .filter(Building::isComplete)
                .sorted(Comparator.comparingInt(Building::getId))
                .forEach(building -> {
                    BlockPos min = building.getPos0();
                    BlockPos max = building.getPos1();
                    var shape = BlueprintMapFootprint.shape(BlueprintMapFootprint.rectangle(
                            min.getX(), min.getZ(), max.getX(), max.getZ()));
                    layers.add(new MapFootprintLayer(
                            building, typeResolver.resolve(building).presentationType(), shape.cells(), shape.spans(), shape.edges(),
                            building.getFloorNumber(), building.getStructureId(), building.getCenter().getY()));
                });

        return new BlueprintMapGeometry(List.copyOf(layers), List.copyOf(floors));
    }

    List<MapFootprintLayer> footprintLayers(Integer selectedFloor) {
        if (selectedFloor == null) {
            return footprintLayers;
        }
        return footprintLayers.stream().filter(layer -> layer.floorOrdinal() == selectedFloor).toList();
    }

    List<MapFootprintLayer> allFootprintLayers() {
        return footprintLayers;
    }

    List<Integer> floorOrdinals() {
        return floorOrdinals;
    }

    record MapFootprintLayer(Building building, BuildingType presentationType,
                             java.util.Set<BlueprintMapFootprint.Cell> footprintCells,
                             List<BlueprintMapFootprint.RowSpan> fillSpans,
                             List<BlueprintMapFootprint.Edge> outlineEdges,
                             Integer floorOrdinal, int logicalBuildingId, int anchorY) {
    }
}
