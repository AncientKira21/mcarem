package com.ancientkira.mca.mixin;
import com.ancientkira.mca.server.PortalPursuit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerPlayer.class)
public abstract class MixinPlayerPortalPursuit {
    @Inject(method="teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",at=@At("HEAD"))
    private void mca$beforePortal(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) { PortalPursuit.beforeTeleport((ServerPlayer)(Object)this,transition); }
    @Inject(method="teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",at=@At("RETURN"))
    private void mca$afterPortal(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) { PortalPursuit.afterTeleport((ServerPlayer)(Object)this,cir.getReturnValue()); }
}
