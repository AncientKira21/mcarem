package com.ancientkira.mca.mixin;
import com.ancientkira.mca.server.PortalPursuit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Entity.class)
public abstract class MixinPortalPursuit {
    @Inject(method="teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/world/entity/Entity;",at=@At("HEAD"))
    private void mca$beforePortal(TeleportTransition transition, CallbackInfoReturnable<Entity> cir) { PortalPursuit.beforeTeleport((Entity)(Object)this,transition); }
    @Inject(method="teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/world/entity/Entity;",at=@At("RETURN"))
    private void mca$afterPortal(TeleportTransition transition, CallbackInfoReturnable<Entity> cir) { PortalPursuit.afterTeleport((Entity)(Object)this,cir.getReturnValue()); }
}
