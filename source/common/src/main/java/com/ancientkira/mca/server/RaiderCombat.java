package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.EquipmentSet;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.Comparator;
import java.util.Map;
import java.util.WeakHashMap;

/** Profession-specific combat runs on the server alongside the shared target-selection brain. */
public final class RaiderCombat {
    private static final Map<VillagerEntityMCA, State> STATES = new com.google.common.collect.MapMaker().weakKeys().makeMap();

    private RaiderCombat() { }

    public static void clear(VillagerEntityMCA person) {
        STATES.remove(person);
        person.setCastingSpell(false);
        person.setTarget(null);
    }

    public static EquipmentSet equipment(VillagerProfession profession) {
        if (profession == ProfessionsMCA.PILLAGER) return EquipmentSet.PILLAGER;
        if (profession == ProfessionsMCA.VINDICATOR) return EquipmentSet.VINDICATOR;
        return EquipmentSet.SPELLCASTER;
    }

    public static void tick(VillagerEntityMCA person) {
        if (!ProfessionsMCA.isRaider(person.getProfession()) || person.isPeacefulRaider() || person.isNoAi()) return;
        LivingEntity target = person.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (target != null && (!target.isAlive() || target.level() != person.level())) target = null;
        // Vanilla Vexes follow their owner's Mob target rather than its brain memory.
        person.setTarget(target);
        if (!ProfessionsMCA.isSpellcaster(person.getProfession())) return;
        State state = STATES.computeIfAbsent(person, ignored -> new State(person.getProfession()));
        if (state.profession != person.getProfession()) {
            state = new State(person.getProfession());
            STATES.put(person, state);
        }
        ServerLevel world = (ServerLevel) person.level();
        long now = world.getGameTime();
        if (person.getProfession() == ProfessionsMCA.WITCH) tickWitch(world, person, target, state, now);
        else tickEvoker(world, person, target, state, now);
    }

    private static void tickEvoker(ServerLevel world, VillagerEntityMCA caster, LivingEntity target, State state, long now) {
        if (target == null) { state.spell = Spell.NONE; caster.setCastingSpell(false); return; }
        caster.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        if (state.spell != Spell.NONE) {
            stopWalking(caster);
            if (now % 4 == 0) world.sendParticles(ParticleTypes.ENCHANT, caster.getX(), caster.getEyeY(), caster.getZ(), 4, 0.4, 0.3, 0.4, 0.05);
            if (now >= state.castAt) {
                if (state.spell == Spell.SUMMON) summonVexes(world, caster, target);
                else castFangs(world, caster, target);
                sound(caster, SoundEvents.EVOKER_CAST_SPELL);
                state.spell = Spell.NONE;
                caster.setCastingSpell(false);
            }
            return;
        }
        positionCaster(caster, target, 12, now);
        if (!caster.hasLineOfSight(target) || caster.distanceToSqr(target) > 24 * 24) return;
        int vexes = world.getEntitiesOfClass(Vex.class, caster.getBoundingBox().inflate(16),
                vex -> vex.isAlive() && vex.getOwner() == caster).size();
        if (now >= state.nextSummon && vexes < 6) {
            state.spell = Spell.SUMMON;
            state.nextSummon = now + 340;
            sound(caster, SoundEvents.EVOKER_PREPARE_SUMMON);
        } else if (now >= state.nextAttack) {
            state.spell = Spell.FANGS;
            state.nextAttack = now + 100;
            sound(caster, SoundEvents.EVOKER_PREPARE_ATTACK);
        } else return;
        caster.setCastingSpell(true);
        state.castAt = now + 20;
        stopWalking(caster);
    }

