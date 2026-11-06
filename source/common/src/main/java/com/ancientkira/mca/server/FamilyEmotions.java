package com.ancientkira.mca.server;

import com.ancientkira.mca.MCA;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.VillagerLike;
import com.ancientkira.mca.server.world.data.FamilyTree;
import com.ancientkira.mca.server.world.data.FamilyTreeNode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import java.util.UUID;

/** Family loss and reunions use elapsed world ticks, including time while relatives are unloaded. */
public final class FamilyEmotions {
    public static final long FIVE_DAYS=5L*24000L;
    private FamilyEmotions() {}
    public static long now(){return MCA.getServer().map(s->s.overworld().getGameTime()).orElse(0L);}
    public static int state(CompoundTag data,long time){
        var grief=data.getCompound("grief").orElseGet(CompoundTag::new);
        if(grief.keySet().stream().anyMatch(key->grief.getLongOr(key,0L)>time))return 1;
        return data.getLongOr("upbeatUntil",0L)>time?2:0;
    }
    public static int lockedMood(VillagerLike<?> person,int proposed){
        int state=person.getTrackedValue(VillagerLike.FAMILY_MOOD);
        if(person.asEntity().level() instanceof ServerLevel level)
            state=FamilyTree.get(level).getOrEmpty(person.asEntity().getUUID()).map(n->state(n.familyEmotions(),now())).orElse(0);
        return state==1?-15:state==2?15:proposed;
    }
    public static void overrideMood(VillagerLike<?> person, int mood) {
        if (!(person.asEntity().level() instanceof ServerLevel level)) return;
        FamilyTree.get(level).getOrEmpty(person.asEntity().getUUID()).ifPresent(node -> {
            node.familyEmotions().remove("grief");
            node.familyEmotions().remove("upbeatUntil");
            node.getRoot().setDirty();
        });
        person.setTrackedValue(VillagerLike.FAMILY_MOOD, 0);
        person.getVillagerBrain().setMoodValue(mood);
    }

