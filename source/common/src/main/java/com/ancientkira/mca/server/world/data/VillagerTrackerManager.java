package com.ancientkira.mca.server.world.data;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.util.MaxSizeHashMap;
import com.ancientkira.mca.util.NbtHelper;
import com.ancientkira.mca.util.WorldUtils;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.UUID;

public class VillagerTrackerManager extends SavedData {
    private final static int MAP_SIZE = 1024 * 16;

    private final Map<UUID, GlobalPos> entries;

    VillagerTrackerManager(ServerLevel world) {
        entries = new MaxSizeHashMap<>(MAP_SIZE);
    }

    VillagerTrackerManager(CompoundTag nbt, HolderLookup.Provider provider) {
        entries = new MaxSizeHashMap<>(MAP_SIZE);
        entries.putAll(NbtHelper.toMap(nbt, UUID::fromString, (id, element) -> NbtHelper.decodeGlobalPos(element)));
    }

    public static VillagerTrackerManager get(ServerLevel world) {
        return WorldUtils.loadData(world.getServer().overworld(), VillagerTrackerManager::new, VillagerTrackerManager::new, "mca_villager_tracker");
    }

    public static void update(Entity entity) {
        if (Config.getInstance().trackVillagerPosition && entity.level() instanceof ServerLevel serverWorld) {
            get(serverWorld).set(entity);
        }
    }

    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        return NbtHelper.fromMap(nbt, entries, UUID::toString, NbtHelper::encodeGlobalPosition);
    }

    public void remove(UUID id) {
        entries.remove(id);
        setDirty();
    }

    public void set(Entity entity) {
        entries.put(entity.getUUID(), GlobalPos.of(entity.level().dimension(), entity.blockPosition()));
    }

    public GlobalPos get(UUID id) {
        return entries.get(id);
    }
}
