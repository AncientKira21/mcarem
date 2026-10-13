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
    private AutomaticVillageConflicts(ServerLevel world) { this.world = world; }
    private AutomaticVillageConflicts(ServerLevel world, CompoundTag tag) {
        this(world);
        for (var entry : tag.getListOrEmpty("Villages")) if (entry instanceof CompoundTag data) {
            int id = data.getIntOr("Id", -1);
            decisions.put(id, data.getLongOr("NextDecision", 0));
            if (data.contains("RaidStarts")) raids.put(id, data.getLongOr("RaidStarts", 0));
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
            list.add(data);
        });
        tag.put("Villages", list); return tag;
    }
    public void cancelRaidWarning(int village) {
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
    public void tick() {
        long now = world.getGameTime();
        if (now % 20 != 0) return;
        var manager = VillageManager.get(world);
        for (var warning : List.copyOf(raids.entrySet())) {
            Village village = manager.getOrEmpty(warning.getKey()).orElse(null);
            if (village == null || !Config.getInstance().enableAutomaticVillageRaids || hasMayor(village)
                    || world.getDifficulty() == Difficulty.PEACEFUL) { raids.remove(warning.getKey()); setDirty(); continue; }
            if (now < warning.getValue()) continue;
            var players = audience(village);
            if (players.isEmpty()) continue;
            ServerPlayer trigger = players.stream().min(Comparator.comparingDouble(p -> p.blockPosition().distSqr(village.getCenter()))).orElseThrow();
            var raid = world.getRaids().createOrExtendRaid(trigger, center(village));
            if (raid != null) {
                raid.setRaidOmenLevel(Math.max(1, raid.getRaidOmenLevel()));
                ((com.ancientkira.mca.mixin.RaidCountdownAccessor) (Object) raid).mca$setRaidCooldownTicks(0);
                world.getRaids().setDirty();
                Component message = Component.translatable("mca.conflict.raid_started", village.getName());
                village.getCivilRegistry().ifPresent(registry -> registry.addText(message));
                players.forEach(player -> player.sendSystemMessage(message));
            }
            raids.remove(warning.getKey()); setDirty();
        }
        Config config = Config.getInstance();
        for (var village : manager.findVillages(v -> v.getPopulation() >= 3).toList()) {
            if (!decisions.containsKey(village.getId())) { decisions.put(village.getId(), now + DAY); setDirty(); }
            if (now < decisions.get(village.getId())) continue;
            decisions.put(village.getId(), now + DAY); setDirty();
            var recipients = audience(village);
            if (recipients.isEmpty() || hasMayor(village) || raids.containsKey(village.getId())
                    || VillageWarfare.get(world).isBusy(village.getId())) continue;
            if (config.enableAutomaticVillageWars && hasDefenders(village) && world.getRandom().nextDouble() < config.automaticVillageWarChancePerDay) {
                var opponents = manager.findVillages(other -> other != village && other.getPopulation() >= 3 && hasDefenders(other)
                        && !raids.containsKey(other.getId()) && !VillageWarfare.get(world).isBusy(other.getId())
                        && other.getCenter().distSqr(village.getCenter()) <= VillageWarfare.NEARBY_RANGE * VillageWarfare.NEARBY_RANGE).toList();
                if (!opponents.isEmpty()) {
                    Village other = opponents.get(world.getRandom().nextInt(opponents.size()));
                    if (VillageWarfare.get(world).declare(village, other.getId(), 3)) {
                        decisions.put(village.getId(), now + 3 * DAY); decisions.put(other.getId(), now + 3 * DAY);
                        var receivers = new HashSet<>(recipients); receivers.addAll(audience(other));
                        receivers.forEach(player -> PlayerSaveData.get(player).sendLetter(
                                Component.translatable("mca.letter.war_warning", village.getName(), other.getName())));
                        continue;
                    }
                }
            }
            if (config.enableAutomaticVillageRaids && world.getDifficulty() != Difficulty.PEACEFUL
                    && world.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.RAIDS)
                    && world.getRaids().getNearbyRaid(center(village), 128 * 128) == null
                    && world.getRandom().nextDouble() < config.automaticVillageRaidChancePerDay) {
                raids.put(village.getId(), now + 600); decisions.put(village.getId(), now + 3 * DAY);
                recipients.forEach(player -> PlayerSaveData.get(player).sendLetter(
                        Component.translatable("mca.letter.raid_warning", village.getName())));
                village.getCivilRegistry().ifPresent(registry -> registry.addText(
                        Component.translatable("mca.conflict.raid_warning", village.getName())));
            }
        }
    }
}
