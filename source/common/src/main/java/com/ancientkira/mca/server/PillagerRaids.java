package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.registry.ProfessionsMCA;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;

import java.util.*;

/** Keeps converted people in vanilla raid counts without retaining an invisible vanilla mob. */
public final class PillagerRaids {
    private static final Map<VillagerEntityMCA, Boolean> PEACEFUL_STATES = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private static final Map<VillagerEntityMCA, Integer> RAID_IDS = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private static final Set<VillagerEntityMCA> CAPTAINS = Collections.newSetFromMap(new com.google.common.collect.MapMaker().weakKeys().makeMap());
    private static final Map<Raid, Set<VillagerEntityMCA>> MEMBERS = new com.google.common.collect.MapMaker().weakKeys().makeMap();

    private PillagerRaids() { }

    public static void transfer(Raider original, VillagerEntityMCA person) {
        if (original.isCaptain()) CAPTAINS.add(person);
        Raid raid = original.getCurrentRaid();
        if (raid == null) return;
        ServerLevel world = (ServerLevel) original.level();
        world.getRaids().getId(raid).ifPresent(id -> RAID_IDS.put(person, id));
        MEMBERS.computeIfAbsent(raid, ignored -> Collections.newSetFromMap(new com.google.common.collect.MapMaker().weakKeys().makeMap())).add(person);
        // Preserve the wave's initial health denominator while replacing its live member.
        raid.removeFromRaid(world, original, false);
        if (original.isPatrolLeader()) raid.removeLeader(original.getWave());
        raid.updateBossbar();
    }

    public static int count(Raid raid) {
        return (int) members(raid).stream().filter(PillagerRaids::active).count();
    }

    public static float health(Raid raid) {
        return (float) members(raid).stream().filter(PillagerRaids::active).mapToDouble(VillagerEntityMCA::getHealth).sum();
    }

    private static Set<VillagerEntityMCA> members(Raid raid) {
        return MEMBERS.getOrDefault(raid, Set.of());
    }

    private static boolean active(VillagerEntityMCA person) {
        return person.isAlive() && !person.isRemoved() && person.isHostile() && ProfessionsMCA.isRaider(person.getProfession());
    }

    public static void update(Raid raid, ServerLevel world) {
        Set<VillagerEntityMCA> members = MEMBERS.get(raid);
        if (members == null) return;
        if (raid.isOver() || raid.isStopped()) {
            members.forEach(RAID_IDS::remove);
            MEMBERS.remove(raid);
            return;
        }
        members.removeIf(person -> {
            boolean remove = !active(person) || person.level() != world
                    || person.blockPosition().distSqr(raid.getCenter()) >= Raid.RAID_REMOVAL_THRESHOLD_SQR;
            if (remove) RAID_IDS.remove(person);
            return remove;
        });
    }

