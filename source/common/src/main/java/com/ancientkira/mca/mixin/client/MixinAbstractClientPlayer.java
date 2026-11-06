package com.ancientkira.mca.mixin.client;

import com.ancientkira.mca.MCAClient;
import com.ancientkira.mca.client.render.DynamicSkinCache;
import com.ancientkira.mca.client.render.VillagerVisuals;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class MixinAbstractClientPlayer {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void mca$getCustomSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        if (!MCAClient.useGeneticsRenderer(player.getUUID())) {
            return;
        }

        MCAClient.getPlayerData(player.getUUID()).ifPresent(data -> {
            VillagerVisuals visuals = VillagerVisuals.capture(data);
            Identifier stitched = DynamicSkinCache.getOrCreateStitchedSkin(visuals);
            PlayerSkin original = cir.getReturnValue();
            if (original != null && stitched != null) {
                PlayerModelType modelType = visuals.female() ? PlayerModelType.SLIM : PlayerModelType.WIDE;
                cir.setReturnValue(new PlayerSkin(new net.minecraft.core.ClientAsset.ResourceTexture(stitched, stitched), original.cape(), original.elytra(), modelType, false));
            }
        });
    }
}
