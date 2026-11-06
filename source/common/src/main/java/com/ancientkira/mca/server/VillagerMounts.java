package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.MoveState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public final class VillagerMounts {
    private VillagerMounts() {}
    public static boolean saddled(Entity entity) { return entity instanceof Mob mob && mob.isSaddled(); }
    public static boolean hasVillager(Entity mount) {
        return mount.getPassengers().stream().anyMatch(VillagerEntityMCA.class::isInstance);
    }
    public static boolean sharesSeat(Entity mount,Entity passenger) {
        return saddled(mount) && (passenger instanceof VillagerEntityMCA || hasVillager(mount))
                && (passenger instanceof VillagerEntityMCA || passenger instanceof Player);
    }
    public static void tick(Mob mount) {
        if(mount.level().isClientSide() || !mount.isSaddled()
                || !(mount.getControllingPassenger() instanceof VillagerEntityMCA rider))return;
        if(rider.getVillagerBrain().getMoveState()==MoveState.STAY) {
            mount.getNavigation().stop();mount.setSpeed(0);mount.setXxa(0);mount.setZza(0);
            return;
        }
        var destination=rider.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET);
        if(destination.isEmpty())return;
        var target=destination.get().getTarget().currentBlockPosition();
        if(mount.blockPosition().distSqr(target)<=Math.max(1,destination.get().getCloseEnoughDist()*destination.get().getCloseEnoughDist())) {
            mount.getNavigation().stop();return;
        }
        if(mount.tickCount%10!=0 && !mount.getNavigation().isDone())return;
        if(mount instanceof net.minecraft.world.entity.animal.camel.Camel camel && camel.isCamelSitting())camel.standUp();
        mount.getNavigation().moveTo(target.getX()+.5,target.getY(),target.getZ()+.5,destination.get().getSpeedModifier());
    }
}
