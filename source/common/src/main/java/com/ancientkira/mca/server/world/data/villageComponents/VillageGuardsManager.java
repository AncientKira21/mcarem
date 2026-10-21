package com.ancientkira.mca.server.world.data.villageComponents;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.entity.EquipmentSet;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.registry.ProfessionsMCA;
import com.ancientkira.mca.server.world.data.Village;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class VillageGuardsManager {
    private final Village village;
    private long lastSupplyCheck = Long.MIN_VALUE;
    private int supplyUnits;

    public VillageGuardsManager(Village village) {
        this.village = village;
    }

    public static EquipmentSet getGuardEquipmentForLevel(int level) {
        EquipmentSet fallback = switch (Math.clamp(level, 0, 2)) {
            case 2 -> EquipmentSet.GUARD_2;
            case 1 -> EquipmentSet.GUARD_1;
            default -> EquipmentSet.GUARD_0;
        };
        return getConfiguredEquipment(Config.getInstance().guardEquipment, level, fallback);
    }

    public static EquipmentSet getArcherEquipmentForLevel(int level) {
        EquipmentSet fallback = switch (Math.clamp(level, 0, 2)) {
            case 2 -> EquipmentSet.ARCHER_2;
            case 1 -> EquipmentSet.ARCHER_1;
            default -> EquipmentSet.ARCHER_0;
        };
        return getConfiguredEquipment(Config.getInstance().archerEquipment, level, fallback);
    }

    public void spawnGuards(ServerLevel world) {
        int guardCapacity = (int) Math.ceil(village.getPopulation() * Config.getInstance().guardSpawnFraction);

        // Count up the guards
        int guards = 0;
        int citizen = 0;
        List<VillagerEntityMCA> villagers = village.getResidents(world);
        List<VillagerEntityMCA> nonGuards = new LinkedList<>();
        for (VillagerEntityMCA villager : villagers) {
            if (villager.isGuard()) {
                guards++;
            } else {
                if (!villager.isBaby() && !villager.isProfessionImportant() && villager.getVillagerXp() == 0 && villager.getVillagerData().level() <= 1) {
                    nonGuards.add(villager);
                }
                citizen++;
            }
        }

        // Count all unloaded villagers against the guard limit
        // This is statistical and may not be accurate, but it's better than nothing
        guards += (int) Math.ceil((village.getPopulation() - guards - citizen) * Config.getInstance().guardSpawnFraction);

        // Spawn a new guard if we don't have enough
        if (!nonGuards.isEmpty() && guards < guardCapacity) {
            VillagerEntityMCA villager = nonGuards.get(world.getRandom().nextInt(nonGuards.size()));
            villager.setProfession(guards % 2 == 0 ? ProfessionsMCA.GUARD : ProfessionsMCA.ARCHER);
        }
    }

    public EquipmentSet getGuardEquipment(VillagerProfession profession) {
        int units = getSupplyUnits();
        boolean archer = profession == ProfessionsMCA.ARCHER;
        if (units >= 4) return archer ? EquipmentSet.ARCHER_NETHERITE : EquipmentSet.GUARD_NETHERITE;
        if (units >= 2) return archer ? EquipmentSet.ARCHER_DIAMOND : EquipmentSet.GUARD_DIAMOND;
        return archer ? getArcherEquipmentForLevel(units) : getGuardEquipmentForLevel(units);
    }

    public boolean suppliesEnchantedEquipment() { return getSupplyUnits() >= 10; }

    public int getSupplyUnits() {
        ServerLevel world = village.getWorld();
        if (world == null) return 0;
        long now = world.getGameTime();
        if (lastSupplyCheck != Long.MIN_VALUE && now >= lastSupplyCheck && now - lastSupplyCheck < 100) return supplyUnits;
        lastSupplyCheck = now;
        java.util.Map<Integer, Integer> buildingUnits = new java.util.HashMap<>();
        for (var building : village.getBuildings().values()) {
            if (java.util.Set.of("armory", "armorer", "weaponsmith", "blacksmith").contains(building.getType())) {
                buildingUnits.put(building.getStructureId(), 1);
            }
        }
        java.util.Map<Integer, java.util.Set<net.minecraft.core.BlockPos>> staffed = new java.util.HashMap<>();
        java.util.Set<net.minecraft.core.BlockPos> outdoorWorkplaces = new java.util.HashSet<>();
        // Contributions follow staffed workplaces, including children not yet registered as residents.
        for (var entity : world.getAllEntities()) {
            if (!(entity instanceof VillagerEntityMCA worker) || !worker.isAlive() || worker.isKidnapped()
                    || worker.getAgeState() == com.ancientkira.mca.entity.ai.relationship.AgeState.BABY) continue;
            var profession = worker.getVillagerData().profession();
            if (!profession.is(VillagerProfession.ARMORER) && !profession.is(VillagerProfession.WEAPONSMITH)
                    && !profession.is(VillagerProfession.TOOLSMITH)) continue;
            var workplace = worker.getBrain().getMemoryInternal(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
            // Young workers can have a profession before a saved JOB_SITE is established.
            // Count their actual nearby workplace, never their profession alone.
            if (workplace == null || workplace.isEmpty()) {
                workplace = world.getPoiManager().findClosest(
                        worker.getProfession().heldJobSite(),
                        pos -> village.isWithinBorder(pos, 0)
                                && Math.abs(pos.getY() - worker.blockPosition().getY()) <= 1,
                        worker.blockPosition(), 4,
                        net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
                        .map(pos -> net.minecraft.core.GlobalPos.of(world.dimension(), pos));
            }
            workplace.filter(site -> site.dimension().equals(world.dimension()))
                    .filter(site -> world.hasChunkAt(site.pos()))
                    .ifPresent(site -> {
                        var block = world.getBlockState(site.pos());
                        boolean matches = profession.is(VillagerProfession.ARMORER) && block.is(net.minecraft.world.level.block.Blocks.BLAST_FURNACE)
                                || profession.is(VillagerProfession.WEAPONSMITH) && block.is(net.minecraft.world.level.block.Blocks.GRINDSTONE)
                                || profession.is(VillagerProfession.TOOLSMITH) && block.is(net.minecraft.world.level.block.Blocks.SMITHING_TABLE);
                        if (matches) village.getBuildingAt(site.pos()).ifPresentOrElse(building ->
                                staffed.computeIfAbsent(building.getStructureId(), key -> new java.util.HashSet<>()).add(site.pos()),
                                () -> {
                                    if (village.isWithinBorder(site.pos(), 0)) outdoorWorkplaces.add(site.pos());
                                });
                    });
        }
        for (var entry : staffed.entrySet()) buildingUnits.merge(entry.getKey(), entry.getValue().size(), Math::max);
        supplyUnits = buildingUnits.values().stream().mapToInt(Integer::intValue).sum() + outdoorWorkplaces.size();
        return supplyUnits;
    }

    private static EquipmentSet getConfiguredEquipment(Map<String, EquipmentSet> config, int level, EquipmentSet fallback) {
        if (config == null) {
            return fallback;
        }

        return config.getOrDefault(Integer.toString(Math.clamp(level, 0, 2)), fallback);
    }
}
