package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.network.c2s.VillagerReviverActionMessage;
import com.ancientkira.mca.server.world.data.FamilyTree;
import com.ancientkira.mca.server.world.data.FamilyTreeNode;
import com.ancientkira.mca.server.world.data.VillageManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Process small batches so bulk revival does not monopolize a server tick. */
public final class BulkRevival {
    private static final Map<MinecraftServer, ArrayDeque<Job>> JOBS = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private BulkRevival() { }
    public static void enqueue(ServerPlayer player, boolean villageOnly) {
        var server = player.level().getServer();
        var queue = JOBS.computeIfAbsent(server, key -> new ArrayDeque<>());
        if (queue.stream().anyMatch(job -> job.player.equals(player.getUUID()))) return;
        var village = villageOnly ? VillageManager.get(player.level()).findNearestVillage(player).orElse(null) : null;
        if (villageOnly && village == null) {
            player.sendSystemMessage(Component.translatable("gui.villager_reviver.no_village"), true);
            return;
        }
        var tree = FamilyTree.get(player.level());
        List<UUID> ids = tree.getAllEntries().filter(node -> !node.isPlayer() && node.isDeceased())
                .filter(node -> !villageOnly || belongsToVillage(node, village.getId(), player.level().dimension().identifier().toString()))
                .map(FamilyTreeNode::id).toList();
        queue.add(new Job(player.getUUID(), new ArrayDeque<>(ids)));
    }
    public static boolean belongsToVillage(FamilyTreeNode node, int village, String dimension) {
        return node.getRevivalData().map(data -> {
            int recorded = data.getIntOr("MCARevivalVillage", data.getCompound(VillagerEntityMCA.MCA_DATA_KEY)
                    .map(nbt -> nbt.getIntOr("HomeVillage", -1)).orElse(-1));
            String recordedDimension = data.getStringOr("MCARevivalDimension", "minecraft:overworld");
            return recorded == village && dimension.equals(recordedDimension);
        }).orElse(false);
    }
    public static void tick(MinecraftServer server) {
        var queue = JOBS.get(server);
        if (queue == null || queue.isEmpty()) return;
        Job job = queue.peek();
        ServerPlayer player = server.getPlayerList().getPlayer(job.player);
        if (player == null) { queue.remove(); return; }
        var tree = FamilyTree.get(player.level());
        for (int count = 0; count < 5 && !job.ids.isEmpty(); count++) {
            UUID id = job.ids.remove();
            var node = tree.getOrEmpty(id).orElse(null);
            if (node == null || !node.isDeceased() || node.isPlayer()) continue;
            try {
                new VillagerReviverActionMessage(id, VillagerReviverActionMessage.Action.REVIVE)
                        .reviveSingle(player, true, job.processed++);
                if (!node.isDeceased()) job.revived++; else job.failed++;
            } catch (RuntimeException exception) {
                job.failed++;
                com.ancientkira.mca.MCA.LOGGER.error("Bulk revival failed for {}", id, exception);
            }
        }
        if (job.ids.isEmpty()) {
            player.sendSystemMessage(Component.translatable("gui.villager_reviver.bulk_done", job.revived, job.failed), false);
            queue.remove();
        }
    }
    private static final class Job {
        final UUID player;
        final ArrayDeque<UUID> ids;
        int processed, revived, failed;
        Job(UUID player, ArrayDeque<UUID> ids) { this.player = player; this.ids = ids; }
    }
}