    private static void summonVexes(ServerLevel world, VillagerEntityMCA caster, LivingEntity target) {
        int existing = world.getEntitiesOfClass(Vex.class, caster.getBoundingBox().inflate(16),
                vex -> vex.isAlive() && vex.getOwner() == caster).size();
        for (int i = 0; i < Math.min(3, 6 - existing); i++) {
            net.minecraft.world.entity.Entity summoned = EntityType.VEX.create(world, EntitySpawnReason.MOB_SUMMONED);
            if (!(summoned instanceof Vex vex)) continue;
            BlockPos pos = caster.blockPosition().offset(caster.getRandom().nextInt(5) - 2, 1, caster.getRandom().nextInt(5) - 2);
            vex.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            vex.finalizeSpawn(world, world.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
            vex.setOwner(caster);
            vex.setTarget(target);
            vex.setBoundOrigin(pos);
            vex.setLimitedLife(20 * (30 + caster.getRandom().nextInt(90)));
            world.addFreshEntity(vex);
        }
    }

    private static void castFangs(ServerLevel world, VillagerEntityMCA caster, LivingEntity target) {
        double minY = Math.min(caster.getY(), target.getY());
        double maxY = Math.max(caster.getY(), target.getY()) + 1;
        float angle = (float) Math.atan2(target.getZ() - caster.getZ(), target.getX() - caster.getX());
        if (caster.distanceToSqr(target) < 9) {
            for (int i = 0; i < 5; i++) {
                float direction = angle + i * (float) Math.PI * 0.4F;
                fang(world, caster, caster.getX() + Math.cos(direction) * 1.5, caster.getZ() + Math.sin(direction) * 1.5, minY, maxY, direction, 0);
            }
            for (int i = 0; i < 8; i++) {
                float direction = angle + i * (float) Math.PI * 0.25F + 1.2566371F;
                fang(world, caster, caster.getX() + Math.cos(direction) * 2.5, caster.getZ() + Math.sin(direction) * 2.5, minY, maxY, direction, 3);
            }
        } else {
            for (int i = 0; i < 16; i++) {
                double distance = 1.25 * (i + 1);
                fang(world, caster, caster.getX() + Math.cos(angle) * distance, caster.getZ() + Math.sin(angle) * distance, minY, maxY, angle, i);
            }
        }
    }

    private static void fang(ServerLevel world, VillagerEntityMCA owner, double x, double z, double minY, double maxY, float angle, int delay) {
        BlockPos pos = BlockPos.containing(x, maxY, z);
        while (pos.getY() >= Math.floor(minY) - 1 && world.hasChunkAt(pos)) {
            BlockPos below = pos.below();
            if (world.getBlockState(below).isFaceSturdy(world, below, Direction.UP)) {
                var shape = world.getBlockState(pos).getCollisionShape(world, pos);
                double surface = shape.isEmpty() ? 0 : shape.max(Direction.Axis.Y);
                world.addFreshEntity(new EvokerFangs(world, x, pos.getY() + surface, z, angle, delay, owner));
                return;
            }
            pos = below;
        }
    }

    private static void tickWitch(ServerLevel world, VillagerEntityMCA witch, LivingEntity target, State state, long now) {
        if (state.drinking != null) {
            stopWalking(witch);
            if (now % 4 == 0) world.sendParticles(ParticleTypes.WITCH, witch.getX(), witch.getEyeY(), witch.getZ(), 3, 0.3, 0.2, 0.3, 0);
            if (now >= state.drinkUntil) {
                new PotionContents(state.drinking).applyToLivingEntity(witch, 1.0F);
                state.drinking = null;
                witch.setItemInHand(witch.getDominantHand(), ItemStack.EMPTY);
                sound(witch, SoundEvents.WITCH_DRINK);
            }
            return;
        }
        if (now >= state.nextDrink && witch.getRandom().nextFloat() < 0.15F) {
            Holder<Potion> potion = defensivePotion(witch, target);
            if (potion != null) {
                state.drinking = potion;
                state.drinkUntil = now + 32;
                state.nextDrink = now + 100;
                witch.setItemInHand(witch.getDominantHand(), PotionContents.createItemStack(Items.POTION, potion));
                stopWalking(witch);
                return;
            }
        }
        if (now >= state.nextAttack) {
            LivingEntity ally = injuredRaidAlly(world, witch);
            if (ally != null && now >= state.nextHeal) {
                throwPotion(world, witch, ally, ally.getHealth() <= 4 ? Potions.HEALING : Potions.REGENERATION);
                state.nextAttack = now + 60;
                state.nextHeal = now + 100;
                return;
            }
        }
        if (target == null) return;
        positionCaster(witch, target, 10, now);
        if (now < state.nextAttack || witch.distanceToSqr(target) > 10 * 10 || !witch.hasLineOfSight(target)) return;
        Holder<Potion> potion = offensivePotion(witch, target);
        throwPotion(world, witch, target, potion);
        state.nextAttack = now + 60;
    }

    private static Holder<Potion> defensivePotion(VillagerEntityMCA witch, LivingEntity target) {
        if (witch.isUnderWater() && !witch.hasEffect(MobEffects.WATER_BREATHING)) return Potions.WATER_BREATHING;
        if (witch.isOnFire() && !witch.hasEffect(MobEffects.FIRE_RESISTANCE)) return Potions.FIRE_RESISTANCE;
        if (witch.getHealth() < witch.getMaxHealth()) return Potions.HEALING;
        if (target != null && witch.distanceToSqr(target) > 11 * 11 && !witch.hasEffect(MobEffects.SPEED)) return Potions.SWIFTNESS;
        return null;
    }

    private static Holder<Potion> offensivePotion(VillagerEntityMCA witch, LivingEntity target) {
        if (witch.distanceToSqr(target) >= 8 * 8 && !target.hasEffect(MobEffects.SLOWNESS)) return Potions.SLOWNESS;
        if (target.getHealth() >= 8 && !target.hasEffect(MobEffects.POISON)) return Potions.POISON;
        if (witch.distanceToSqr(target) <= 3 * 3 && !target.hasEffect(MobEffects.WEAKNESS) && witch.getRandom().nextFloat() < 0.25F) return Potions.WEAKNESS;
        return Potions.HARMING;
    }

    private static LivingEntity injuredRaidAlly(ServerLevel world, VillagerEntityMCA witch) {
        var raid = PillagerRaids.getRaid(witch).filter(r -> r.isActive() && !r.isOver());
        if (raid.isEmpty()) return null;
        return world.getEntitiesOfClass(LivingEntity.class, witch.getBoundingBox().inflate(10),
                        ally -> ally != witch && ally.isAlive() && ally.getHealth() < ally.getMaxHealth()
                                && !ally.hasEffect(MobEffects.REGENERATION) && witch.hasLineOfSight(ally)
                                && PillagerRaids.belongsTo(raid.get(), ally)
                                && !(ally instanceof net.minecraft.world.entity.monster.Witch)
                                && !(ally instanceof VillagerEntityMCA person && person.getProfession() == ProfessionsMCA.WITCH))
                .stream().min(Comparator.comparingDouble(LivingEntity::getHealth)).orElse(null);
    }

    public static void throwPotion(ServerLevel world, VillagerEntityMCA witch, LivingEntity target, Holder<Potion> potion) {
        witch.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        ItemStack stack = PotionContents.createItemStack(Items.SPLASH_POTION, potion);
        double x = target.getX() + target.getDeltaMovement().x - witch.getX();
        double z = target.getZ() + target.getDeltaMovement().z - witch.getZ();
        double distance = Math.sqrt(x * x + z * z);
        double y = target.getEyeY() - 1.1 - witch.getY() + distance * 0.2;
        Projectile.spawnProjectileUsingShoot(ThrownSplashPotion::new, world, stack, witch, x, y, z, distance <= 2 ? 0.45F : 0.75F, 8);
        witch.swing(witch.getDominantHand());
        sound(witch, SoundEvents.WITCH_THROW);
    }

    private static void positionCaster(VillagerEntityMCA caster, LivingEntity target, int range, long now) {
        caster.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        if (now % 20 != 0) return;
        if (caster.distanceToSqr(target) > range * range || !caster.hasLineOfSight(target)) {
            caster.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(target, false), 0.6F, range - 2));
        } else stopWalking(caster);
    }

    private static void stopWalking(VillagerEntityMCA caster) {
        caster.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        caster.getNavigation().stop();
    }

    private static void sound(VillagerEntityMCA caster, SoundEvent sound) {
        caster.playSound(sound, 1.0F, 1.0F);
    }

    private enum Spell { NONE, FANGS, SUMMON }

    private static final class State {
        final VillagerProfession profession;
        long nextAttack, nextSummon, castAt, nextDrink, nextHeal, drinkUntil;
        Spell spell = Spell.NONE;
        Holder<Potion> drinking;
        State(VillagerProfession profession) { this.profession = profession; }
    }
}
