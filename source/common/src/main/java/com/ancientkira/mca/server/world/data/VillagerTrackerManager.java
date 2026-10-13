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
    private final Map<UUID, CompoundTag> captives = new java.util.HashMap<>();
    private final Map<UUID, UUID> rescueRequests = new java.util.HashMap<>();
    private final Map<UUID, Integer> rescueAttempts = new java.util.HashMap<>();

    VillagerTrackerManager(ServerLevel world) {
        entries = new MaxSizeHashMap<>(MAP_SIZE);
    }

    VillagerTrackerManager(CompoundTag nbt, HolderLookup.Provider provider) {
        entries = new MaxSizeHashMap<>(MAP_SIZE);
        CompoundTag locations = nbt.copy();
        locations.remove("Captives");
        entries.putAll(NbtHelper.toMap(locations, UUID::fromString, (id, element) -> NbtHelper.decodeGlobalPos(element)));
        nbt.getCompound("Captives").ifPresent(saved -> saved.keySet().forEach(key ->
                saved.getCompound(key).ifPresent(data -> captives.put(UUID.fromString(key), data.copy()))));
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
        NbtHelper.fromMap(nbt, entries, UUID::toString, NbtHelper::encodeGlobalPosition);
        CompoundTag saved = new CompoundTag();
        captives.forEach((id, data) -> saved.put(id.toString(), data.copy()));
        nbt.put("Captives", saved);
        return nbt;
    }

    public void remove(UUID id) {
        entries.remove(id);
        captives.remove(id);
        rescueRequests.remove(id);
        rescueAttempts.remove(id);
        setDirty();
    }

    public void set(Entity entity) {
        entries.put(entity.getUUID(), GlobalPos.of(entity.level().dimension(), entity.blockPosition()));
        setDirty();
    }

    public void recordCaptive(com.ancientkira.mca.entity.VillagerEntityMCA person) {
        set(person);
        var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, person.registryAccess());
        person.saveWithoutId(output);
        CompoundTag data = output.buildResult();
        data.remove("Brain");
        data.remove("Memories");
        data.remove("Inventory");
        captives.put(person.getUUID(), data);
        setDirty();
    }

    public void clearCaptive(UUID id) {
        captives.remove(id);
        rescueRequests.remove(id);
        rescueAttempts.remove(id);
        setDirty();
    }

    public Map<UUID, CompoundTag> getCaptives() { return java.util.Collections.unmodifiableMap(captives); }

    public void requestRescue(UUID id, net.minecraft.server.level.ServerPlayer player) {
        GlobalPos pos = entries.get(id);
        if (!captives.containsKey(id) || pos == null) return;
        ServerLevel world = player.level().getServer().getLevel(pos.dimension());
        if (world == null) return;
        rescueRequests.put(id, player.getUUID());
        rescueAttempts.put(id, 0);
        // Chunk terrain and saved entities load separately; finish on a subsequent server tick.
        world.getChunk(pos.pos().getX() >> 4, pos.pos().getZ() >> 4);
    }

    public void tickRescueRequests(net.minecraft.server.MinecraftServer server) {
        for (UUID id : java.util.List.copyOf(rescueRequests.keySet())) {
            GlobalPos pos = entries.get(id);
            ServerLevel world = pos == null ? null : server.getLevel(pos.dimension());
            if (world == null || server.getPlayerList().getPlayer(rescueRequests.get(id)) == null
                    || rescueAttempts.merge(id, 1, Integer::sum) > 100) {
                rescueRequests.remove(id);
                rescueAttempts.remove(id);
                continue;
            }
            world.getChunk(pos.pos().getX() >> 4, pos.pos().getZ() >> 4);
            if (world.getEntity(id) instanceof com.ancientkira.mca.entity.VillagerEntityMCA person) {
                fulfillRescue(person);
            }
        }
    }

    public boolean fulfillRescue(com.ancientkira.mca.entity.VillagerEntityMCA person) {
        UUID playerId = rescueRequests.remove(person.getUUID());
        rescueAttempts.remove(person.getUUID());
        if (playerId == null || !(person.level() instanceof ServerLevel world)) return false;
        var player = world.getServer().getPlayerList().getPlayer(playerId);
        if (player == null) return false;
        person.rescueFromKidnapping();
        person.teleportTo(player.level(), player.getX(), player.getY(), player.getZ(),
                java.util.Set.of(), person.getYRot(), person.getXRot(), false);
        return true;
    }

    public GlobalPos get(UUID id) {
        return entries.get(id);
    }
}
