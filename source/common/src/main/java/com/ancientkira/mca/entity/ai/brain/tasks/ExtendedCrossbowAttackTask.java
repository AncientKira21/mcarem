package com.ancientkira.mca.entity.ai.brain.tasks;

import com.ancientkira.mca.entity.ai.RangedWeaponHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.CrossbowAttack;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.CrossbowAttackMob;

public final class ExtendedCrossbowAttackTask<E extends Mob & CrossbowAttackMob, T extends LivingEntity> extends CrossbowAttack<E, T> {
    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, E entity) {
        LivingEntity target = getAttackTarget(entity);
        InteractionHand hand = RangedWeaponHelper.getCrossbowHoldingHand(entity);
        return RangedWeaponHelper.isValidAttackTarget(entity, target)
               && hand != null
               && entity.getSensing().hasLineOfSight(target)
               && entity.distanceToSqr(target) <= RangedWeaponHelper.getAttackRangeSquared(entity, hand);
    }

    @Override
    public void crossbowAttack(E entity, LivingEntity target) {
        if (entity instanceof com.ancientkira.mca.entity.VillagerEntityMCA person && person.isUsingCombatShield()) return;
        if (this.crossbowState == CrossbowState.UNCHARGED) {
            InteractionHand hand = RangedWeaponHelper.getCrossbowHoldingHand(entity);
            if (hand == null) {
                return;
            }
            if (!entity.getItemInHand(hand).getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).isEmpty()) {
                this.crossbowState = CrossbowState.READY_TO_ATTACK;
                return;
            }
            entity.startUsingItem(hand);
            this.crossbowState = CrossbowState.CHARGING;
            entity.setChargingCrossbow(true);
            return;
        }

        super.crossbowAttack(entity, target);
    }

    @Override
    protected void stop(ServerLevel level, E entity, long gameTime) {
        if (entity.isUsingItem()) {
            entity.stopUsingItem();
        }
        // A temporary loss of sight keeps the shot; leaving combat unloads it.
        if (!RangedWeaponHelper.isValidAttackTarget(entity, getAttackTarget(entity))
                && entity instanceof com.ancientkira.mca.entity.VillagerEntityMCA person) unloadIdle(person);
        this.crossbowState = CrossbowState.UNCHARGED;
        entity.setChargingCrossbow(false);
    }

    public static void unloadIdle(com.ancientkira.mca.entity.VillagerEntityMCA person) {
        if (person.level().isClientSide()) return;
        LivingEntity target = getAttackTarget(person);
        if (RangedWeaponHelper.isValidAttackTarget(person, target)
                || RangedWeaponHelper.isValidAttackTarget(person, person.getTarget())) return;
        for (InteractionHand hand : InteractionHand.values()) {
            var weapon = person.getItemInHand(hand);
            if (!(weapon.getItem() instanceof net.minecraft.world.item.CrossbowItem)) continue;
            if (person.isUsingItem() && person.getUsedItemHand() == hand) person.stopUsingItem();
            var loaded = weapon.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
            if (!loaded.isEmpty()) {
                // Multishot's additional projectiles are copies of the one consumed round.
                var ammunition = loaded.itemCopies().iterator().next();
                var remainder = person.getInventory().addItem(ammunition);
                if (!remainder.isEmpty() && person.level() instanceof ServerLevel world)
                    world.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(world,
                            person.getX(), person.getY(), person.getZ(), remainder));
                weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
            }
        }
        person.setChargingCrossbow(false);
    }

    private static LivingEntity getAttackTarget(LivingEntity entity) {
        return entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }
}
