package com.ancientkira.mca.entity.ai;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.jetbrains.annotations.Nullable;

public final class RangedWeaponHelper {
    private RangedWeaponHelper() {
    }

    public static boolean isHoldingSupportedWeapon(LivingEntity entity) {
        return getWeaponHoldingHand(entity) != null;
    }

    @Nullable
    public static InteractionHand getWeaponHoldingHand(LivingEntity entity) {
        InteractionHand gunHand = GunCompatibility.hand(entity);
        if (gunHand != null) return gunHand;
        InteractionHand crossbowHand = findHoldingHand(entity, CrossbowItem.class);
        if(crossbowHand!=null)return crossbowHand;
        InteractionHand trident=getTridentHoldingHand(entity);
        return trident!=null?trident:findHoldingHand(entity,BowItem.class);
    }

    @Nullable
    public static InteractionHand getBowHoldingHand(LivingEntity entity) {
        return getSelectedHoldingHand(entity, BowItem.class);
    }

    @Nullable
    public static InteractionHand getCrossbowHoldingHand(LivingEntity entity) {
        return getSelectedHoldingHand(entity, CrossbowItem.class);
    }

    @Nullable
    public static InteractionHand getTridentHoldingHand(LivingEntity entity) {
        for(InteractionHand hand:InteractionHand.values())
            if(entity.getItemInHand(hand).is(net.minecraft.world.item.Items.TRIDENT))return hand;
        return null;
    }

    public static double getAttackRangeSquared(LivingEntity entity, double maximumRangeSquared) {
        InteractionHand hand = getWeaponHoldingHand(entity);
        return hand == null ? 0.0D : getAttackRangeSquared(entity, hand, maximumRangeSquared);
    }

    public static double getAttackRangeSquared(LivingEntity entity, InteractionHand hand) {
        if (GunCompatibility.isGun(entity.getItemInHand(hand))) return 1024.0D;
        if (entity.getItemInHand(hand).is(net.minecraft.world.item.Items.TRIDENT)) return 225.0D;
        if (!(entity.getItemInHand(hand).getItem() instanceof ProjectileWeaponItem weapon)) {
            return 0.0D;
        }

        double range = entity.isInWater() ? 3.0D : weapon instanceof CrossbowItem ? 15.0D : weapon.getDefaultProjectileRange();
        return range * range;
    }

    public static double getAttackRangeSquared(LivingEntity entity, InteractionHand hand, double maximumRangeSquared) {
        return Math.min(maximumRangeSquared, getAttackRangeSquared(entity, hand));
    }

    public static boolean isValidAttackTarget(Mob entity, @Nullable LivingEntity target) {
        return target != null
               && (!(entity instanceof com.ancientkira.mca.entity.VillagerEntityMCA guard) || GuardPerception.canLocate(guard,target))
               && target.isAlive()
               && target.level() == entity.level()
               && entity.canAttack(target);
    }

    @Nullable
    private static InteractionHand getSelectedHoldingHand(
            LivingEntity entity,
            Class<? extends ProjectileWeaponItem> weaponType
    ) {
        InteractionHand hand = getWeaponHoldingHand(entity);
        return hand != null && weaponType.isInstance(entity.getItemInHand(hand).getItem()) ? hand : null;
    }

    @Nullable
    private static InteractionHand findHoldingHand(LivingEntity entity, Class<? extends ProjectileWeaponItem> weaponType) {
        if (weaponType.isInstance(entity.getMainHandItem().getItem())) {
            return InteractionHand.MAIN_HAND;
        }

        if (weaponType.isInstance(entity.getOffhandItem().getItem())) {
            return InteractionHand.OFF_HAND;
        }

        return null;
    }
}
