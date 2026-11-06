package com.ancientkira.mca.server;

import com.ancientkira.mca.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Track actual farmland loss rather than letting farmers turn surrounding dirt into new farms. */
public final class FarmRepairs extends SavedData {
    private final Set<Long> plots=new HashSet<>();
    private FarmRepairs(ServerLevel world) {}
    private FarmRepairs(CompoundTag data) { for(long p:data.getLongArray("plots").orElse(new long[0]))plots.add(p); }
    private static FarmRepairs get(ServerLevel world) {
        return WorldUtils.loadData(world,(tag,provider)->new FarmRepairs(tag),FarmRepairs::new,"mca_farm_repairs");
    }
    public static void trampled(ServerLevel world,BlockPos pos) {var data=get(world);if(data.plots.add(pos.asLong()))data.setDirty();}
    public static boolean needsRepair(ServerLevel world,BlockPos pos) {return get(world).plots.contains(pos.asLong());}
    public static void repaired(ServerLevel world,BlockPos pos) {var data=get(world);if(data.plots.remove(pos.asLong()))data.setDirty();}
    public static List<BlockPos> nearby(ServerLevel world, BlockPos center) {
        return get(world).plots.stream().map(BlockPos::of)
                .filter(pos -> pos.distSqr(center) <= 64 && world.hasChunkAt(pos))
                .sorted(Comparator.comparingDouble(center::distSqr))
                .limit(32).toList();
    }
    public CompoundTag save(CompoundTag data,HolderLookup.Provider provider) {
        data.putLongArray("plots",plots.stream().mapToLong(Long::longValue).toArray());return data;
    }
}
