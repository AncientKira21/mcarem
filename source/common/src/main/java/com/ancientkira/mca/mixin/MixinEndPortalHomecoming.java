package com.ancientkira.mca.mixin;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.server.PortalHomecoming;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EndPortalBlock.class)
abstract class MixinEndPortalHomecoming {
    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void mca$returnToHome(ServerLevel source, Entity entity, BlockPos portal,
                                 CallbackInfoReturnable<TeleportTransition> callback) {
        if (entity instanceof VillagerEntityMCA person) {
            TeleportTransition destination = PortalHomecoming.endDestination(source, person);
            if (destination != null) callback.setReturnValue(destination);
        }
    }
}
