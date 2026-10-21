package com.ancientkira.mca.server;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.resources.Rank;
import com.ancientkira.mca.resources.Tasks;
import com.ancientkira.mca.server.world.data.*;
import com.ancientkira.mca.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Daily village decisions and saved raid preparation deadlines. */
public final class AutomaticVillageConflicts extends SavedData {
    private static final long DAY = 24000L;
    private final ServerLevel world;
    private final Map<Integer, Long> decisions = new HashMap<>();
    private final Map<Integer, Long> raids = new HashMap<>();
    private final Map<Integer, Integer> raidIds = new HashMap<>();
    private final Map<Integer, Long> raidRetryAfter = new HashMap<>();
    private AutomaticVillageConflicts(ServerLevel world) { this.world = world; }
    private AutomaticVillageConflicts(ServerLevel world, CompoundTag tag) {
        this(world);
        for (var entry : tag.getListOrEmpty("Villages")) if (entry instanceof CompoundTag data) {
            int id = data.getIntOr("Id", -1);
            decisions.put(id, data.getLongOr("NextDecision", 0));
            if (data.contains("RaidStarts")) raids.put(id, data.getLongOr("RaidStarts", 0));
            if (data.contains("RaidId")) raidIds.put(id, data.getIntOr("RaidId", -1));
        }
    }
    public static AutomaticVillageConflicts get(ServerLevel world) {
        return WorldUtils.loadData(world, (tag, registries) -> new AutomaticVillageConflicts(world, tag),
                AutomaticVillageConflicts::new, "mca_automatic_conflicts");
    }
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        decisions.forEach((id, deadline) -> {
            CompoundTag data = new CompoundTag(); data.putInt("Id", id); data.putLong("NextDecision", deadline);
            if (raids.containsKey(id)) data.putLong("RaidStarts", raids.get(id));
            if (raidIds.containsKey(id)) data.putInt("RaidId", raidIds.get(id));
            list.add(data);
        });
        tag.put("Villages", list); return tag;
    }
    public void cancelRaidWarning(int village) {
        raidIds.remove(village);
        raidRetryAfter.remove(village);
        if (raids.remove(village) != null) setDirty();
    }
    public static boolean preventsAutomaticConflict(Rank rank) { return rank == Rank.MAYOR; }
    private boolean hasMayor(Village village) {
        return world.players().stream().anyMatch(player -> preventsAutomaticConflict(Tasks.getRank(village, player)));
    }
    private boolean hasDefenders(Village village) {
        var family = FamilyTree.get(world);
        return village.getResidentsUUIDs().anyMatch(id -> family.getOrEmpty(id).filter(node -> !node.isDeceased()
                && (node.getProfession() == com.ancientkira.mca.registry.ProfessionsMCA.GUARD
                || node.getProfession() == com.ancientkira.mca.registry.ProfessionsMCA.ARCHER)).isPresent());
    }
    private List<ServerPlayer> audience(Village village) {
        return world.players().stream().filter(player -> !player.isSpectator()
                && player.blockPosition().distSqr(village.getCenter()) <= VillageWarfare.NEARBY_RANGE * VillageWarfare.NEARBY_RANGE).toList();
    }
    private static BlockPos center(Village village) {
        var pos = village.getCenter(); return new BlockPos(pos.getX(), pos.getY(), pos.getZ());
    }
    private void announceRaid(Village village, String translationKey) {
        Component message = Component.translatable(translationKey, village.getName());
        village.getCivilRegistry().ifPresent(registry -> registry.addText(message));
        world.getServer().getPlayerList().broadcastSystemMessage(message, false);
    }

    public void tick() {
        long now = world.getGameTime();
        if (now % 20 != 0) return;
        var manager = VillageManager.get(world);
        var diplomacy = VillageDiplomacy.get(world);
        diplomacy.tick();
        for (var warning : List.copyOf(raids.entrySet())) {
            int villageId = warning.getKey();
            Village village = manager.getOrEmpty(villageId).orElse(null);
            var raid = raidIds.containsKey(villageId) ? world.getRaids().get(raidIds.get(villageId)) : null;
            if (village == null || !Config.getInstance().enableAutomaticVillageRaids || hasMayor(village)
                    || world.getDifficulty() == Difficulty.PEACEFUL
                    || !world.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.RAIDS)) {
                if (raid != null && !raid.hasFirstWaveSpawned()) raid.stop();
                cancelRaidWarning(villageId);
                continue;
            }
            if (raid != null && !raid.isStopped() && !raid.isOver()) {
                if (raid.hasFirstWaveSpawned()) {
                    announceRaid(village, "mca.conflict.raid_started");
                    cancelRaidWarning(villageId);
                }
                continue;
            }
            if (now < raidRetryAfter.getOrDefault(villageId, 0L)) continue;
            var trigger = audience(village).stream()
                    .filter(player -> village.getBuildings().values().stream().anyMatch(building ->
                            building.getSourceBlock().distSqr(player.blockPosition()) <= 96 * 96))
                    .min(Comparator.comparingDouble(player -> player.blockPosition().distSqr(village.getCenter()))).orElse(null);
            if (trigger == null) continue;
            int preparation = (int) Math.min(600L, Math.max(300L, warning.getValue() - now));
            raid = RaidStarter.start(trigger, village, preparation);
            if (raid != null) {
                world.getRaids().getId(raid).ifPresent(id -> raidIds.put(villageId, id));
                raidRetryAfter.remove(villageId);
                setDirty();
            } else {
                // Keep the warning instead of silently deleting the pending raid.
                raidRetryAfter.put(villageId, now + 200);
            }
        }
        Config config = Config.getInstance();
        for (var village : manager.findVillages(v -> v.getPopulation() >= 3).toList()) {
            if (!decisions.containsKey(village.getId())) { decisions.put(village.getId(), now + DAY); setDirty(); }
            if (now < decisions.get(village.getId())) continue;
            var recipients = audience(village);
            if (recipients.isEmpty() || hasMayor(village) || raids.containsKey(village.getId())
                    || VillageWarfare.get(world).isBusy(village.getId()) || diplomacy.hasPending(village.getId())) continue;
            decisions.put(village.getId(), now + DAY); setDirty();
            if (config.enableAutomaticVillageWars && hasDefenders(village) && world.getRandom().nextDouble() < config.automaticVillageWarChancePerDay) {
                var opponents = manager.findVillages(other -> other != village && other.getPopulation() >= 3 && hasDefenders(other)
                        && !raids.containsKey(other.getId()) && !VillageWarfare.get(world).isBusy(other.getId())
                        && !diplomacy.hasPending(other.getId())
                        && !VillageWarfare.get(world).isFriendly(village.getId(), other.getId())
                        && other.getCenter().distSqr(village.getCenter()) <= VillageWarfare.NEARBY_RANGE * VillageWarfare.NEARBY_RANGE).toList();
                if (!opponents.isEmpty()) {
                    Village other = opponents.get(world.getRandom().nextInt(opponents.size()));
                    if (diplomacy.requestWar(village, other)) {
                        decisions.put(village.getId(), now + 3 * DAY); decisions.put(other.getId(), now + 3 * DAY);
                        continue;
                    }
                }
            }
            if (config.enableAutomaticVillageRaids && world.getDifficulty() != Difficulty.PEACEFUL
                    && world.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.RAIDS)
                    && world.getRaids().getNearbyRaid(center(village), 128 * 128) == null
                    && world.getRandom().nextDouble() < config.automaticVillageRaidChancePerDay) {
                raids.put(village.getId(), now + 600); decisions.put(village.getId(), now + 3 * DAY);
                var trigger = recipients.stream().filter(player -> village.getBuildings().values().stream().anyMatch(building ->
                        building.getSourceBlock().distSqr(player.blockPosition()) <= 96 * 96)).findFirst().orElse(null);
                if (trigger != null) {
                    var prepared = RaidStarter.start(trigger, village, 600);
                    if (prepared != null) world.getRaids().getId(prepared).ifPresent(id -> raidIds.put(village.getId(), id));
                }
                setDirty();
                recipients.forEach(player -> PlayerSaveData.get(player).sendLetter(
                        Component.translatable("mca.letter.raid_warning", village.getName())));
                announceRaid(village, "mca.conflict.raid_warning");
            }
        }
    }
}
