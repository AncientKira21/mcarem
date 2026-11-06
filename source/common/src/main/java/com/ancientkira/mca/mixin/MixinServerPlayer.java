package com.ancientkira.mca.mixin;

import com.ancientkira.mca.item.BabyItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
abstract class MixinServerPlayer {
    @Inject(method="tick()V",at=@At("TAIL"))
    private void mca$refreshHiddenCollisionBlocks(org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        com.ancientkira.mca.server.FirstJoinSetup.tick((ServerPlayer)(Object)this);
        com.ancientkira.mca.server.CollisionVisibilityRepair.tick((net.minecraft.server.level.ServerPlayer)(Object)this);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void mca$saveNormalGravityDuringSetup(net.minecraft.world.level.storage.ValueOutput output,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        ServerPlayer player = (ServerPlayer)(Object)this;
        if (com.ancientkira.mca.server.FirstJoinSetup.active(player))
            output.putBoolean("NoGravity", com.ancientkira.mca.server.FirstJoinSetup.originalNoGravity(player));
    }

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void mca$protectFirstJoin(net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.damagesource.DamageSource source, float amount, CallbackInfoReturnable<Boolean> info) {
        if (com.ancientkira.mca.server.FirstJoinSetup.active((ServerPlayer)(Object)this)) info.setReturnValue(false);
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"),
            cancellable = true)
    private void onDropItem(ItemStack stack, boolean randomly, boolean thrownFromHand, CallbackInfoReturnable<ItemEntity> info) {
        //noinspection ConstantValue
        if (stack.getItem() instanceof BabyItem baby && !baby.onDropped(stack, (ServerPlayer) (Object) this)) {
            info.setReturnValue(null);
        }
    }
}
