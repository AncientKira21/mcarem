package com.ancientkira.mca.server.world;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.GravityProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Terrain-following streets use the ground, rather than the tops of plants or slabs. */
public final class VillageStreetGravityProcessor extends GravityProcessor {
    public static final VillageStreetGravityProcessor INSTANCE = new VillageStreetGravityProcessor();

    private VillageStreetGravityProcessor() {
        super(Heightmap.Types.WORLD_SURFACE_WG, -1);
    }

    public static int surface(LevelReader level, int x, int z) {
        int top = Math.max(level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z),
                level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top - 1, z);
        while (pos.getY() >= level.getMinY()) {
            var state = level.getBlockState(pos);
            // Existing paths retain their elevation; water retains the bridge waterline.
            if (state.is(Blocks.DIRT_PATH) || state.is(Blocks.WATER)
                    || (!(state.getBlock() instanceof SlabBlock) && !state.is(BlockTags.LEAVES)
                        && !state.is(BlockTags.FLOWERS)
                        && !state.is(Blocks.SHORT_GRASS) && !state.is(Blocks.TALL_GRASS)
                        && !state.is(Blocks.FERN) && !state.is(Blocks.LARGE_FERN)
                        && !state.is(Blocks.BUSH) && !state.is(Blocks.DEAD_BUSH)
                        && (state.is(Blocks.GRASS_BLOCK) || state.isCollisionShapeFullBlock(level, pos)))) {
                return pos.getY() + 1;
            }
            pos.move(0, -1, 0);
        }
        return top;
    }

    // 26.1 passes the original block; 26.2+ passes its relative position.
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos targetPosition,
            BlockPos referencePos, StructureTemplate.StructureBlockInfo original,
            StructureTemplate.StructureBlockInfo processed, StructurePlaceSettings settings) {
        return processBlock(level, targetPosition, referencePos, original.pos(), processed, settings);
    }

    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos targetPosition,
            BlockPos referencePos, BlockPos original,
            StructureTemplate.StructureBlockInfo processed, StructurePlaceSettings settings) {
        var pos = processed.pos();
        int y = surface(level, pos.getX(), pos.getZ()) - 1 + original.getY();
        return new StructureTemplate.StructureBlockInfo(new BlockPos(pos.getX(), y, pos.getZ()),
                processed.state(), processed.nbt());
    }
}
