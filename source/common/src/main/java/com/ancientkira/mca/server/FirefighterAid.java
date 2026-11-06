package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.registry.ProfessionsMCA;
import com.ancientkira.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

public final class FirefighterAid {
    private FirefighterAid() {}
    public static boolean isHelping(VillagerEntityMCA person) {
        return person.getProfession() == ProfessionsMCA.FIREFIGHTER
                && (person.getWaterClutchState().getBooleanOr("FireWater",false)
                    || person.getWaterClutchState().getBooleanOr("FireTarget",false)
                    || person.getWaterClutchState().getBooleanOr("FireRefilling",false));
    }
    public static void tick(VillagerEntityMCA person) {
        if (!(person.level() instanceof ServerLevel world) || !person.isAlive()) return;
        var state = person.getWaterClutchState();
        FirefighterExtinguisher.restoreHand(person,world);
        if (state.contains("FireSpongeUntil") && world.getGameTime() >= state.getLongOr("FireSpongeUntil",0)) {
            BlockPos sponge = new BlockPos(state.getIntOr("FireX",0),state.getIntOr("FireY",0),state.getIntOr("FireZ",0));
            if (world.getBlockState(sponge).is(Blocks.SPONGE) || world.getBlockState(sponge).is(Blocks.WET_SPONGE))
                world.setBlock(sponge,Blocks.AIR.defaultBlockState(),3);
            state.remove("FireSpongeUntil");
        }
        if (state.getBooleanOr("FireWater", false)) {
            BlockPos water = new BlockPos(state.getIntOr("FireX",0), state.getIntOr("FireY",0), state.getIntOr("FireZ",0));
            if (!state.getStringOr("FireDimension", "").equals(world.dimension().identifier().toString())) { state.remove("FireWater"); return; }
            if (world.getGameTime() < state.getLongOr("FireCollect",0)) {
                if (!person.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
                    person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);person.getNavigation().stop();
                }
                return;
            }
            if (person.distanceToSqr(water.getX()+.5,water.getY()+.5,water.getZ()+.5) > 16) {
                if (!com.ancientkira.mca.entity.ai.brain.VillagerTasksMCA.hasMovementOrder(person))
                    person.getBrain().setMemory(MemoryModuleType.WALK_TARGET,new WalkTarget(water,.75F,2));
                return;
            }
            boolean burning = false;
            for (BlockPos nearby : BlockPos.betweenClosed(water.offset(-4,-2,-4),water.offset(4,2,4))) {
                if (world.getBlockState(nearby).getBlock() instanceof BaseFireBlock) { burning = true; break; }
            }
            if (burning && world.getGameTime() < state.getLongOr("FireDeadline",0)) {
                state.putLong("FireCollect",world.getGameTime()+20);
                return;
            }
            if (world.getBlockState(water).is(Blocks.WATER)) {
                // A real sponge absorbs the source and its connected flowing water.
                world.setBlock(water,Blocks.SPONGE.defaultBlockState(),3);
                state.putLong("FireSpongeUntil",world.getGameTime()+10);
                person.swing(person.getDominantHand());
            }
            state.remove("FireWater"); state.remove("FireTarget");
            person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET); person.getNavigation().stop();
            return;
        }
        if (person.getProfession() != ProfessionsMCA.FIREFIGHTER || person.isNoAi() || person.isKidnapped()
                || InfirmaryCare.isPatient(person) || person.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                || !VillagerGriefing.allowed(person)) return;
        boolean useExtinguisher=com.ancientkira.mca.compat.FireExtinguisherCompat.available();
        ItemStack extinguisher=useExtinguisher?FirefighterExtinguisher.equipment(person):ItemStack.EMPTY;
        if(useExtinguisher && FirefighterExtinguisher.refill(person,world,extinguisher))return;
        int bucket = InventoryUtils.getFirstSlotContainingItem(person.getInventory(), s -> s.is(Items.WATER_BUCKET));
        BlockPos fire = null;
        if (state.getBooleanOr("FireTarget", false)) {
            var saved = new BlockPos(state.getIntOr("TargetX",0),state.getIntOr("TargetY",0),state.getIntOr("TargetZ",0));
            if (world.hasChunkAt(saved) && FirefighterSearch.fire(world.getBlockState(saved))) fire = saved;
            else state.remove("FireTarget");
        }
        if (fire == null) {
            fire=FirefighterSearch.fire(person,world);
            if(fire!=null){state.putBoolean("FireTarget",true);state.putInt("TargetX",fire.getX());state.putInt("TargetY",fire.getY());state.putInt("TargetZ",fire.getZ());}
        }
        if (fire == null) return;
        if (person.distanceToSqr(fire.getX()+.5,fire.getY()+.5,fire.getZ()+.5) > 16) {
            if (!com.ancientkira.mca.entity.ai.brain.VillagerTasksMCA.hasMovementOrder(person)) {
                if (person.isSleeping()) person.stopSleeping();
                person.getBrain().setMemory(MemoryModuleType.WALK_TARGET,new WalkTarget(fire,.75F,2));
            }
            return;
        }
        if(useExtinguisher) {
            FirefighterExtinguisher.spray(person,world,fire,extinguisher);
            return;
        }
        if (world.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES,fire)) return;
        if (world.setBlock(fire,Blocks.WATER.defaultBlockState(),3)) {
            if (bucket >= 0) person.getInventory().setItem(bucket,new ItemStack(Items.BUCKET));
            state.putBoolean("FireWater",true);state.putInt("FireX",fire.getX());state.putInt("FireY",fire.getY());state.putInt("FireZ",fire.getZ());
            state.putString("FireDimension",world.dimension().identifier().toString());state.putLong("FireCollect",world.getGameTime()+30);state.putLong("FireDeadline",world.getGameTime()+200);
            person.playSound(SoundEvents.BUCKET_EMPTY,1,1);person.swing(person.getDominantHand());
            person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);person.getNavigation().stop();
        }
    }
}