    public static void death(FamilyTreeNode dead){
        dead.familyEmotions().putBoolean("revivalProcessed",false);
        long until=now()+FIVE_DAYS;
        dead.getRoot().getAllEntries().filter(n->!n.isDeceased() && !n.id().equals(dead.id())
                && (n.isFamilyMember(dead.id()) || n.getWidowedPartner().equals(dead.id()))).forEach(n->{
            var data=n.familyEmotions();var grief=data.getCompound("grief").orElseGet(CompoundTag::new);
            grief.putLong(dead.id().toString(),until);data.put("grief",grief);
            // A new death supersedes an earlier celebration.
            data.remove("upbeatUntil");data.remove("reunions");data.remove("hugUntil");
            n.getRoot().setDirty();
        });
    }
    public static void revive(Entity returned){
        if(!(returned.level() instanceof ServerLevel level))return;
        var tree=FamilyTree.get(level);String id=returned.getUUID().toString();long until=now()+FIVE_DAYS;
        var returnedNode=tree.getOrEmpty(returned.getUUID()).orElse(null);if(returnedNode==null)return;
        if(returnedNode.familyEmotions().getBooleanOr("revivalProcessed",false))return;
        returnedNode.familyEmotions().putBoolean("revivalProcessed",true);returnedNode.getRoot().setDirty();
        boolean[] reunited={false};
        tree.getAllEntries().filter(n->!n.isDeceased()&&!n.id().equals(returned.getUUID())).forEach(n->{
            var data=n.familyEmotions();var grief=data.getCompound("grief").orElseGet(CompoundTag::new);
            if(!grief.contains(id)&&!n.isFamilyMember(returned.getUUID()))return;
            grief.remove(id);data.put("grief",grief);data.putLong("upbeatUntil",until);
            var queue=data.getCompound("reunions").orElseGet(CompoundTag::new);queue.putBoolean(id,true);data.put("reunions",queue);
            n.getRoot().setDirty();reunited[0]=true;
        });
        if(reunited[0]){returnedNode.familyEmotions().putLong("upbeatUntil",until);returnedNode.getRoot().setDirty();}
    }
    public static void tick(VillagerEntityMCA person){
        if(!(person.level() instanceof ServerLevel level))return;
        var node=person.getRelationships().getFamilyEntry();var data=node.familyEmotions();long time=now();
        int mood=state(data,time);person.setTrackedValue(VillagerLike.FAMILY_MOOD,mood);
        if(mood!=0)person.getVillagerBrain().setMoodValue(mood==1?-15:15);
        boolean hugging=data.getLongOr("hugUntil",0L)>time;
        person.setTrackedValue(VillagerLike.FAMILY_HUG,hugging);
        if(hugging){
            var partner=hugPartner(person,level);
            if(partner!=null && partner.isAlive() && person.distanceToSqr(partner)<1.44
                    && !person.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET))holdHug(person,partner);
            else {data.remove("hugUntil");data.remove("hugPartner");person.setTrackedValue(VillagerLike.FAMILY_HUG,false);}
            return;
        }
        var queue=data.getCompound("reunions").orElseGet(CompoundTag::new);
        for(String key:java.util.List.copyOf(queue.keySet())){
            UUID id;try{id=UUID.fromString(key);}catch(IllegalArgumentException e){queue.remove(key);continue;}
            var relative=level.getEntity(id);
            if(relative==null || !relative.isAlive() || relative.isRemoved())continue;
            if(relative instanceof VillagerEntityMCA other && (other.isSleeping() || other.isPassenger()
                    || other.getInteractions().getInteractingPlayer().isPresent()
                    || other.getRelationships().getFamilyEntry().familyEmotions().getLongOr("hugUntil",0L)>time
                    || other.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET)))continue;
            if(person.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET)
                    || person.isSleeping() || person.isPassenger() || person.getInteractions().getInteractingPlayer().isPresent())return;
            if(person.distanceToSqr(relative)>.81 || !person.hasLineOfSight(relative)){
                if(person.distanceToSqr(relative)<32*32)person.getNavigation().moveTo(relative,0.65);
                return;
            }
            data.putString("hugPartner",relative.getUUID().toString());
            holdHug(person,relative);data.putLong("hugUntil",time+60);
            person.setTrackedValue(VillagerLike.FAMILY_HUG,true);
            person.sendChatToAllAround("family.reunited",relative.getName());
            if(relative instanceof VillagerEntityMCA other){
                holdHug(other,person);
                var otherNode=other.getRelationships().getFamilyEntry();otherNode.familyEmotions().putLong("hugUntil",time+60);
                otherNode.familyEmotions().putString("hugPartner",person.getUUID().toString());
                otherNode.familyEmotions().getCompound("reunions").ifPresent(pending -> pending.remove(person.getUUID().toString()));
                other.setTrackedValue(VillagerLike.FAMILY_HUG,true);
                other.sendChatToAllAround("family.reunited",person.getName());otherNode.getRoot().setDirty();
            }
            queue.remove(key);data.put("reunions",queue);node.getRoot().setDirty();return;
        }
    }
    private static Entity hugPartner(VillagerEntityMCA person,ServerLevel level) {
        try {return level.getEntity(UUID.fromString(person.getRelationships().getFamilyEntry().familyEmotions().getStringOr("hugPartner","")));}
        catch(IllegalArgumentException ignored){return null;}
    }
    private static void holdHug(VillagerEntityMCA person,Entity partner) {
        person.getNavigation().stop();
        person.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
        person.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.PATH);
        person.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET,
                new net.minecraft.world.entity.ai.behavior.EntityTracker(partner,true));
        float yaw=(float)Math.toDegrees(Math.atan2(partner.getX()-person.getX(),partner.getZ()-person.getZ()))*-1;
        person.setYRot(yaw);person.setYBodyRot(yaw);person.setYHeadRot(yaw);
        person.getLookControl().setLookAt(partner,30,30);
    }
}
