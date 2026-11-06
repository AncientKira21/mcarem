package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Small incremental discovery plus a chunk-indexed cache of observed fire changes. */
public final class FirefighterSearch {
    private static final Map<ServerLevel, Index> WORLDS = new WeakHashMap<>();
    private static final List<BlockPos> OFFSETS = new ArrayList<>();
    static {
        for (int x=-16;x<=16;x++) for (int y=-6;y<=6;y++) for (int z=-16;z<=16;z++) OFFSETS.add(new BlockPos(x,y,z));
        OFFSETS.sort(Comparator.comparingInt(pos -> pos.getX()*pos.getX()+pos.getY()*pos.getY()+pos.getZ()*pos.getZ()));
    }
    private FirefighterSearch() {}
    private static class Index {
        final Map<Long, Set<BlockPos>> chunks = new HashMap<>();
        long tick = Long.MIN_VALUE;
        int remaining;
    }
    public static boolean fire(BlockState state) {
        return state.getBlock() instanceof BaseFireBlock || state.is(BlockTags.FIRE);
    }
    private static long chunk(int x,int z) { return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32); }
    private static long chunk(BlockPos pos) { return chunk(pos.getX()>>4,pos.getZ()>>4); }
    public static void changed(ServerLevel world, BlockPos pos, BlockState state) {
        Index index = WORLDS.get(world);
        if (fire(state)) {
            if (index == null) { index = new Index(); WORLDS.put(world,index); }
            index.chunks.computeIfAbsent(chunk(pos),key -> new HashSet<>()).add(pos.immutable());
        } else if (index != null) {
            Set<BlockPos> positions = index.chunks.get(chunk(pos));
            if (positions != null) { positions.remove(pos); if (positions.isEmpty()) index.chunks.remove(chunk(pos)); }
        }
    }
    public static BlockPos fire(VillagerEntityMCA person, ServerLevel world) {
        Index index = WORLDS.computeIfAbsent(world,key -> new Index());
        BlockPos origin = person.blockPosition();
        BlockPos nearest = null;
        double distance = Double.MAX_VALUE;
        int inspected=0;
        known: for (int x=(origin.getX()-16)>>4;x<=(origin.getX()+16)>>4;x++) for (int z=(origin.getZ()-16)>>4;z<=(origin.getZ()+16)>>4;z++) {
            Set<BlockPos> positions = index.chunks.get(chunk(x,z));
            if (positions == null) continue;
            for (var it=positions.iterator();it.hasNext();) {
                if(inspected++>=64)break known;
                BlockPos pos=it.next();
                if (!world.hasChunkAt(pos)) { it.remove(); continue; }
                if (!fire(world.getBlockState(pos))) { it.remove(); continue; }
                if (Math.abs(pos.getX()-origin.getX())>16 || Math.abs(pos.getY()-origin.getY())>6 || Math.abs(pos.getZ()-origin.getZ())>16) continue;
                double d=pos.distSqr(origin);
                if(d<distance){nearest=pos;distance=d;}
            }
        }
        if (nearest != null) return nearest;
        return scan(person,world,false);
    }
    public static BlockPos scan(VillagerEntityMCA person, ServerLevel world, boolean hydrant) {
        Index index=WORLDS.computeIfAbsent(world,key -> new Index());
        long now=world.getGameTime();
        if(index.tick!=now){index.tick=now;index.remaining=1024;}
        if(Math.floorMod(person.tickCount+person.getId(),4)!=0 || index.remaining<=0)return null;
        var state=person.getWaterClutchState();
        String key=hydrant?"HydrantScan":"FireScan";
        int cursor=state.getIntOr(key,0);
        int count=Math.min(64,index.remaining);index.remaining-=count;
        for(int n=0;n<count;n++){
            BlockPos pos=person.blockPosition().offset(OFFSETS.get(Math.floorMod(cursor++,OFFSETS.size())));
            if(!world.hasChunkAt(pos))continue;
            boolean match=hydrant?com.ancientkira.mca.compat.FireExtinguisherCompat.hydrant(world,pos):fire(world.getBlockState(pos));
            if(match){state.putInt(key,cursor%OFFSETS.size());if(!hydrant)changed(world,pos,world.getBlockState(pos));return pos;}
        }
        state.putInt(key,cursor%OFFSETS.size());return null;
    }
}
