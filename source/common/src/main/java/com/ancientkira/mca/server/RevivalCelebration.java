package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.MemoryModuleTypeMCA;
import com.ancientkira.mca.server.world.data.FamilyTree;
import net.minecraft.util.Util;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import java.util.UUID;

/** Persist gratitude on family records so unloaded relatives can celebrate on their return. */
public final class RevivalCelebration {
    private RevivalCelebration() {}

    public static void complete(Entity revived, UUID rescuer) {
        if (!(revived.level() instanceof ServerLevel level)) return;
        var tree = FamilyTree.get(level);
        UUID victim = revived.getUUID();
        tree.getOrEmpty(victim).ifPresent(node -> node.recordReviver(rescuer));
        tree.getAllEntries().filter(node -> !node.isPlayer() && !node.isDeceased()).forEach(node -> {
            if (node.id().equals(victim) || node.isRelative(victim) || node.partner().equals(victim)) {
                node.queueRevivalThanks(victim, rescuer, node.id().equals(victim) ? 10 : 5);
            }
        });
        for (var world : level.getServer().getAllLevels()) {
            for (var pos : com.ancientkira.mca.server.world.data.GraveyardManager.get(world)
                    .findAll(com.ancientkira.mca.server.world.data.GraveyardManager.TombstoneState.FILLED)) {
                if (world.hasChunkAt(pos) && world.getBlockEntity(pos) instanceof com.ancientkira.mca.block.TombstoneBlock.Data grave
                        && grave.storesEntity(victim)) grave.cleanupAfterRevival();
            }
            for (Entity entity : world.getAllEntities()) {
                if (entity instanceof VillagerEntityMCA person) {
                    var node = person.getRelationships().getFamilyEntry();
                    boolean mourning = person.getBrain().getMemory(MemoryModuleTypeMCA.MOURNING_PERSON)
                            .filter(victim::equals).isPresent();
                    if (mourning && !node.id().equals(victim) && !node.isRelative(victim)
                            && !node.partner().equals(victim)) node.queueRevivalThanks(victim, rescuer, 5);
                    tick(person);
                }
            }
        }
    }

    public static void tick(VillagerEntityMCA person) {
        if (!(person.level() instanceof ServerLevel level)) return;
        var node = person.getRelationships().getFamilyEntry();
        var events = node.revivalThanks();
        person.getBrain().getMemory(MemoryModuleTypeMCA.MOURNING_PERSON).ifPresent(victim -> {
            FamilyTree.get(level).getOrEmpty(victim).filter(dead -> !dead.isDeceased()
                    && dead.lastRevivalRescuer() != null).ifPresent(returned -> {
                boolean queued = java.util.stream.IntStream.range(0, events.size()).anyMatch(i ->
                        events.get(i) instanceof CompoundTag event
                        && event.read("victim", UUIDUtil.CODEC).filter(victim::equals).isPresent());
                if (!queued) node.queueRevivalThanks(victim, returned.lastRevivalRescuer(), 5);
            });
        });
        for (int i = events.size() - 1; i >= 0; i--) {
            if (!(events.get(i) instanceof CompoundTag event)) continue;
            UUID victim = event.read("victim", UUIDUtil.CODEC).orElse(Util.NIL_UUID);
            var brain = person.getBrain();
            if (!event.getBoolean("celebrated").orElse(false)) {
                person.getVillagerBrain().modifyMoodValue(Math.max(0, 10 - person.getVillagerBrain().getMoodValue()));
                var mourning = brain.getMemory(MemoryModuleTypeMCA.MOURNING_PERSON);
                if (person.getUUID().equals(victim) || mourning.isEmpty() || mourning.filter(victim::equals).isPresent()) {
                    brain.eraseMemory(MemoryModuleTypeMCA.MOURNING_PERSON);
                    brain.eraseMemory(MemoryModuleTypeMCA.MOURNING_SITE);
                    brain.eraseMemory(MemoryModuleTypeMCA.MOURNING_POSITION);
                    person.getVillagerBrain().justGrieved();
                    brain.eraseMemory(MemoryModuleType.PATH);
                    brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                    brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
                    brain.setActiveActivityIfPossible(Activity.IDLE);
                }
                event.putBoolean("celebrated", true);
                node.getRoot().setDirty();
            }
            UUID rescuer = event.read("rescuer", UUIDUtil.CODEC).orElse(Util.NIL_UUID);
            var player = level.getServer().getPlayerList().getPlayer(rescuer);
            if (player != null) person.getVillagerBrain().rewardHearts(player, event.getInt("hearts").orElse(5));
            if (player != null || rescuer.equals(Util.NIL_UUID)) {
                events.remove(i);
                node.getRoot().setDirty();
            }
        }
    }
}
