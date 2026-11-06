package com.ancientkira.mca.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.properties.WoodType;
@Mixin(FenceGateBlock.class)
public interface FenceGateBlockAccessor {
    @Accessor("type") WoodType mca$woodType();
}
