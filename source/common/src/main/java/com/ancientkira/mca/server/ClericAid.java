package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.registry.ProfessionsMCA;
import com.ancientkira.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.alchemy.Potions;

import java.util.Comparator;
import java.util.Map;

/** Temporary rescue assignments leave normal cleric work and residence memories intact. */
public final class ClericAid {
    private static final Map<VillagerEntityMCA, Assignment> ASSIGNMENTS = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private static final Map<VillagerEntityMCA, Long> NEXT_CALL = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private static final int HELP_RADIUS = 128;
    private static final int ASSIGNMENT_TIMEOUT = 20 * 180;
    private ClericAid() { }

    public static void onCaptured(VillagerEntityMCA person) {
        for (var entry : java.util.List.copyOf(ASSIGNMENTS.entrySet())) {
            if (entry.getKey() == person || entry.getValue().patient == person) finish(entry.getKey());
        }
        NEXT_CALL.remove(person);
        person.getBrain().eraseMemory(MemoryModuleTypeMCA.RECOVERING_FROM_CAPTIVITY);
    }

    public static void onRescued(VillagerEntityMCA person) {
        onCaptured(person);
        person.getBrain().setMemory(MemoryModuleTypeMCA.RECOVERING_FROM_CAPTIVITY, true);
    }

    public static boolean isHelping(VillagerEntityMCA person) { return InfirmaryCare.isPatient(person) || ASSIGNMENTS.containsKey(person)
            || ASSIGNMENTS.values().stream().anyMatch(a -> a.patient == person && a.seekingCure); }

