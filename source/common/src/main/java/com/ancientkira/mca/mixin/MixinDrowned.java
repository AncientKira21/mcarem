package com.ancientkira.mca.mixin;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.UnderwaterAim;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Drowned.class)
public class MixinDrowned {
    @Inject(method="performRangedAttack(Lnet/minecraft/world/entity/LivingEntity;F)V", at=@At("HEAD"), cancellable=true)
    private void mca$aimAtSwimmingPerson(LivingEntity target, float power, CallbackInfo ci) {
        Drowned drowned = (Drowned)(Object)this;
        if (!(target instanceof VillagerEntityMCA) || !target.isSwimming()
                || !(drowned.level() instanceof ServerLevel world)) return;
        ItemStack item = drowned.getMainHandItem().is(Items.TRIDENT) ? drowned.getMainHandItem() : new ItemStack(Items.TRIDENT);
        ThrownTrident projectile = new ThrownTrident(world, drowned, item);
        var direction = UnderwaterAim.direction(projectile.position(), target, 1.6, .99);
        Projectile.spawnProjectileUsingShoot(projectile, world, item, direction.x, direction.y, direction.z, 1.6F, 2);
        drowned.playSound(SoundEvents.DROWNED_SHOOT, 1, 1);
        ci.cancel();
    }
}
