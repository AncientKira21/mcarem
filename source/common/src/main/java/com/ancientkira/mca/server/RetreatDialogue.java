package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** One taunt when an actual player attacker retreats from an ongoing pursuit. */
public final class RetreatDialogue {
    private static final Map<VillagerEntityMCA, Pursuit> PURSUITS = new WeakHashMap<>();
    private RetreatDialogue() { }

    private static final class Pursuit {
        final UUID attacker;
        double nearest;
        boolean spoken;
        Pursuit(Player player, double distance) { attacker = player.getUUID(); nearest = distance; }
    }

    public static void tick(VillagerEntityMCA villager) {
        if (villager.tickCount % 20 != 0) return;
        var target = villager.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (!(target instanceof Player player) || !player.isAlive() || player.isSpectator() || player.isCreative()
                || player.level() != villager.level() || !villager.isAlive() || villager.isHostile()
                || villager.isNoAi() || villager.isKidnapped() || villager.getVillagerBrain().isPanicking()
                || villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.HIT_BY_PLAYER)
                    .filter(attacker -> attacker == player).isEmpty()) {
            PURSUITS.remove(villager);
            return;
        }
        double distance = villager.distanceTo(player);
        Pursuit pursuit = PURSUITS.get(villager);
        if (pursuit == null || !pursuit.attacker.equals(player.getUUID())) {
            PURSUITS.put(villager, new Pursuit(player, distance));
            return;
        }
        pursuit.nearest = Math.min(pursuit.nearest, distance);
        if (!pursuit.spoken && distance >= 12 && distance <= 20 && distance - pursuit.nearest >= 4
                && villager.getSensing().hasLineOfSight(player)) {
            pursuit.spoken = true;
            villager.sendChatMessage(player, "villager.ranaway");
        }
    }
}
