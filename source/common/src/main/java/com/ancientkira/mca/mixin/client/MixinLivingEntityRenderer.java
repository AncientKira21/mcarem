package com.ancientkira.mca.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.ancientkira.mca.client.gui.PreviewEntityAnimation;
import com.ancientkira.mca.client.render.VillagerStateHolder;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {
    @ModifyReturnValue(method = "getModelTint", at = @At("RETURN"))
    private int mca$greyRaiderGolem(int tint, LivingEntityRenderState state) {
        if (state instanceof com.ancientkira.mca.entity.RaiderGolemAppearance raider && raider.mca$isGreyRaider()) {
            int red = ((tint >>> 16) & 255) * 153 / 255;
            int green = ((tint >>> 8) & 255) * 153 / 255;
            int blue = (tint & 255) * 153 / 255;
            return (tint & 0xFF000000) | red << 16 | green << 8 | blue;
        }
        return tint;
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void mca$capturePreviewAnimationState(
            LivingEntity entity,
            LivingEntityRenderState state,
            float partialTicks,
            CallbackInfo ci
    ) {
        VillagerStateHolder.require(state).mca$setPreviewEntityAnimationState(PreviewEntityAnimation.getActiveState(entity));
    }

    @ModifyReturnValue(
            method = "getRenderType(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            at = @At("RETURN")
    )
    private @Nullable RenderType mca$hideVanillaPlayerModel(
            @Nullable RenderType original,
            LivingEntityRenderState state,
            boolean showBody,
            boolean translucent,
            boolean showOutline
    ) {
        return state instanceof VillagerStateHolder holder && holder.mca$isVillagerRendererActive()
                ? null
                : original;
    }
}
