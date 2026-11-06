package com.ancientkira.mca.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

/** Shared bed detection, including the optional Drops Backport straw bed. */
public final class BedCompatibility {
    private BedCompatibility() { }
    public static boolean isBed(BlockState state) {
        return (state.is(BlockTags.BEDS) || state.getBlock() instanceof BedBlock)
                && state.hasProperty(BedBlock.PART) && state.hasProperty(BedBlock.OCCUPIED)
                && state.hasProperty(BedBlock.FACING);
    }
    public static void registerHomeStates() {
        BuiltInRegistries.BLOCK.get(Identifier.withDefaultNamespace("straw_bed")).ifPresent(block -> {
            var states = block.value().getStateDefinition().getPossibleStates().stream()
                    .filter(state -> state.hasProperty(BedBlock.PART)
                            && state.getValue(BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD)
                    .filter(state -> PoiTypes.forState(state).isEmpty())
                    .collect(java.util.stream.Collectors.toSet());
            if (!states.isEmpty()) com.ancientkira.mca.mixin.PoiTypesAccessor.mca$registerStates(
                    BuiltInRegistries.POINT_OF_INTEREST_TYPE.getOrThrow(PoiTypes.HOME), states);
        });
    }
}
