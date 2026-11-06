package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.brain.sensor.GuardEnemiesSensor;
import com.ancientkira.mca.registry.ProfessionsMCA;
import com.ancientkira.mca.server.world.data.Village;
import com.ancientkira.mca.server.world.data.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import com.ancientkira.mca.MCA;
import java.util.*;

/** Uses vanilla golem combat, with persistent faction/home tags and shared MCA targets. */
public final class GolemAllies {
    public static final String RAIDER = "mca_raider_golem";
    private static final String HOME = "mca_golem_home:";
    private GolemAllies() {}
    public static boolean raider(IronGolem golem) { return golem.entityTags().contains(RAIDER); }
    public static Optional<Village> home(IronGolem golem) {
        if (!(golem.level() instanceof ServerLevel world)) return Optional.empty();
        var manager = VillageManager.get(world);
        for (String tag : golem.entityTags()) if (tag.startsWith(HOME)) {
            try { var found = manager.getOrEmpty(Integer.parseInt(tag.substring(HOME.length()))); if (found.isPresent()) return found; }
            catch (NumberFormatException ignored) {}
        }
        var village = Village.findNearest(golem);
        if (village.isPresent()) {
            for (String tag : List.copyOf(golem.entityTags())) if (tag.startsWith(HOME)) golem.removeTag(tag);
            golem.addTag(HOME + village.get().getId());
        }
        return village;
    }
    public static boolean enemy(IronGolem golem, LivingEntity target) {
        if (GolemPolice.prisoner(target) || target instanceof VillagerEntityMCA person && VillageWarfare.knockedOut(person)) return false;
        if (target == golem || !target.isAlive() || target.level() != golem.level()
                || VillageWarfare.knockedOut(target) || VillageWarfare.knockedOut(golem)) return false;
        boolean corrupted=com.ancientkira.mca.compat.HostileGolemCompat.hostile(golem);
        if (target instanceof IronGolem other && (corrupted || com.ancientkira.mca.compat.HostileGolemCompat.hostile(other)))
            return corrupted != com.ancientkira.mca.compat.HostileGolemCompat.hostile(other);
        if (corrupted && target instanceof VillagerEntityMCA person) return !person.isKidnapped();
        var relation=VillageWarfare.combatRelation(golem,target);
        if(relation.isPresent())return relation.get();
        if (raider(golem) && golem.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return false;
        if (target instanceof VillagerEntityMCA person) {
            if (person.isKidnapped()) return false;
            if (raider(golem)) return !ProfessionsMCA.isRaider(person.getProfession());
            return person.isHostile() || person.isGuard() && home(golem)
                    .map(v -> VillageWarfare.isEnemy(v.getId(), person)).orElse(false);
        }
        if (target instanceof IronGolem other) {
            if (raider(golem) != raider(other)) return true;
            if (raider(golem)) return false;
            return home(golem).flatMap(a -> home(other).map(b -> VillageWarfare.areEnemies((ServerLevel) golem.level(), a.getId(), b.getId()))).orElse(false);
        }
        return false;
    }
    public static boolean enemyOfGuard(IronGolem golem, VillagerEntityMCA guard) {
        if (VillageWarfare.knockedOut(golem) || VillageWarfare.knockedOut(guard)) return false;
        if (com.ancientkira.mca.compat.HostileGolemCompat.hostile(golem)) return true;
        var relation=VillageWarfare.combatRelation(golem,guard);
        if(relation.isPresent())return relation.get();
        return raider(golem) || home(golem).map(v -> VillageWarfare.isEnemy(v.getId(), guard)).orElse(false);
    }
    private static void updateCombatSpeed(IronGolem golem, boolean attacking) {
        var speed = golem.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        var id = MCA.locate("golem_combat_speed");
        speed.removeModifier(id);
        if (!attacking) return;
        double guardSpeed = .375;
        for (var guard : golem.level().getEntitiesOfClass(VillagerEntityMCA.class, golem.getBoundingBox().inflate(48),
                p -> p.isAlive() && p.isGuard() && !enemyOfGuard(golem, p))) {
            guardSpeed = Math.max(guardSpeed, guard.getAttributeValue(Attributes.MOVEMENT_SPEED) * .75);
        }
        double bonus = guardSpeed - speed.getValue();
        if (bonus > 0) speed.addTransientModifier(new AttributeModifier(id, bonus, AttributeModifier.Operation.ADD_VALUE));
    }
    public static void tick(IronGolem golem) {
        if (!(golem.level() instanceof ServerLevel world) || golem.isNoAi()) return;
        if (VillageWarfare.knockedOut(golem)) return;
        if (golem.getTarget()!=null && VillageWarfare.combatRelation(golem,golem.getTarget()).filter(enemy->!enemy).isPresent()) {
            golem.setTarget(null);golem.stopBeingAngry();golem.setLastHurtByMob(null);
        }
        if (!com.ancientkira.mca.compat.HostileGolemCompat.hostile(golem) && GolemPolice.transport(golem)) return;
        if (golem.getTarget() == null || !golem.getTarget().isAlive()) updateCombatSpeed(golem, false);
        if (!raider(golem) && !com.ancientkira.mca.compat.HostileGolemCompat.hostile(golem) && golem.getTarget() != null && golem.getTarget().isAlive()
                && golem.distanceToSqr(golem.getTarget()) <= 9.0 && GolemPolice.arrestable(golem.getTarget())) {
            if (GolemPolice.arrest(golem, golem.getTarget())) return;
        }
        if (golem.tickCount % 10 != 0) return;
        updateCombatSpeed(golem, false);
        if (raider(golem) && world.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            golem.setTarget(null); golem.stopBeingAngry(); return;
        }
        var village = raider(golem) || com.ancientkira.mca.compat.HostileGolemCompat.hostile(golem) ? Optional.<Village>empty() : home(golem);
        LivingEntity target = golem.getTarget();
        if (target instanceof VillagerEntityMCA || target instanceof IronGolem) {
            if (!enemy(golem, target)) { golem.setTarget(null); golem.stopBeingAngry(); target = null; }
        }
        if (target != null && (!target.isAlive() || !raider(golem)
                && village.isPresent() && !VillageWarfare.hasWar(world, village.get().getId())
                && !village.get().isWithinBorder(target.blockPosition(), 16))) {
            golem.setTarget(null); golem.stopBeingAngry(); target = null;
        }
        if (target == null) {
            target=VillageWarfare.nearestOpponent(golem).orElse(null);
        }
        if (target == null) {
            var candidates = new ArrayList<LivingEntity>();
            Iterable<? extends net.minecraft.world.entity.Entity> nearby = world.getEntitiesOfClass(LivingEntity.class, golem.getBoundingBox().inflate(48));
            for (var entity : nearby) {
                if (entity instanceof LivingEntity living && enemy(golem, living)
                        && (golem.distanceToSqr(living) <= 48 * 48
                        || living instanceof VillagerEntityMCA person && village.isPresent()
                        && VillageWarfare.isEnemy(village.get().getId(), person))) candidates.add(living);
            }
            for (var ally : world.getEntitiesOfClass(VillagerEntityMCA.class, golem.getBoundingBox().inflate(48), p -> p.isAlive() && !p.isKidnapped()
                    && (raider(golem) ? ProfessionsMCA.isRaider(p.getProfession()) : p.isGuard()
                    && !home(golem).map(v -> VillageWarfare.isEnemy(v.getId(), p)).orElse(false)))) {
                LivingEntity shared = ally.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).orElse(ally.getTarget());
                if (shared == null || !shared.isAlive() || shared == golem || shared.level() != world
                        || !com.ancientkira.mca.entity.ai.GuardPerception.canLocate(ally,shared)) continue;
                if (enemy(golem, shared) || !(shared instanceof VillagerEntityMCA) && !(shared instanceof IronGolem)
                        && (raider(golem) || GuardEnemiesSensor.isGuardEnemy(shared, ally)
                            || shared instanceof net.minecraft.world.entity.player.Player player && !player.isCreative() && !player.isSpectator())) candidates.add(shared);
            }
            target = candidates.stream().min(Comparator.comparingDouble(golem::distanceToSqr)).orElse(null);
            if (target != null && !raider(golem) && village.isPresent() && !VillageWarfare.hasWar(world, village.get().getId())
                    && !village.get().isWithinBorder(target.blockPosition(), 16)) target = null;
            golem.setTarget(target);
        }
        if (target != null) {
            if (golem.getTarget()!=target) golem.setTarget(target);
            updateCombatSpeed(golem, true);
            golem.getLookControl().setLookAt(target, 30, 30);
            if (golem.getNavigation().isDone() || golem.getNavigation().getTargetPos() == null
                    || golem.getNavigation().getTargetPos().distSqr(target.blockPosition()) > 16)
                golem.getNavigation().moveTo(target, 1.0);
        } else if (village.isPresent() && !village.get().isWithinBorder(golem.blockPosition(), 4)) {
            var center = village.get().getCenter();
            golem.getNavigation().moveTo(center.getX() + .5, center.getY(), center.getZ() + .5, .75);
        }
    }
}
