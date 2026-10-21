package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.level.block.state.BlockState;

/** Preserve neutral-mob rules while allowing real retaliation and village defence. */
public final class NeutralMobRules {
    private NeutralMobRules() { }

    public static boolean passiveUntilProvoked(LivingEntity mob) {
        return mob.is(EntityType.ENDERMAN) || mob.is(EntityType.ZOMBIFIED_PIGLIN);
    }

    public static boolean wearingGold(LivingEntity person) {
        return PiglinAi.isWearingSafeArmor(person);
    }

    public static boolean provokedBy(Mob mob, LivingEntity person) {
        var anger = mob.getBrain().getMemoryInternal(MemoryModuleType.ANGRY_AT);
        return mob.getLastHurtByMob() == person && mob.tickCount - mob.getLastHurtByMobTimestamp() <= 600
                || anger != null && anger.filter(person.getUUID()::equals).isPresent();
    }

    public static boolean mayHuntVillager(Mob mob, LivingEntity person) {
        if (passiveUntilProvoked(mob)) return false;
        return !(mob instanceof Piglin piglin) || !piglin.isBaby()
                && (!wearingGold(person) || provokedBy(mob, person));
    }

    public static boolean threatensVillager(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) return false;
        LivingEntity target = mob.getTarget();
        if (target == null) {
            // Goal-based mobs may not register brain attack memories at all.
            var rememberedTarget = mob.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
            if (rememberedTarget != null) target = rememberedTarget.orElse(null);
        }
        return target instanceof VillagerEntityMCA && target.isAlive();
    }

    public static void onBlockMined(ServerLevel world, VillagerEntityMCA miner, BlockPos pos, BlockState state) {
        if (!state.is(BlockTags.GUARDED_BY_PIGLINS)) return;
        for (Piglin piglin : world.getEntitiesOfClass(Piglin.class, miner.getBoundingBox().inflate(16),
                piglin -> piglin.isAlive() && !piglin.isBaby() && piglin.hasLineOfSight(miner)
                        && piglin.distanceToSqr(miner) <= 16 * 16)) {
            piglin.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, miner.getUUID(), 600L);
            piglin.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, miner);
            piglin.setTarget(miner);
        }
    }
}
