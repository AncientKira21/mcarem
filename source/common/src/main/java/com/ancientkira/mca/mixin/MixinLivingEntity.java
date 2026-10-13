package com.ancientkira.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.ancientkira.mca.entity.PlayerDimensions;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;

@Mixin(LivingEntity.class)
abstract class MixinLivingEntity {
    @ModifyArg(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"), index = 0)
    private float mca$captureInsteadOfKilling(float remainingHealth, @Local(argsOnly = true) DamageSource source) {
        if ((Object) this instanceof VillagerEntityMCA victim
                && remainingHealth < victim.getHealth() && remainingHealth <= victim.getMaxHealth() * 0.25F
                && com.ancientkira.mca.server.RaiderSocial.tryKidnapFromAttack(source.getEntity(), victim)) {
            // Armor, shields and absorption have already reduced the damage at this point.
            return Math.max(1.0F, remainingHealth);
        }
        return remainingHealth;
    }

    @ModifyReturnValue(method = "getDimensions", at = @At("RETURN"))
    private EntityDimensions mca$scalePlayerDimensions(EntityDimensions original, Pose pose) {
        if (pose == Pose.SLEEPING || !((Object) this instanceof Player player)) {
            return original;
        }

        return PlayerDimensions.getScale(player)
                .map(scale -> {
                    EntityDimensions scaled = original.scale(scale.width(), scale.height());
                    return scaled;
                })
                .orElse(original);
    }

    @ModifyReturnValue(method = "isImmobile()Z", at = @At("RETURN"))
    private boolean mca$allowMcaControlledMovement(boolean original) {
        if ((Object) this instanceof Mob mob && mob.getControllingPassenger() instanceof VillagerEntityMCA) {
            return false;
        }
        return original;
    }
}
