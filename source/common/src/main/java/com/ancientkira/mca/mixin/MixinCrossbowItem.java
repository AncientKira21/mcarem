package com.ancientkira.mca.mixin;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.UnderwaterAim;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.CrossbowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrossbowItem.class)
public class MixinCrossbowItem {
    @Inject(method="shootProjectile(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/projectile/Projectile;IFFFLnet/minecraft/world/entity/LivingEntity;)V", at=@At("TAIL"))
    private void mca$waterAim(LivingEntity shooter, Projectile projectile, int index, float power,
            float uncertainty, float angle, LivingEntity target, CallbackInfo ci) {
        if (!(shooter instanceof VillagerEntityMCA) || !shooter.isInWater() || target == null
                || !(projectile instanceof AbstractArrow)) return;
        var aim = UnderwaterAim.direction(projectile.position(), target, power, projectile instanceof ThrownTrident ? .99 : .6);
        aim = aim.yRot(-angle * (float)Math.PI / 180);
        projectile.shoot(aim.x, aim.y, aim.z, power, 2);
    }
}
