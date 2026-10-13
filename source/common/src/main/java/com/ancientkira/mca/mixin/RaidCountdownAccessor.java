package com.ancientkira.mca.mixin;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Raid.class)
public interface RaidCountdownAccessor {
    @Accessor("raidCooldownTicks") void mca$setRaidCooldownTicks(int ticks);
}
