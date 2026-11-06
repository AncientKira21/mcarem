package com.ancientkira.mca.server;

import com.ancientkira.mca.compat.FireExtinguisherCompat;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.brain.VillagerTasksMCA;
import com.ancientkira.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;

/** Uses the installed mod's spray logic, charge values and refill configuration. */
public final class FirefighterExtinguisher {
    private FirefighterExtinguisher() {}
    public static void restoreHand(VillagerEntityMCA person, ServerLevel world) {
        var state=person.getWaterClutchState();
        if (state.getBooleanOr("FireToolHandSaved",false) && world.getGameTime()>=state.getLongOr("FireToolUntil",0)) {
            ItemStack previous=state.read("FirePreviousHand",ItemStack.CODEC).orElse(ItemStack.EMPTY);
            person.setItemInHand(person.getDominantHand(),previous);
            state.remove("FireToolHandSaved");state.remove("FirePreviousHand");
        }
    }
    private static void hold(VillagerEntityMCA person, ItemStack tool, long until) {
        var state=person.getWaterClutchState();
        if(!state.getBooleanOr("FireToolHandSaved",false)) {
            state.putBoolean("FireToolHandSaved",true);
            var previous=person.getItemInHand(person.getDominantHand());
            if(!previous.isEmpty())state.store("FirePreviousHand",ItemStack.CODEC,previous.copy());
            else state.remove("FirePreviousHand");
        }
        person.setItemInHand(person.getDominantHand(),tool.copy());state.putLong("FireToolUntil",until);
    }
    public static ItemStack equipment(VillagerEntityMCA person) {
        int slot=InventoryUtils.getFirstSlotContainingItem(person.getInventory(),FireExtinguisherCompat::isExtinguisher);
        if(slot>=0)return person.getInventory().getItem(slot);
        return person.getWaterClutchState().read("FireExtinguisherEquipment",ItemStack.CODEC)
                .orElseGet(FireExtinguisherCompat::equipment);
    }
    private static void save(VillagerEntityMCA person, ItemStack tool) {
        // Real supplied tools remain real inventory items; only the automatic tool is stored here.
        int slot=InventoryUtils.getFirstSlotContainingItem(person.getInventory(),FireExtinguisherCompat::isExtinguisher);
        if(slot<0)person.getWaterClutchState().store("FireExtinguisherEquipment",ItemStack.CODEC,tool);
        person.getInventory().setChanged();
    }
    /** Return true while an empty extinguisher owns the firefighter's work. */
    public static boolean refill(VillagerEntityMCA person,ServerLevel world,ItemStack tool) {
        if(!FireExtinguisherCompat.empty(tool))return false;
        if(!FireExtinguisherCompat.canRefill())return true;
        var state=person.getWaterClutchState();
        BlockPos hydrant=null;
        if(state.getBooleanOr("FireRefilling",false)) {
            BlockPos saved=new BlockPos(state.getIntOr("HydrantX",0),state.getIntOr("HydrantY",0),state.getIntOr("HydrantZ",0));
            if(FireExtinguisherCompat.hydrant(world,saved) && world.getGameTime()<state.getLongOr("HydrantUntil",0))hydrant=saved;
            else state.remove("FireRefilling");
        }
        if(hydrant==null) {
            hydrant=FirefighterSearch.scan(person,world,true);
            if(hydrant==null)return true;
            state.putBoolean("FireRefilling",true);state.putInt("HydrantX",hydrant.getX());
            state.putInt("HydrantY",hydrant.getY());state.putInt("HydrantZ",hydrant.getZ());
            state.putLong("HydrantUntil",world.getGameTime()+400);
        }
        hold(person,tool,world.getGameTime()+10);
        if(person.distanceToSqr(hydrant.getX()+.5,hydrant.getY()+.5,hydrant.getZ()+.5)>9) {
            if(!VillagerTasksMCA.hasMovementOrder(person)) {
                if(person.isSleeping())person.stopSleeping();
                var previous=person.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET);
                if(previous.isEmpty() || !previous.get().getTarget().currentBlockPosition().equals(hydrant))
                    person.getBrain().setMemory(MemoryModuleType.WALK_TARGET,new WalkTarget(hydrant,.75F,1));
            }
            return true;
        }
        tool.setDamageValue(0);save(person,tool);
        hold(person,tool,world.getGameTime()+10);
        person.playSound(SoundEvents.BUCKET_FILL,1,1);person.swing(person.getDominantHand());
        world.sendParticles(ParticleTypes.SPLASH,hydrant.getX()+.5,hydrant.getY()+1,hydrant.getZ()+.5,12,.3,.2,.3,.1);
        state.remove("FireRefilling");state.putLong("FireSprayAfter",world.getGameTime()+20);
        person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);person.getNavigation().stop();
        return true;
    }
    public static void spray(VillagerEntityMCA person,ServerLevel world,BlockPos fire,ItemStack tool) {
        var state=person.getWaterClutchState();
        if(world.getGameTime()<state.getLongOr("FireSprayAfter",0))return;
        person.getLookControl().setLookAt(fire.getX()+.5,fire.getY()+.5,fire.getZ()+.5);
        hold(person,tool,world.getGameTime()+10);
        int extinguished=FireExtinguisherCompat.spray(world,fire);
        if(extinguished>0) {
            // Retain the depleted tool so it can be refilled instead of breaking/disappearing.
            if(tool.isDamageableItem())tool.setDamageValue(Math.min(tool.getMaxDamage(),tool.getDamageValue()+1));
            save(person,tool);hold(person,tool,world.getGameTime()+10);
            person.playSound(SoundEvents.FIRE_EXTINGUISH,1,1);person.swing(person.getDominantHand());
            world.sendParticles(ParticleTypes.CLOUD,fire.getX()+.5,fire.getY()+.5,fire.getZ()+.5,24,.4,.4,.4,.02);
        }
        state.putLong("FireSprayAfter",world.getGameTime()+FireExtinguisherCompat.cooldown());
        state.remove("FireTarget");
        person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);person.getNavigation().stop();
    }
}
