package com.ancientkira.mca.server;

import com.ancientkira.mca.server.world.data.Village;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.raid.Raid;
import java.util.Comparator;

/** Use actual settlement buildings, and retain the vanilla wave-spawn preparation. */
public final class RaidStarter {
    private RaidStarter() { }
    public static Raid start(ServerPlayer player, Village village) {
        return start(player, village, 300);
    }
    public static Raid start(ServerPlayer player, Village village, int preparationTicks) {
        var world = player.level();
        if (world.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL || player.isSpectator()) return null;
        var center = village == null ? player.blockPosition() : village.getBuildings().values().stream()
                .min(Comparator.comparingDouble(building -> building.getSourceBlock().distSqr(player.blockPosition())))
                .map(com.ancientkira.mca.server.world.data.Building::getSourceBlock).orElse(player.blockPosition());
        if (world.getRaids().getNearbyRaid(center, 128 * 128) != null) return null;
        var raid = world.getRaids().createOrExtendRaid(player, center);
        if (raid == null) return null;
        raid.setRaidOmenLevel(Math.max(1, raid.getRaidOmenLevel()));
        var access = (com.ancientkira.mca.mixin.RaidCountdownAccessor) (Object) raid;
        if (village != null && !raid.isStarted()) access.mca$setCenter(center);
        access.mca$setRaidCooldownTicks(Math.max(300, preparationTicks));
        access.mca$getRaidEvent().setVisible(true);
        access.mca$getRaidEvent().addPlayer(player);
        world.getRaids().setDirty();
        return raid;
    }
}
