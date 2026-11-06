package com.ancientkira.mca.server;

import com.ancientkira.mca.compat.HostileGolemCompat;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.relationship.AgeState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.schedule.Activity;

/** A spoken call for help alerts existing local defenders to the addressed player. */
public final class VillageHelp {
    private VillageHelp() {}

    public static void call(VillagerEntityMCA caller, ServerPlayer offender) {
        if (!(caller.level() instanceof ServerLevel world) || offender.level() != world
                || !caller.isAlive() || caller.isKidnapped() || caller.isToYoungToSpeak()
                || caller.isHostile() || !offender.isAlive() || offender.isCreative() || offender.isSpectator()
                || caller.distanceToSqr(offender) > 32 * 32
                || caller.getVillagerBrain().getMemoriesForPlayer(offender).getHearts() >= 0
                || GolemPolice.prisoner(offender)) return;

        for (Mob defender : world.getEntitiesOfClass(Mob.class, caller.getBoundingBox().inflate(48))) {
            if (!defender.isAlive() || defender.isNoAi() || defender.isSleeping() || !defender.canAttack(offender)
                    || VillageWarfare.knockedOut(defender)
                    || VillageWarfare.combatRelation(defender, offender).filter(enemy -> !enemy).isPresent()) continue;

            if (defender instanceof VillagerEntityMCA guard) {
                if (!guard.isGuard() || guard.isHostile() || guard.isKidnapped()
                        || guard.getAgeState() == AgeState.BABY || RaiderSocial.isFamily(guard, offender)
                        || guard.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) continue;
                guard.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_TARGET, offender, 600);
                guard.getBrain().setActiveActivityIfPossible(Activity.FIGHT);
                guard.setTarget(offender);
                guard.getLookControl().setLookAt(offender, 30, 30);
            } else if (defender instanceof IronGolem golem) {
                if (GolemAllies.raider(golem) || HostileGolemCompat.hostile(golem) || golem.getTarget() != null) continue;
                golem.setTarget(offender);
                golem.getNavigation().moveTo(offender, 1.0);
            }
        }
    }
}
