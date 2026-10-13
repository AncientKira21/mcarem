package com.ancientkira.mca.mixin;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NPCs need the same disable duration even when an item/enchantment calls disable directly. */
@Mixin(BlocksAttacks.class)
public abstract class MixinBlocksAttacks {
    @Shadow
    private int disableBlockingForTicks(float seconds) { throw new AssertionError(); }

    @Inject(method = "disable", at = @At("HEAD"))
    private void mca$rememberNpcShieldCooldown(ServerLevel world, LivingEntity user, float seconds,
                                              ItemStack shield, CallbackInfo ci) {
        if (user instanceof VillagerEntityMCA person) {
            person.disableCombatShield(disableBlockingForTicks(seconds));
        }
    }
}
