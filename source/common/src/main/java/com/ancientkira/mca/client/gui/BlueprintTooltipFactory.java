package com.ancientkira.mca.client.gui;

import com.ancientkira.mca.server.world.data.Building;
import com.ancientkira.mca.server.world.data.Village;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class BlueprintTooltipFactory {
    private final Village village;
    private final BlueprintRoomTypeResolver roomTypeResolver;

    private BlueprintTooltipFactory(Village village, BlueprintRoomTypeResolver roomTypeResolver) {
        this.village = village;
        this.roomTypeResolver = roomTypeResolver;
    }

    static BlueprintTooltipFactory empty() {
        return new BlueprintTooltipFactory(null, BlueprintRoomTypeResolver.create(null));
    }

    static BlueprintTooltipFactory create(Village village, BlueprintRoomTypeResolver roomTypeResolver) {
        return village == null ? empty() : new BlueprintTooltipFactory(village, roomTypeResolver);
    }

    List<Component> tooltip(Building building, Integer floorOrdinal) {
        if (village == null || building == null) {
            return List.of();
        }

        List<Component> lines = new ArrayList<>();
        BlueprintRoomTypeResolver.Context context = roomTypeResolver.resolve(building);
        lines.add(Component.translatable("buildingType." + context.presentationType().name()));
        if (floorOrdinal != null) {
            lines.add(Component.translatable(floorOrdinal == 0
                    ? "gui.blueprint.floor.ground"
                    : floorOrdinal > 0 ? "gui.blueprint.floor.upper" : "gui.blueprint.floor.basement",
                    Math.abs(floorOrdinal)).withStyle(ChatFormatting.GRAY));
        }
        if (context.isMainRoom()) {
            lines.add(Component.translatable("gui.blueprint.roomTooltip.mainRoom").withStyle(ChatFormatting.GOLD));
        } else if (context.contributesToMain()) {
            lines.add(Component.translatable("gui.blueprint.roomTooltip.contributesToMain").withStyle(ChatFormatting.DARK_AQUA));
        }

        village.getResidents(building.getId()).forEach(name -> lines.add(Component.literal(name).withStyle(ChatFormatting.GRAY)));
        appendPoiLines(lines, context.ownBlocks());
        if (!context.inheritedBlocks().isEmpty()) {
            lines.add(Component.translatable("gui.blueprint.roomTooltip.inheritedPoi").withStyle(ChatFormatting.AQUA));
            appendPoiLines(lines, context.inheritedBlocks());
            context.contributors().forEach(contributor -> lines.add(Component.translatable(
                    "gui.blueprint.roomTooltip.inheritedFrom", contributor.getId()).withStyle(ChatFormatting.DARK_GRAY)));
        }
        BlockPos center = building.getCenter();
        lines.add(Component.translatable("gui.blueprint.roomTooltip.position", center.getX(), center.getY(), center.getZ())
                .withStyle(ChatFormatting.DARK_GRAY));
        return List.copyOf(lines);
    }

    private static void appendPoiLines(List<Component> lines, Map<Identifier, List<BlockPos>> blocks) {
        for (var entry : blocks.entrySet()) {
            lines.add(Component.literal(entry.getValue().size() + " x ")
                    .append(BuiltInBlockName.get(entry.getKey())).withStyle(ChatFormatting.GRAY));
        }
    }

    private static final class BuiltInBlockName {
        private static Component get(Identifier id) {
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id);
            return block.<Component>map(holder -> holder.value().getName())
                    .orElseGet(() -> Component.translatable("tag.block." + id.getNamespace() + "." + id.getPath()));
        }
    }
}
