package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.navigation.TerrainSafety;
import com.ancientkira.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Temporary hospital beds do not replace the patient's home or workplace. */
public final class InfirmaryCare {
    private static final class Stay {
        final BlockPos bed; final long started; long lastTick = Long.MIN_VALUE;
        Stay(BlockPos bed, long started) { this.bed = bed; this.started = started; }
    }
    private static final Map<VillagerEntityMCA, Stay> PATIENTS = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private InfirmaryCare() { }
    public static boolean isPatient(VillagerEntityMCA person) { return PATIENTS.containsKey(person); }
    private static boolean sick(VillagerEntityMCA person) {
        return person.isInfected() || person.hasEffect(MobEffects.WITHER);
    }
    public static boolean emergency(VillagerEntityMCA person) {
        return person.isAlive() && person.getHealth() <= 1 && !person.isKidnapped()
                && !person.isNoAi() && !ProfessionsMCA.isRaider(person.getProfession());
    }
    public static void tick(VillagerEntityMCA person) {
        if (!(person.level() instanceof ServerLevel world)) return;
        Stay stay = PATIENTS.get(person);
        if (!person.isAlive() || person.isRemoved() || person.isKidnapped() || person.isNoAi()
                || ProfessionsMCA.isRaider(person.getProfession())) {
            if (stay != null) discharge(person, false);
            return;
        }
        if (stay == null) {
            if (!emergency(person) && person.tickCount % 40 != 0 || (!sick(person) && person.getHealth() > person.getMaxHealth() * .25F)
                    || !emergency(person) && person.getInteractions().getInteractingPlayer().isPresent()) return;
            var villages = emergency(person)
                    ? com.ancientkira.mca.server.world.data.VillageManager.get(world).findVillages(v -> true)
                    : person.getResidency().getHomeVillage().stream();
            Optional<BlockPos> bed = villages.flatMap(v -> v.getBuildingsOfType("infirmary"))
                    .flatMap(building -> building.getBlockPosStream())
                    .filter(pos -> world.hasChunkAt(pos))
                    .filter(pos -> world.getBlockState(pos).getBlock() instanceof BedBlock
                            && world.getBlockState(pos).getValue(BedBlock.PART) == BedPart.HEAD
                            && !world.getBlockState(pos).getValue(BedBlock.OCCUPIED))
                    .filter(pos -> world.getFluidState(pos).isEmpty()
                            && world.getBlockState(pos.above()).getCollisionShape(world, pos.above()).isEmpty())
                    .filter(pos -> PATIENTS.entrySet().stream().noneMatch(e -> e.getKey().isAlive()
                            && e.getKey().level() == world && e.getValue().bed.equals(pos)))
                    .sorted(Comparator.comparingDouble(pos -> person.distanceToSqr(Vec3.atCenterOf(pos))))
                    .filter(pos -> { if (emergency(person)) return true; var path = person.getNavigation().createPath(pos, 1); return path != null && path.canReach(); })
                    .findFirst();
            if (bed.isEmpty()) return;
            stay = new Stay(bed.get().immutable(), world.getGameTime());
            PATIENTS.put(person, stay);
            person.getVillagerBrain().abandonJob();
            if (emergency(person)) {
                person.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                person.setTarget(null);
                person.getNavigation().stop();
                person.stopRiding();
                person.setDeltaMovement(Vec3.ZERO);
                person.setRemainingFireTicks(0);
                person.fallDistance = 0;
                person.teleportTo(stay.bed.getX() + .5, stay.bed.getY() + .5625, stay.bed.getZ() + .5);
                person.startSleeping(stay.bed);
            }
        }
        if (emergency(person) && !person.isSleeping()) {
            person.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            person.setTarget(null);
            person.getNavigation().stop();
            person.setDeltaMovement(Vec3.ZERO);
            person.teleportTo(stay.bed.getX() + .5, stay.bed.getY() + .5625, stay.bed.getZ() + .5);
            person.startSleeping(stay.bed);
        }
        if (stay.lastTick == world.getGameTime()) return;
        stay.lastTick = world.getGameTime();
        if (!(world.getBlockState(stay.bed).getBlock() instanceof BedBlock)
                || world.getGameTime() - stay.started > 12000
                || !sick(person) && person.getHealth() >= person.getMaxHealth() * .85F) {
            discharge(person, true);
            return;
        }
        person.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        if (person.distanceToSqr(Vec3.atCenterOf(stay.bed)) > 4) {
            if (person.isSleeping()) person.stopSleeping();
            person.setRestingInInfirmary(false);
            person.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(stay.bed, .65F, 1));
            return;
        }
        person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        person.getNavigation().stop();
        person.setXxa(0); person.setZza(0);
        person.setDeltaMovement(0, person.getDeltaMovement().y, 0);
        if (person.getHealth() < person.getMaxHealth() * .85F) {
            person.setRestingInInfirmary(false);
            if (!person.isSleeping()) person.startSleeping(stay.bed);
            if (person.tickCount % 40 == 0) person.heal(1);
        } else {
            if (person.isSleeping()) person.stopSleeping();
            person.setPos(stay.bed.getX() + .5, stay.bed.getY() + .5625, stay.bed.getZ() + .5);
            person.setRestingInInfirmary(true);
        }
    }
    private static void discharge(VillagerEntityMCA person, boolean leave) {
        Stay stay = PATIENTS.remove(person);
        person.setRestingInInfirmary(false);
        if (person.isSleeping()) person.stopSleeping();
        person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        person.getNavigation().stop();
        if (leave && stay != null) {
            var building = person.getResidency().getHomeVillage().flatMap(v -> v.getBuildingAt(stay.bed));
            for (BlockPos pos : BlockPos.betweenClosed(stay.bed.offset(-12, -1, -12), stay.bed.offset(12, 1, 12))) {
                if (building.filter(b -> b.containsPos(pos)).isPresent() || !TerrainSafety.solidFloor(person.level(), pos)
                        || !TerrainSafety.clear(person.level(), pos) || TerrainSafety.dangerousEdge(person.level(), pos)) continue;
                var path = person.getNavigation().createPath(pos, 0);
                if (path != null && path.canReach()) {
                    person.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos.immutable(), .65F, 1));
                    break;
                }
            }
        }
        person.getBrain().updateActivityFromSchedule(person.level().environmentAttributes(), person.level().getGameTime(), person.position());
    }
    public static final class CareActivity extends net.minecraft.world.entity.ai.behavior.Behavior<VillagerEntityMCA> {
        public CareActivity() { super(com.google.common.collect.ImmutableMap.of(), 1200); }
        @Override protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA p) { return isPatient(p); }
        @Override protected boolean canStillUse(ServerLevel world, VillagerEntityMCA p, long time) { return isPatient(p); }
        @Override protected void start(ServerLevel world, VillagerEntityMCA p, long time) { InfirmaryCare.tick(p); }
        @Override protected void tick(ServerLevel world, VillagerEntityMCA p, long time) { InfirmaryCare.tick(p); }
    }
}
