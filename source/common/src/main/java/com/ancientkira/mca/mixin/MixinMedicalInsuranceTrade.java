package com.ancientkira.mca.mixin;

import com.ancientkira.mca.server.MedicalInsurance;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.world.inventory.MerchantContainer;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantResultSlot.class)
public class MixinMedicalInsuranceTrade {
    @Shadow @Final private MerchantContainer slots;
    @Unique private boolean mca$servicePurchase;
    @Inject(method = "onTake", at = @At("HEAD"))
    private void mca$rememberService(Player player, ItemStack carried, CallbackInfo ci) {
        var offer = slots.getActiveOffer();
        mca$servicePurchase = offer != null && MedicalInsurance.isPolicy(offer.getResult());
    }
    @Inject(method = "onTake", at = @At("TAIL"))
    private void mca$consumeServicePreview(Player player, ItemStack carried, CallbackInfo ci) {
        if (mca$servicePurchase) MedicalInsurance.consumePreview(player, carried);
        mca$servicePurchase = false;
    }
}
