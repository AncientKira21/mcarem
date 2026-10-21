package com.ancientkira.mca.server;

import com.ancientkira.mca.block.TombstoneBlock;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Records ownership, never infers it from a flower merely being near a grave. */
public final class GraveFlowers {
    private static final Map<Level, Map<BlockPos, WeakReference<TombstoneBlock.Data>>> OWNERS = new WeakHashMap<>();
    private GraveFlowers() { }

    public static void track(Level world, BlockPos pos, TombstoneBlock.Data grave) {
        if (!world.isClientSide()) OWNERS.computeIfAbsent(world, ignored -> new HashMap<>())
                .put(pos.immutable(), new WeakReference<>(grave));
    }

    public static void changed(Level world, BlockPos pos, BlockState state) {
        if (world.isClientSide()) return;
        var positions = OWNERS.get(world);
        if (positions == null) return;
        var reference = positions.get(pos);
        TombstoneBlock.Data grave = reference == null ? null : reference.get();
        if (grave == null || grave.isRemoved()) { positions.remove(pos); return; }
        if (!grave.ownsDecoration(pos, state)) {
            positions.remove(pos);
            grave.forgetDecoration(pos);
        }
    }

    public static boolean place(ServerLevel world, BlockPos gravePos, VillagerEntityMCA mourner, ItemStack flower) {
        if (!(world.getBlockEntity(gravePos) instanceof TombstoneBlock.Data grave) || !grave.hasEntity()
                || !(flower.getItem() instanceof BlockItem item)) return false;
        BlockState plant = item.getBlock().defaultBlockState();
        BlockState potted = plant.is(Blocks.WHITE_TULIP) ? Blocks.POTTED_WHITE_TULIP.defaultBlockState()
                : plant.is(Blocks.RED_TULIP) ? Blocks.POTTED_RED_TULIP.defaultBlockState()
                : plant.is(Blocks.ORANGE_TULIP) ? Blocks.POTTED_ORANGE_TULIP.defaultBlockState()
                : plant.is(Blocks.PINK_TULIP) ? Blocks.POTTED_PINK_TULIP.defaultBlockState() : null;
        if (potted == null) return false;
        var candidates = new ArrayList<BlockPos>();
        for (int dy : new int[]{0, -1, 1}) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (x != 0 || z != 0) candidates.add(gravePos.offset(x, dy, z));
        }
        java.util.Collections.shuffle(candidates, new java.util.Random(mourner.getRandom().nextLong()));
        // Prefer a flower in grass. Only fall back to a pot if there is no usable grass spot.
        for (boolean grass : new boolean[]{true, false}) for (BlockPos pos : candidates) {
            if (!world.hasChunkAt(pos) || !world.isEmptyBlock(pos) || !world.getFluidState(pos).isEmpty()) continue;
            BlockPos below = pos.below();
            if (grass ? !world.getBlockState(below).is(Blocks.GRASS_BLOCK)
                    : !world.getBlockState(below).isFaceSturdy(world, below, Direction.UP)) continue;
            BlockState placed = grass ? plant : potted;
            if (!placed.canSurvive(world, pos) || !world.setBlockAndUpdate(pos, placed)) continue;
            grave.recordDecoration(pos, placed);
            return true;
        }
        return false;
    }
}
