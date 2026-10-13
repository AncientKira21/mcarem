package com.ancientkira.mca.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.Hud")
public interface MixinHudAccessor {
    @Accessor("isHidden")
    boolean mca$isHidden();

    @Accessor("isHidden")
    void mca$setHidden(boolean hidden);
}
