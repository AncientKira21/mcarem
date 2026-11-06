package com.ancientkira.mca.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
@Mixin(TrapDoorBlock.class)
public interface TrapDoorBlockAccessor {
    @Invoker("playSound") void mca$playSound(Player player, Level level, BlockPos pos, boolean opening);
}
