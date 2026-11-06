package com.ancientkira.mca.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Display.class)
abstract class MixinDisplay {
    @Inject(method="tick()V", at=@At("HEAD"))
    private void mca$removeTacticalAnvil(CallbackInfo ci) {
        com.ancientkira.mca.server.TacticalCommands.tickAnvil((Entity)(Object)this);
    }
}
