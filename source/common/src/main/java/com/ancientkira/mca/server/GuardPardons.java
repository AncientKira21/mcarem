package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.MemoryModuleTypeMCA;
import com.ancientkira.mca.registry.ProfessionsMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import java.util.Comparator;

public final class GuardPardons {
    private GuardPardons() { }
    public static void tick(VillagerEntityMCA outlaw) {
        if (!(outlaw.level() instanceof ServerLevel world) || outlaw.tickCount % 100 != 0
                || outlaw.getProfession() != ProfessionsMCA.OUTLAW || !outlaw.isAlive()
                || outlaw.isKidnapped() || outlaw.isNoAi()
                || outlaw.getLastHurtMobTimestamp() > 0 && outlaw.tickCount - outlaw.getLastHurtMobTimestamp() < 1200
                || outlaw.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                || outlaw.getLongTermMemory().hasMemory("guardPardonCheck")) return;
        var guard = world.getEntitiesOfClass(VillagerEntityMCA.class, outlaw.getBoundingBox().inflate(16),
                        person -> person.getProfession() == ProfessionsMCA.GUARD && person.isAlive()
                                && !person.isKidnapped() && !person.isNoAi() && !person.isSleeping()
                                && person.hasLineOfSight(outlaw) && person.distanceToSqr(outlaw) <= 256)
                .stream().min(Comparator.comparingDouble(outlaw::distanceToSqr));
        if (guard.isEmpty()) return;
        outlaw.getLongTermMemory().remember("guardPardonCheck", 24000);
        if (world.getRandom().nextFloat() >= .1F) return;
        outlaw.setProfession(BuiltInRegistries.VILLAGER_PROFESSION.getValueOrThrow(VillagerProfession.NONE));
        outlaw.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        outlaw.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
        outlaw.setLastHurtByMob(null);
        for (var entity : world.getAllEntities()) {
            if (!(entity instanceof VillagerEntityMCA defender) || !defender.isGuard()) continue;
            if (defender.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).filter(outlaw::equals).isPresent()) {
                defender.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                defender.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                defender.getNavigation().stop();
            }
            if (defender.getBrain().getMemoryInternal(MemoryModuleType.NEAREST_HOSTILE).filter(outlaw::equals).isPresent())
                defender.getBrain().eraseMemory(MemoryModuleType.NEAREST_HOSTILE);
            if (defender.getLastHurtByMob() == outlaw) defender.setLastHurtByMob(null);
            if (defender.getBrain().getMemoryInternal(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY).filter(outlaw::equals).isPresent())
                defender.getBrain().eraseMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY);
            if (defender.getBrain().getMemoryInternal(MemoryModuleType.HURT_BY_ENTITY).filter(outlaw::equals).isPresent())
                defender.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
        }
        Component message = Component.translatable("mca.outlaw.pardoned", guard.get().getName(), outlaw.getName());
        outlaw.getResidency().getRegistryVillage().flatMap(v -> v.getCivilRegistry()).ifPresent(registry -> registry.addText(message));
        world.getServer().getPlayerList().broadcastSystemMessage(message, false);
    }
}
