package com.ancientkira.mca.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import java.util.Map;
import java.util.WeakHashMap;

/** First-join protection is server-owned and ends when Destiny is completed. */
public final class FirstJoinSetup {
    private static final Map<ServerPlayer, Boolean> ACTIVE = new WeakHashMap<>();
    private FirstJoinSetup() { }
    public static boolean active(ServerPlayer player) { return ACTIVE.containsKey(player); }
    public static boolean originalNoGravity(ServerPlayer player) { return ACTIVE.getOrDefault(player, player.isNoGravity()); }
    public static void begin(ServerPlayer player) {
        ACTIVE.putIfAbsent(player, player.isNoGravity()); player.setNoGravity(true);
    }
    public static void finish(ServerPlayer player) {
        Boolean gravity = ACTIVE.remove(player);
        if (gravity != null) player.setNoGravity(gravity);
        player.fallDistance = 0;
    }
    public static void tick(ServerPlayer player) {
        if (!active(player)) return;
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.fallDistance = 0; player.clearFire(); player.setAirSupply(player.getMaxAirSupply());
        if (player.tickCount % 10 != 0) return;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48))) {
            if (mob.getTarget() == player) { mob.setTarget(null); mob.getNavigation().stop(); }
            if (mob.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET) != null
                    && mob.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).filter(target -> target == player).isPresent())
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }
}
