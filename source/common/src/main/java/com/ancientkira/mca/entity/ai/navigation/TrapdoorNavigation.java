package com.ancientkira.mca.entity.ai.navigation;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.brain.tasks.SmarterOpenDoorsTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.HashSet;

public final class TrapdoorNavigation {
    private TrapdoorNavigation() {}
    public static boolean canOpen(BlockGetter world,BlockPos pos) {
        var state=world.getBlockState(pos);
        if(!(state.getBlock() instanceof TrapDoorBlock) || !state.is(BlockTags.WOODEN_TRAPDOORS)
                || state.getValue(BlockStateProperties.POWERED) || !world.getFluidState(pos).isEmpty())return false;
        // Open an obstruction, never a bridge hatch covering a dangerous fall or liquid.
        for(int down=1;down<=2;down++) {
            BlockPos below=pos.below(down);
            if(!world.getFluidState(below).isEmpty())return false;
            if(world.getBlockState(below).is(BlockTags.CLIMBABLE))return true;
            if(world.getBlockState(below).isFaceSturdy(world,below,Direction.UP))return true;
        }
        return false;
    }
    public static void tick(VillagerEntityMCA person) {
        if(!(person.level() instanceof ServerLevel world))return;
        var path=person.getNavigation().getPath();
        if(path==null || path.isDone())return;
        int end=Math.min(path.getNodeCount(),path.getNextNodeIndex()+3);
        for(int i=path.getNextNodeIndex();i<end;i++) {
            BlockPos feet=path.getNode(i).asBlockPos();
            for(BlockPos pos:new BlockPos[]{feet,feet.above()}) {
                if(!pos.closerToCenterThan(person.position(),2.5) || !canOpen(world,pos))continue;
                var state=world.getBlockState(pos);
                if(!state.getValue(BlockStateProperties.OPEN) && SmarterOpenDoorsTask.setOpen(person,world,state,pos,true)) {
                    var doors=new HashSet<>(person.getBrain().getMemoryInternal(MemoryModuleType.DOORS_TO_CLOSE).orElseGet(HashSet::new));
                    doors.add(GlobalPos.of(world.dimension(),pos));
                    person.getBrain().setMemory(MemoryModuleType.DOORS_TO_CLOSE,doors);
                }
            }
        }
    }
}
