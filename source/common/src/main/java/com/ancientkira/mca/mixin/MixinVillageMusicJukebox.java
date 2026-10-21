package com.ancientkira.mca.mixin;

import com.ancientkira.mca.server.VillageMusic;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(JukeboxBlockEntity.class)
abstract class MixinVillageMusicJukebox {
    @Unique private int mca$silentTicks;
    @Inject(method="popOutTheItem", at=@At("HEAD"), cancellable=true)
    private void mca$lockPlayback(CallbackInfo ci) {
        if (VillageMusic.isPlaybackCopy(((JukeboxBlockEntity)(Object)this).getTheItem())) ci.cancel();
    }
    @Inject(method="splitTheItem", at=@At("HEAD"), cancellable=true)
    private void mca$noPlaybackExtraction(int count, CallbackInfoReturnable<ItemStack> cir) {
        if (VillageMusic.isPlaybackCopy(((JukeboxBlockEntity)(Object)this).getTheItem())) cir.setReturnValue(ItemStack.EMPTY);
    }
    @Inject(method="canTakeItem", at=@At("HEAD"), cancellable=true)
    private void mca$noHopperCopy(Container into, int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (VillageMusic.isPlaybackCopy(stack)) cir.setReturnValue(false);
    }
    @Inject(method="preRemoveSideEffects", at=@At("HEAD"))
    private void mca$discardBrokenPlayback(BlockPos pos, BlockState state, CallbackInfo ci) {
        JukeboxBlockEntity box=(JukeboxBlockEntity)(Object)this;
        if (VillageMusic.isPlaybackCopy(box.getTheItem())) box.setTheItem(ItemStack.EMPTY);
    }
    @Inject(method="tick", at=@At("TAIL"))
    private static void mca$cleanupAfterSilence(Level level, BlockPos pos, BlockState state, JukeboxBlockEntity box, CallbackInfo ci) {
        if (level.isClientSide()) return;
        // DiscCord plays client-streamed audio; its real ending is reported by listeners.
        if(com.ancientkira.mca.compat.DiscCordMusic.isDisc(box.getTheItem()))return;
        MixinVillageMusicJukebox self=(MixinVillageMusicJukebox)(Object)box;
        if (!VillageMusic.isPlaybackCopy(box.getTheItem()) || box.getSongPlayer().isPlaying()) {
            self.mca$silentTicks=0;
        } else if (++self.mca$silentTicks >= 60) {
            box.setTheItem(ItemStack.EMPTY);
            self.mca$silentTicks=0;
        }
    }
}
