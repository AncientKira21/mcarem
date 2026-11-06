package com.ancientkira.mca.mixin;

import com.ancientkira.mca.item.ExtendedWrittenBookItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the vanilla lectern/menu path available to Cinematic Interactions and other readers. */
@Mixin(LecternBlock.class)
public abstract class MixinLecternBooks {
    @Inject(method="tryPlaceBook",at=@At("HEAD"))
    private static void mca$preparePages(LivingEntity source,Level level,BlockPos pos,BlockState state,
                                         ItemStack stack,CallbackInfoReturnable<Boolean> cir) {
        if(!level.isClientSide() && !state.getValue(LecternBlock.HAS_BOOK)
                && stack.getItem() instanceof ExtendedWrittenBookItem book)
            book.prepareLectern(stack,source instanceof Player player?player:null);
    }
}
