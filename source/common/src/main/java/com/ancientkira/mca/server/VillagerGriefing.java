package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;

public final class VillagerGriefing {
    private VillagerGriefing() {}
    public static boolean allowed(VillagerEntityMCA person) {
        return person.level() instanceof ServerLevel world && (world.getGameRules().get(GameRules.MOB_GRIEFING)
                || person.getResidency().getHomeVillage().filter(v -> v.isClutchOverride()).isPresent()
                || com.ancientkira.mca.server.world.data.VillageManager.get(world).findNearestVillage(person.blockPosition(),0)
                        .filter(v -> v.isClutchOverride()).isPresent());
    }
}
