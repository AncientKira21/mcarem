package com.ancientkira.mca.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class MixinLevelGraveFlowers {
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"))
    private void mca$rememberTrampledFarm(BlockPos pos,BlockState state,int flags,int depth,CallbackInfoReturnable<Boolean> result) {
        if((Object)this instanceof net.minecraft.server.level.ServerLevel world
                && state.is(net.minecraft.world.level.block.Blocks.DIRT)
                && world.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.FARMLAND))
            com.ancientkira.mca.server.FarmRepairs.trampled(world,pos);
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
    private void mca$forgetChangedMourningFlower(BlockPos pos, BlockState state, int flags, int depth,
                                               CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValue()) {
            com.ancientkira.mca.server.GraveFlowers.changed((Level) (Object) this, pos, state);
            if ((Object)this instanceof net.minecraft.server.level.ServerLevel world)
                com.ancientkira.mca.server.FirefighterSearch.changed(world,pos,state);
        }
    }
}
