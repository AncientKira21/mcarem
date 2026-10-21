package com.ancientkira.mca.mixin;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Raid.class)
public interface RaidCountdownAccessor {
    @Accessor("center") void mca$setCenter(net.minecraft.core.BlockPos center);
    @Accessor("raidEvent") net.minecraft.server.level.ServerBossEvent mca$getRaidEvent();
    @Accessor("raidCooldownTicks") void mca$setRaidCooldownTicks(int ticks);
}
