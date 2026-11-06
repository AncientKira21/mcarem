package com.ancientkira.mca.mixin;

import com.ancientkira.mca.server.world.data.PlayerBedClaims;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
abstract class MixinPlayerBedClaim {
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void mca$claimHomeBed(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
                                  BlockHitResult hit, CallbackInfoReturnable<InteractionResult> callback) {
        if (!player.isSecondaryUseActive() && level instanceof net.minecraft.server.level.ServerLevel world)
            PlayerBedClaims.claim(player, world, hit.getBlockPos());
    }
}