    public static void tick(VillagerEntityMCA person) {
        if (!(person.level() instanceof ServerLevel world) || !person.isAlive()) return;
        long now = world.getGameTime();
        if (!person.isInfected() && !person.hasEffect(MobEffects.WITHER) && person.getHealth() >= person.getMaxHealth() * 0.85F && !person.isOnFire()) {
            person.getBrain().eraseMemory(MemoryModuleTypeMCA.RECOVERING_FROM_CAPTIVITY);
        }
        if (person.tickCount % 20 == 0) {
            for (var entry : java.util.List.copyOf(ASSIGNMENTS.entrySet())) {
                VillagerEntityMCA cleric = entry.getKey();
                Assignment aid = entry.getValue();
                if (!cleric.isAlive() || cleric.isRemoved() || cleric.isKidnapped() || cleric.isNoAi()
                        || !aid.patient.isAlive() || aid.patient.isRemoved() || aid.patient.isKidnapped()
                        || cleric.level() != aid.patient.level() || cleric.level().getGameTime() >= aid.expires) {
                    finish(cleric);
                }
            }
        }
        if (!ProfessionsMCA.isRaider(person.getProfession()) && !person.isKidnapped() && !person.isNoAi()
                && (person.isInfected() || person.hasEffect(MobEffects.WITHER) || person.getHealth() <= person.getMaxHealth() * 0.25F
                    || person.getBrain().hasMemoryValue(MemoryModuleTypeMCA.RECOVERING_FROM_CAPTIVITY))
                && person.tickCount % 20 == 0
                && ASSIGNMENTS.values().stream().noneMatch(a -> a.patient == person)) {
            if (now >= NEXT_CALL.getOrDefault(person, 0L)) {
                NEXT_CALL.put(person, now + 200);
                person.sendChatToAllAround("villager.cleric_help");
            }
            world.getEntitiesOfClass(VillagerEntityMCA.class, person.getBoundingBox().inflate(HELP_RADIUS),
                    cleric -> cleric != person && cleric.isAlive() && !cleric.isNoAi() && !cleric.isKidnapped() && cleric.getHealth() > cleric.getMaxHealth() * 0.25F
                            && cleric.getVillagerData().profession().is(VillagerProfession.CLERIC)
                            && !ASSIGNMENTS.containsKey(cleric) && !MedicalInsurance.isHelping(cleric) && cleric.distanceToSqr(person) <= HELP_RADIUS * HELP_RADIUS
                            && cleric.getInteractions().getInteractingPlayer().isEmpty())
                    .stream().min(Comparator.comparingDouble(person::distanceToSqr)).ifPresent(cleric -> {
                        ASSIGNMENTS.put(cleric, new Assignment(person, now));
                        cleric.sendChatToAllAround("villager.cleric_response");
                    });
        }
        Assignment assignment = ASSIGNMENTS.get(person);
        if (assignment == null) return;
        VillagerEntityMCA patient = assignment.patient;
        if (!person.getVillagerData().profession().is(VillagerProfession.CLERIC)
                || person.isNoAi() || person.getHealth() <= person.getMaxHealth() * 0.25F
                || person.isKidnapped() || !patient.isAlive() || patient.isKidnapped() || patient.isNoAi()
                || ProfessionsMCA.isRaider(patient.getProfession()) || patient.level() != world
                || patient.distanceToSqr(person) > HELP_RADIUS * HELP_RADIUS || now >= assignment.expires
                || !patient.isInfected() && !patient.hasEffect(MobEffects.WITHER) && patient.getHealth() >= patient.getMaxHealth() * 0.85F && !patient.isOnFire()) {
            finish(person);
            return;
        }
        if (patient.isInfected() && !InfirmaryCare.isPatient(patient) && patient.getInteractions().getInteractingPlayer().isEmpty()) {
            assignment.seekingCure = true;
            if (patient.isSleeping()) patient.stopSleeping();
            patient.getBrain().setActiveActivityIfPossible(Activity.IDLE);
            patient.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(person, true));
            patient.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new EntityTracker(person, false), 0.65F, 1));
        }
        if (person.isSleeping()) person.stopSleeping();
        person.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        person.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(patient, true));
        double treatmentRange = patient.isInfected() || patient.hasEffect(MobEffects.WITHER) ? 2.0 : 6.0;
        if (person.distanceToSqr(patient) > treatmentRange * treatmentRange || !person.hasLineOfSight(patient)) {
            person.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new EntityTracker(patient, false), 0.65F, patient.isInfected() || patient.hasEffect(MobEffects.WITHER) ? 1 : 3));
            return;
        }
        person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        person.getNavigation().stop();
        if (now < assignment.nextPotion) return;
        if (patient.hasEffect(MobEffects.WITHER)) {
            person.swing(InteractionHand.MAIN_HAND);
            world.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.MILK_BUCKET),
                    patient.getX(), patient.getY() + .5, patient.getZ(), 8, .15, .1, .15, .02);
            patient.playSound(SoundEvents.GENERIC_DRINK.value(), 1.0F, 1.0F);
            patient.removeEffect(MobEffects.WITHER);
            assignment.nextPotion = now + 60;
            return;
        }
        if (patient.isInfected()) {
            person.swing(InteractionHand.MAIN_HAND);
            world.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.GOLDEN_APPLE),
                    patient.getX(), patient.getY() + patient.getBbHeight() * 0.8, patient.getZ(),
                    8, 0.15, 0.1, 0.15, 0.02);
            patient.playSound(SoundEvents.GENERIC_EAT.value(), 1.0F, 1.0F);
            patient.setInfected(false);
            patient.ate();
            releasePatient(assignment);
            assignment.nextPotion = now + 60;
            if (patient.getHealth() >= patient.getMaxHealth() * 0.85F && !patient.isOnFire()) finish(person);
            return;
        }
        var potion = patient.isOnFire() && !patient.hasEffect(MobEffects.FIRE_RESISTANCE) ? Potions.FIRE_RESISTANCE
                : patient.getHealth() <= patient.getMaxHealth() * 0.5F ? Potions.STRONG_HEALING
                : !patient.hasEffect(MobEffects.REGENERATION) ? Potions.REGENERATION : Potions.HEALING;
        RaiderCombat.throwPotion(world, person, patient, potion);
        assignment.nextPotion = now + 60;
    }

    private static void finish(VillagerEntityMCA cleric) {
        Assignment assignment = ASSIGNMENTS.remove(cleric);
        if (assignment != null) releasePatient(assignment);
        cleric.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        cleric.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        cleric.getNavigation().stop();
        cleric.getBrain().updateActivityFromSchedule(cleric.level().environmentAttributes(),
                cleric.level().getGameTime(), cleric.position());
    }

    private static void releasePatient(Assignment assignment) {
        if (!assignment.seekingCure) return;
        assignment.seekingCure = false;
        VillagerEntityMCA patient = assignment.patient;
        patient.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        patient.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        patient.getNavigation().stop();
        patient.getBrain().updateActivityFromSchedule(patient.level().environmentAttributes(),
                patient.level().getGameTime(), patient.position());
    }

    private static final class Assignment {
        final VillagerEntityMCA patient;
        final long expires;
        long nextPotion;
        boolean seekingCure;
        Assignment(VillagerEntityMCA patient, long now) {
            this.patient = patient;
            expires = now + ASSIGNMENT_TIMEOUT;
            nextPotion = now + 20;
        }
    }
}
