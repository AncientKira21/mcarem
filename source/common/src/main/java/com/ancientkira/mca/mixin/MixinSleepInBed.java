package com.ancientkira.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.SleepInBed;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SleepInBed.class)
abstract class MixinSleepInBed {
    @org.spongepowered.asm.mixin.injection.Inject(method = "checkExtraStartConditions", at = @At("HEAD"), cancellable = true)
    private void mca$leavePlayerBedFree(ServerLevel world, LivingEntity entity,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> callback) {
        var home = entity.getBrain().getMemoryInternal(MemoryModuleType.HOME).orElse(null);
        if (home != null && com.ancientkira.mca.server.world.data.PlayerBedClaims.reserved(world, home.pos())) {
            if (entity instanceof VillagerEntityMCA person) person.releasePoi(MemoryModuleType.HOME);
            entity.getBrain().eraseMemory(MemoryModuleType.HOME);
            callback.setReturnValue(false);
        }
    }

    @ModifyExpressionValue(
            method = "checkExtraStartConditions",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"
            )
    )
    private boolean mca$allowBehindFootApproach(
            boolean original,
            ServerLevel world,
            LivingEntity entity
    ) {
        if (original || !(entity instanceof VillagerEntityMCA)) {
            return original;
        }

        BlockPos bedPos = entity.getBrain().getMemory(MemoryModuleType.HOME).orElseThrow().pos();
        BlockState bedState = world.getBlockState(bedPos);
        if (!bedState.is(BlockTags.BEDS)
                || !bedState.hasProperty(BedBlock.FACING)
                || !bedState.hasProperty(BedBlock.PART)
                || bedState.getValue(BedBlock.PART) != BedPart.HEAD) {
            return false;
        }

        Direction facing = bedState.getValue(BedBlock.FACING);
        BlockPos behindFoot = bedPos.relative(facing.getOpposite(), 2);
        return entity.blockPosition().equals(behindFoot);
    }
}
