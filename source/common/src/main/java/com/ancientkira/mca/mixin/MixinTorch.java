package com.ancientkira.mca.mixin;

import com.ancientkira.mca.block.UnderwaterTorchBlock;
import com.ancientkira.mca.registry.BlocksMCA;
import com.ancientkira.mca.server.UnderwaterVillageSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BaseTorchBlock.class)
abstract class MixinTorch {
    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    private void mca$convertToUnderwaterTorch(BlockState state, LevelReader levelReader, ScheduledTickAccess scheduledTickAccess,
                                             BlockPos pos, Direction direction, BlockPos neighborPos,
                                             BlockState neighborState, RandomSource randomSource,
                                             CallbackInfoReturnable<BlockState> cir) {
        if (state.is(Blocks.TORCH) && levelReader instanceof Level level && level.isWaterAt(pos)) {
            if (UnderwaterVillageSupport.isAroundUnderwaterVillage(level, pos)) {
                cir.setReturnValue(BlocksMCA.UNDERWATER_TORCH.defaultBlockState().setValue(UnderwaterTorchBlock.WATERLOGGED, true));
            }
        }
    }
}
