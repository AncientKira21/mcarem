package com.ancientkira.mca.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.LinkedHashMap;

final class BlueprintTerrainRenderer {
    private static final int TARGET_CELL_PIXELS = 4;
    private static final int MAX_CACHED_CELLS = 30_000;
    private static final int FALLBACK_COLOR = 0xff6f826f;
    private final LinkedHashMap<Long, TerrainCell> cache = new LinkedHashMap<>(1024, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Long, TerrainCell> eldest) {
            return size() > MAX_CACHED_CELLS;
        }
    };
    private ClientLevel cachedLevel;

    void render(GuiGraphicsExtractor context, BlueprintMapViewport viewport) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        ClientLevel level = minecraft.level;
        if (cachedLevel != level) {
            cache.clear();
            cachedLevel = level;
        }

        int step = Math.max(1, (int) Math.ceil((double) TARGET_CELL_PIXELS / viewport.scale()));
        int radius = (int) Math.ceil((viewport.halfSize() + step) / viewport.scale());
        int minX = (int) Math.floor(viewport.mapCenterX()) - radius;
        int maxX = (int) Math.floor(viewport.mapCenterX()) + radius;
        int minZ = (int) Math.floor(viewport.mapCenterZ()) - radius;
        int maxZ = (int) Math.floor(viewport.mapCenterZ()) + radius;

        for (int worldX = minX; worldX <= maxX; worldX += step) {
            for (int worldZ = minZ; worldZ <= maxZ; worldZ += step) {
                TerrainCell cell = sample(level, worldX, worldZ);
                if (cell == null) {
                    continue;
                }

                double left = viewport.screenX(worldX);
                double top = viewport.screenY(worldZ);
                double right = viewport.screenX(worldX + step);
                double bottom = viewport.screenY(worldZ + step);
                int shade = shadeColor(cell.color(), cell.slope(), cell.elevation());
                context.fill(
                        Math.max(viewport.left() + 1, (int) Math.floor(left)),
                        Math.max(viewport.top() + 1, (int) Math.floor(top)),
                        Math.min(viewport.right() - 1, Math.max((int) Math.floor(left) + 1, (int) Math.ceil(right))),
                        Math.min(viewport.bottom() - 1, Math.max((int) Math.floor(top) + 1, (int) Math.ceil(bottom))),
                        shade
                );
            }
        }
    }

    private TerrainCell sample(ClientLevel level, int x, int z) {
        long key = ((long) x << 32) | (z & 0xffffffffL);
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        if (!level.hasChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) {
            cache.put(key, null);
            return null;
        }

        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        int minBuildHeight = level.getMinY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, surfaceY, z);
        BlockState state = level.getBlockState(pos);
        MapColor mapColor = state.getMapColor(level, pos);
        while (mapColor == MapColor.NONE && pos.getY() > minBuildHeight) {
            pos.move(0, -1, 0);
            state = level.getBlockState(pos);
            mapColor = state.getMapColor(level, pos);
        }

        int color = mapColor == MapColor.NONE ? FALLBACK_COLOR : 0xff000000 | mapColor.col & 0x00ffffff;
        int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int east = sampleHeight(level, x + 1, z, height);
        int west = sampleHeight(level, x - 1, z, height);
        int south = sampleHeight(level, x, z + 1, height);
        int north = sampleHeight(level, x, z - 1, height);
        float slope = (west - east + north - south) * 0.25f;
        TerrainCell cell = new TerrainCell(color, slope, height);
        cache.put(key, cell);
        return cell;
    }

    private static int sampleHeight(ClientLevel level, int x, int z, int fallback) {
        if (!level.hasChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) {
            return fallback;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private static int shadeColor(int color, float slope, int height) {
        float brightness = Math.max(0.65f, Math.min(1.12f, 0.92f + slope * 0.045f + height * 0.0003f));
        int red = Math.min(255, Math.round((color >>> 16 & 0xff) * brightness));
        int green = Math.min(255, Math.round((color >>> 8 & 0xff) * brightness));
        int blue = Math.min(255, Math.round((color & 0xff) * brightness));
        return 0xff000000 | red << 16 | green << 8 | blue;
    }

    private record TerrainCell(int color, float slope, int elevation) {
    }
}
