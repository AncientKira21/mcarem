package com.ancientkira.mca.mixin;

import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.UUID;

@Mixin(ZombieVillager.class)
public interface ZombieVillagerAccessor {
    @Invoker("startConverting")
    void mca$startConverting(@Nullable UUID conversionStarter, int villagerConversionTime);
}
