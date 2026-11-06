package com.ancientkira.mca.server;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;
public final class AdoptionEffects {
    private AdoptionEffects() { }
    public static void apply(VillagerEntityMCA child, ServerPlayer parent) {
        var memory = child.getVillagerBrain().getMemoriesForPlayer(parent);
        memory.setHearts(Math.max(memory.getHearts(), com.ancientkira.mca.Config.getInstance().childInitialHearts));
        child.getLongTermMemory().forget("pendingAdoptionLetter");
        com.ancientkira.mca.registry.CriterionMCA.FAMILY.trigger(parent);
        var tree = com.ancientkira.mca.server.world.data.FamilyTree.get((net.minecraft.server.level.ServerLevel)child.level());
        tree.getOrEmpty(parent.getUUID()).flatMap(node -> tree.getOrEmpty(node.partner())).ifPresent(spouse -> {
            var partner = parent.level().getServer().getPlayerList().getPlayer(spouse.id());
            if (partner != null) {
                var other = child.getVillagerBrain().getMemoriesForPlayer(partner);
                other.setHearts(Math.max(other.getHearts(), com.ancientkira.mca.Config.getInstance().childInitialHearts));
            }
        });
    }
}