    public static void tick(VillagerEntityMCA person) {
        if (!ProfessionsMCA.isRaider(person.getProfession())) return;
        RaiderSocial.tickKidnapping(person);
        RaiderSocial.tickSelfRedemption(person);
        if (!ProfessionsMCA.isRaider(person.getProfession())) return;
        ServerLevel world = (ServerLevel) person.level();
        boolean peaceful = person.isPeacefulRaider();
        Boolean previous = PEACEFUL_STATES.put(person, peaceful);
        if (previous == null || previous != peaceful) {
            RaiderCombat.clear(person);
            person.stopUsingItem();
            person.setChargingCrossbow(false);
            person.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            person.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
            person.getBrain().eraseMemory(MemoryModuleType.NEAREST_HOSTILE);
            person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            person.getBrain().eraseMemory(MemoryModuleType.PATH);
            person.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            person.getBrain().eraseMemory(com.ancientkira.mca.entity.ai.MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY);
            person.getBrain().eraseMemory(com.ancientkira.mca.entity.ai.MemoryModuleTypeMCA.WEARS_ARMOR);
            if (peaceful) {
                person.setItemInHand(person.getDominantHand(), net.minecraft.world.item.ItemStack.EMPTY);
                person.setItemInHand(person.getOpposingHand(), net.minecraft.world.item.ItemStack.EMPTY);
                getRaid(person).ifPresent(raid -> {
                    Set<VillagerEntityMCA> members = MEMBERS.get(raid);
                    if (members != null) members.remove(person);
                    raid.updateBossbar();
                });
                RAID_IDS.remove(person);
            }
            person.refreshBrain(world);
        }
        if (peaceful) { RaiderSocial.updatePursuitSpeed(person); return; }
        RaiderSocial.updatePursuitSpeed(person);
        RaiderCombat.tick(person);
        Integer id = RAID_IDS.get(person);
        if (id == null) return;
        Raid raid = world.getRaids().get(id);
        if (raid == null || raid.isOver() || raid.isStopped()) { RAID_IDS.remove(person); return; }
        MEMBERS.computeIfAbsent(raid, ignored -> Collections.newSetFromMap(new com.google.common.collect.MapMaker().weakKeys().makeMap())).add(person);
        if (person.tickCount % 100 == 0) {
            raid.updateBossbar();
            if (!person.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                    && !person.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                person.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(raid.getCenter()), 0.65F, 8));
            }
        }
    }

    public static Optional<Raid> getRaid(VillagerEntityMCA person) {
        Integer id = RAID_IDS.get(person);
        return id == null || !(person.level() instanceof ServerLevel world)
                ? Optional.empty() : Optional.ofNullable(world.getRaids().get(id));
    }

    public static boolean belongsTo(Raid raid, LivingEntity entity) {
        return entity instanceof Raider raider && raider.getCurrentRaid() == raid
                || entity instanceof VillagerEntityMCA person && getRaid(person).orElse(null) == raid;
    }

    public static Optional<LivingEntity> findTarget(VillagerEntityMCA person) {
        if (person.isPeacefulRaider()) return Optional.empty();
        Optional<LivingEntity> attacker = person.getBrain().getMemoryInternal(MemoryModuleType.HURT_BY_ENTITY);
        if (attacker.isPresent() && (isJohnny(person) ? !isRaiderTarget(attacker.get()) : !RaiderSocial.isFamily(person, attacker.get()) && !person.isAlliedTo(attacker.get())) && attacker.get().isAlive()
                && !(attacker.get() instanceof VillagerEntityMCA captive && captive.isKidnapped())
                && person.canAttack(attacker.get()) && person.hasLineOfSight(attacker.get())
                && (!(attacker.get() instanceof Player player) || !player.isCreative() && !player.isSpectator() && !RaiderSocial.isMenuOpen(player))) {
            return attacker;
        }
        return person.getBrain().getMemoryInternal(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                .flatMap(visible -> {
                    Optional<LivingEntity> playerTarget = visible.find(target -> target instanceof Player && isTarget(person, target))
                            .min(Comparator.comparingDouble(person::distanceToSqr));
                    return playerTarget.isPresent() ? playerTarget : visible.find(target -> isTarget(person, target))
                            .min(Comparator.comparingDouble(person::distanceToSqr));
                });
    }

    public static boolean isJohnny(VillagerEntityMCA person) {
        return person.getProfession() == ProfessionsMCA.VINDICATOR && person.getName().getString().equals("Johnny");
    }

    public static boolean isRaiderTarget(LivingEntity target) {
        return target instanceof Raider
                || target instanceof VillagerEntityMCA person && ProfessionsMCA.isRaider(person.getProfession());
    }

    private static boolean isTarget(VillagerEntityMCA person, LivingEntity target) {
        if (!target.isAlive() || target == person || !person.canAttack(target)
                || target instanceof VillagerEntityMCA captive && captive.isKidnapped()) return false;
        if (target instanceof Player player && RaiderSocial.isMenuOpen(player)) return false;
        if (isJohnny(person)) {
            return !isRaiderTarget(target)
                    && (!(target instanceof Player player) || !player.isCreative() && !player.isSpectator() && !RaiderSocial.isFriendly(person, player));
        }
        if (person.isAlliedTo(target) || RaiderSocial.isFamily(person, target)) return false;
        if (target instanceof Player player) return RaiderSocial.isAngryAt(person, player);
        if (person.getProfession() == ProfessionsMCA.WITCH) return false;
        if (target instanceof VillagerEntityMCA villager) return !villager.isHostile();
        return target instanceof Villager || target instanceof IronGolem;
    }

    public static void onDeath(VillagerEntityMCA person, DamageSource source) {
        Integer id = RAID_IDS.remove(person);
        if (id == null || !(person.level() instanceof ServerLevel world)) return;
        Raid raid = world.getRaids().get(id);
        if (raid != null) {
            Set<VillagerEntityMCA> members = MEMBERS.get(raid);
            if (members != null) members.remove(person);
            if (source.getEntity() instanceof Player player) raid.addHeroOfTheVillage(player);
            raid.updateBossbar();
        }
    }

    public static void leaveProfession(VillagerEntityMCA person) {
        if (person.level() instanceof ServerLevel world) {
            world.getEntitiesOfClass(net.minecraft.world.entity.monster.Vex.class, person.getBoundingBox().inflate(64),
                    vex -> vex.getOwner() == person).forEach(net.minecraft.world.entity.Entity::discard);
            world.getEntitiesOfClass(net.minecraft.world.entity.projectile.EvokerFangs.class, person.getBoundingBox().inflate(64),
                    fangs -> fangs.getOwner() == person).forEach(net.minecraft.world.entity.Entity::discard);
        }
        getRaid(person).ifPresent(raid -> {
            Set<VillagerEntityMCA> members = MEMBERS.get(raid);
            if (members != null) members.remove(person);
            raid.updateBossbar();
        });
        RAID_IDS.remove(person);
        CAPTAINS.remove(person);
        PEACEFUL_STATES.remove(person);
        RaiderCombat.clear(person);
        person.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        person.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
        person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        person.getBrain().eraseMemory(MemoryModuleType.PATH);
        person.getBrain().eraseMemory(com.ancientkira.mca.entity.ai.MemoryModuleTypeMCA.WEARS_ARMOR);
        person.setItemInHand(person.getDominantHand(), net.minecraft.world.item.ItemStack.EMPTY);
        person.setItemInHand(person.getOpposingHand(), net.minecraft.world.item.ItemStack.EMPTY);
    }

    public static void dropCaptainLoot(VillagerEntityMCA person, ServerLevel world) {
        if (!CAPTAINS.remove(person)) return;
        var bottle = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OMINOUS_BOTTLE);
        bottle.set(net.minecraft.core.component.DataComponents.OMINOUS_BOTTLE_AMPLIFIER,
                new net.minecraft.world.item.component.OminousBottleAmplifier(person.getRandom().nextInt(5)));
        person.spawnAtLocation(world, bottle, 1.0F);
    }

    public static void write(VillagerEntityMCA person, CompoundTag data) {
        data.putBoolean("PillagerCaptain", CAPTAINS.contains(person));
        Integer id = RAID_IDS.get(person);
        if (id != null) data.putInt("PillagerRaid", id);
    }

    public static void read(VillagerEntityMCA person, CompoundTag data) {
        CAPTAINS.remove(person);
        if (data.getBooleanOr("PillagerCaptain", false)) CAPTAINS.add(person);
        RAID_IDS.remove(person);
        data.getInt("PillagerRaid").ifPresent(id -> {
            RAID_IDS.put(person, id);
            if (person.level() instanceof ServerLevel world) {
                Raid raid = world.getRaids().get(id);
                if (raid != null && !raid.isOver()) {
                    MEMBERS.computeIfAbsent(raid, ignored -> Collections.newSetFromMap(new com.google.common.collect.MapMaker().weakKeys().makeMap())).add(person);
                }
            }
        });
    }
}
