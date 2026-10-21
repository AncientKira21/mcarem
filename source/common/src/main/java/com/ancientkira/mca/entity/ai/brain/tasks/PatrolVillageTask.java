package com.ancientkira.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.MemoryModuleTypeMCA;
import com.ancientkira.mca.util.BlockBoxExtended;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class PatrolVillageTask extends Behavior<VillagerEntityMCA> {
    private final int completionRange;
    private final float speed;

    public PatrolVillageTask(int completionRange, float speed) {
        super(ImmutableMap.of(
                MemoryModuleTypeMCA.PLAYER_FOLLOWING, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.INTERACTION_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED));
        this.completionRange = completionRange;
        this.speed = speed;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA entity) {
        return entity.getResidency().getEquipmentVillage().isPresent()
               && !InteractTask.shouldRun(entity);
    }

    @Override
    protected void start(ServerLevel serverWorld, VillagerEntityMCA villager, long l) {
        getNextPosition(villager).ifPresent(pos -> BehaviorUtils.setWalkAndLookTargetMemories(villager, pos, speed, completionRange));
    }

    public static void keepRoutinePatrolInside(VillagerEntityMCA person) {
        if (!person.isGuard() || person.isNoAi() || person.isKidnapped() || person.tickCount%10!=0
                || person.getVillagerBrain().getMoveState()!=com.ancientkira.mca.entity.ai.MoveState.MOVE
                || person.getVillagerBrain().isPanicking() || person.isInWater() || person.isInLava() || person.isOnFire()
                || person.getBrain().hasMemoryValue(MemoryModuleType.INTERACTION_TARGET)
                || com.ancientkira.mca.server.CaptiveRescue.isHelping(person)
                || com.ancientkira.mca.server.ClericAid.isHelping(person)) return;
        var home=person.getResidency().getEquipmentVillage();
        if(home.isEmpty() || com.ancientkira.mca.server.VillageWarfare.get((ServerLevel)person.level()).isBusy(home.get().getId()))return;
        var village=home.get();
        var target=person.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
        if(target!=null && target.isPresent()) {
            if(com.ancientkira.mca.entity.ai.brain.VillagerTasksMCA.canPursueFromVillage(person,target.get()))return;
            person.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            person.setTarget(null);
        }
        if(person.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY_ENTITY)
                && village.isWithinBorder(person.blockPosition(),16))return;
        var walk=person.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET);
        boolean outside=!village.isWithinBorder(person.blockPosition(),0);
        if(outside && walk!=null && walk.isPresent()) {
            BlockPos destination=walk.get().getTarget().currentBlockPosition();
            if(village.isWithinBorder(destination,0)
                    || borderDistance(destination,village.getBox())<borderDistance(person.blockPosition(),village.getBox()))return;
        }
        if(!outside && (walk==null || walk.isEmpty() || village.isWithinBorder(walk.get().getTarget().currentBlockPosition(),0)))return;
        person.getNavigation().stop();
        person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        person.getBrain().eraseMemory(MemoryModuleType.PATH);
        person.getMoveControl().setWantedPosition(person.getX(),person.getY(),person.getZ(),0);
        person.setXxa(0);person.setZza(0);
        if(outside)new PatrolVillageTask(1,0.4F).getNextPosition(person).ifPresent(pos->
                BehaviorUtils.setWalkAndLookTargetMemories(person,pos,0.4F,1));
    }

    private static long borderDistance(BlockPos pos, BlockBoxExtended box){
        long dx=Math.max(0,Math.max(box.minX()-pos.getX(),pos.getX()-box.maxX()));
        long dz=Math.max(0,Math.max(box.minZ()-pos.getZ(),pos.getZ()-box.maxZ()));
        return dx*dx+dz*dz;
    }

    private Optional<BlockPos> getNextPosition(VillagerEntityMCA villager) {
        return villager.getResidency().getEquipmentVillage().map(village -> {
            BlockBoxExtended box = village.getBox();
            int x = box.minX() + villager.getRandom().nextInt(box.getXSpan());
            int z = box.minZ() + villager.getRandom().nextInt(box.getZSpan());
            Vec3 targetPos = new Vec3(x, box.getCenter().getY(), z);

            for(int attempt=0;attempt<10;attempt++){
                Vec3 candidate=DefaultRandomPos.getPosTowards(villager,32,16,targetPos,Math.PI*0.5);
                if(candidate==null)continue;
                BlockPos feet=BlockPos.containing(candidate);
                boolean outside=!village.isWithinBorder(villager.blockPosition(),0);
                if((!village.isWithinBorder(feet,0) && (!outside || borderDistance(feet,box)>=borderDistance(villager.blockPosition(),box)))
                        || !com.ancientkira.mca.entity.ai.navigation.TerrainSafety.solidFloor(villager.level(),feet)
                        || !com.ancientkira.mca.entity.ai.navigation.TerrainSafety.clear(villager.level(),feet)
                        || com.ancientkira.mca.entity.ai.navigation.TerrainSafety.dangerousEdge(villager.level(),feet))continue;
                var path=villager.getNavigation().createPath(feet,completionRange);
                if(path!=null && path.canReach()) {
                    boolean inside=true;
                    if(village.isWithinBorder(villager.blockPosition(),0))
                        for(int n=0;n<path.getNodeCount();n++){
                            var node=path.getNode(n);
                            if(!village.isWithinBorder(new BlockPos(node.x,node.y,node.z),0)){inside=false;break;}
                        }
                    if(inside)return candidate;
                }
            }
            return (Vec3)null;
        }).map(BlockPos::containing);
    }
}
