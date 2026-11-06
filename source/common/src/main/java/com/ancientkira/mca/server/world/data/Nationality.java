package com.ancientkira.mca.server.world.data;

import com.ancientkira.mca.util.NbtHelper;
import com.ancientkira.mca.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

public class Nationality extends SavedData {
    private static final int CHUNK_SIZE = 128;
    private static final int[][] neighbours = {
            {0, 0},
            {-1, 0},
            {1, 0},
            {0, -1},
            {0, 1},
            {-1, 1},
            {1, 1},
            {-1, -1},
            {1, -1},
    };
    final RandomSource random = RandomSource.create();
    private Map<Long, Integer> map = new HashMap<>();
    private String nameLanguage = "en_us";

    Nationality(ServerLevel level) {

    }

    Nationality(CompoundTag nbt, HolderLookup.Provider provider) {
        nameLanguage = nbt.getStringOr("NameLanguage", "en_us");
        CompoundTag regions = nbt.copy();
        regions.remove("NameLanguage");
        map = NbtHelper.toMap(regions, Long::valueOf, e -> e.asInt().orElse(0));
    }

    public static Nationality get(ServerLevel world) {
        return WorldUtils.loadData(world.getServer().overworld(), Nationality::new, Nationality::new, "mca_nationality");
    }

    private static long toId(long x, long z) {
        return x / CHUNK_SIZE * (long) Integer.MAX_VALUE + z / CHUNK_SIZE;
    }

    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        NbtHelper.fromMap(nbt, map, String::valueOf, IntTag::valueOf);
        nbt.putString("NameLanguage", nameLanguage);
        return nbt;
    }

    public String getNameLanguage() { return nameLanguage; }

    public void rememberNameLanguage(String language) {
        if (language != null && !language.isBlank() && !language.equals(nameLanguage)) {
            nameLanguage = language;
            setDirty();
        }
    }

    public int getRegionId(BlockPos pos) {
        int id = -1;
        for (int[] neighbour : neighbours) {
            int x = pos.getX() + neighbour[0] * CHUNK_SIZE;
            int z = pos.getZ() + neighbour[1] * CHUNK_SIZE;
            long rid = toId(x, z);
            if (map.containsKey(rid)) {
                id = map.get(rid);
                break;
            }
        }
        if (id == -1) {
            id = random.nextInt();
        }

        long rid = toId(pos.getX(), pos.getZ());
        if (!map.containsKey(rid)) {
            map.put(rid, id);
            setDirty();
        }
        return id;
    }
}
