package com.ancientkira.mca.mixin.client;

import com.ancientkira.mca.entity.RaiderGolemAppearance;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(IronGolemRenderState.class)
abstract class MixinIronGolemRenderState implements RaiderGolemAppearance {
    @Unique private boolean mca$greyRaider;
    @Override public boolean mca$isGreyRaider() { return mca$greyRaider; }
    @Override public void mca$setGreyRaider(boolean grey) { mca$greyRaider = grey; }
}
