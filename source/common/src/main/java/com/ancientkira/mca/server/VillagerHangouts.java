package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.MoveState;
import com.ancientkira.mca.entity.ai.relationship.AgeState;
import com.ancientkira.mca.entity.ai.navigation.TerrainSafety;
import com.ancientkira.mca.registry.ProfessionsMCA;
import com.ancientkira.mca.server.world.data.PlayerSaveData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Per-villager saved appointments and explicit location orders. No offline absence penalties. */
public final class VillagerHangouts {
    private static final long DAY=24000, GRACE=1000, DURATION=2400;
    private VillagerHangouts() { }
    public static final class Activity extends net.minecraft.world.entity.ai.behavior.Behavior<VillagerEntityMCA> {
        public Activity(){super(com.google.common.collect.ImmutableMap.of());}
        @Override protected boolean checkExtraStartConditions(ServerLevel world,VillagerEntityMCA p){var s=p.getHangoutState();return s.contains("Work")||s.contains("ReturnHome")||s.getBooleanOr("Active",false);}
        @Override protected boolean canStillUse(ServerLevel world,VillagerEntityMCA p,long time){return checkExtraStartConditions(world,p);}
        @Override protected void start(ServerLevel world,VillagerEntityMCA p,long time){control(p);}
        @Override protected void tick(ServerLevel world,VillagerEntityMCA p,long time){control(p);}
    }
    private static void control(VillagerEntityMCA p){
        if(p.isKidnapped()||p.getVillagerBrain().isPanicking()||p.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET))return;
        if(p.getHangoutState().getBooleanOr("Active",false)){
            if(p.isSleeping())p.stopSleeping();
            p.getBrain().setActiveActivityIfPossible(net.minecraft.world.entity.schedule.Activity.IDLE);
        }
        if(!available(p)||p.getVillagerBrain().getMoveState()!=MoveState.MOVE||p.getVillagerBrain().isPanicking()
                ||p.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET))return;
        var s=p.getHangoutState();
        BlockPos target=s.contains("Work")?BlockPos.of(s.getLongOr("Work",0)):s.contains("ReturnHome")?BlockPos.of(s.getLongOr("ReturnHome",0)):spot(p);
        if(p.distanceToSqr(Vec3.atBottomCenterOf(target))>4){p.moveTowards(target,.6F);return;}
        if(s.contains("Work")||s.contains("ReturnHome"))return;
        p.getNavigation().stop();p.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        p.getMoveControl().setWantedPosition(p.getX(),p.getY(),p.getZ(),0);p.setXxa(0);p.setZza(0);p.setDeltaMovement(0,p.getDeltaMovement().y,0);
    }
    public static long nextTime(long clock,String period){
        long start=switch(period){case "morning"->1000;case "afternoon"->7000;case "evening"->13000;default->throw new IllegalArgumentException(period);};
        long target=Math.floorDiv(clock,DAY)*DAY+start;
        return target<clock?target+DAY:target;
    }
    private static boolean available(VillagerEntityMCA p){
        return !InfirmaryCare.isPatient(p)&&!ClericAid.isHelping(p)&&p.isAlive()&&!p.isRemoved()&&!p.isKidnapped()&&!p.isNoAi()&&!p.isSleeping()&&!p.isInWater()&&!p.isInLava()&&!p.isOnFire()
                &&p.getAgeState()!=AgeState.BABY&&!ProfessionsMCA.isRaider(p.getProfession());
    }
    private static BlockPos spot(VillagerEntityMCA p){return BlockPos.of(p.getHangoutState().getLongOr("MeetingSpot",p.getHangoutState().getLongOr("Spot",p.blockPosition().asLong())));}
    private static boolean hasSpot(VillagerEntityMCA p){var s=p.getHangoutState();return (s.contains("MeetingSpot")||s.contains("Spot"))&&s.getStringOr("MeetingDimension",s.getStringOr("Dimension","")).equals(p.level().dimension().identifier().toString());}
    private static boolean safe(VillagerEntityMCA p,BlockPos pos){return p.level().hasChunkAt(pos)&&TerrainSafety.solidFloor(p.level(),pos)&&TerrainSafety.clear(p.level(),pos)&&!TerrainSafety.dangerousEdge(p.level(),pos);}
    private static boolean ensureSpot(VillagerEntityMCA p){
        if(hasSpot(p))return safe(p,spot(p));
        for(BlockPos pos:BlockPos.betweenClosed(p.blockPosition().offset(-3,-1,-3),p.blockPosition().offset(3,1,3))){
            if(safe(p,pos)){saveSpot(p,pos);return true;}
        }
        return false;
    }
    private static void saveSpot(VillagerEntityMCA p,BlockPos pos){var s=p.getHangoutState();s.putLong("Spot",pos.asLong());s.putString("Dimension",p.level().dimension().identifier().toString());}
    public static boolean handle(VillagerEntityMCA p,ServerPlayer player,String command,String arg){
        if(!Set.of("sethangout","hangouttime","removehangouttime","gohangout","endhangout","gotowork").contains(command))return false;
        if(p.distanceToSqr(player)>64||!available(p))return true;
        var s=p.getHangoutState();
        switch(command){
            case "sethangout" -> {
                if(!safe(p,player.blockPosition())){p.sendChatMessage(player,"mca.hangout.unsafe");break;}
                saveSpot(p,player.blockPosition());p.sendChatMessage(player,"mca.hangout.spot_set");
            }
            case "hangouttime" -> {
                if(!Set.of("morning","afternoon","evening").contains(arg)||!hasSpot(p)){p.sendChatMessage(player,"mca.hangout.no_spot");break;}
                schedule(p,player.getUUID(),arg,true,false);p.sendChatMessage(Component.translatable("mca.hangout.scheduled",Component.translatable("mca.hangout."+arg)),player);
            }
            case "removehangouttime" -> {finish(p,false);clearAppointment(s);p.sendChatMessage(player,"mca.hangout.removed");}
            case "gohangout" -> {
                if(!hasSpot(p)){p.sendChatMessage(player,"mca.hangout.no_spot");break;}
                p.getVillagerBrain().abandonJob();p.getVillagerBrain().setMoveState(MoveState.MOVE,player);
                boolean recurring=s.getBooleanOr("Recurring",false);String period=s.getStringOr("Period","morning");
                clearAppointment(s);s.putBoolean("Recurring",recurring);s.putString("Period",period);
                s.putString("Partner",player.getUUID().toString());s.putLong("Due",p.level().getOverworldClockTime());
                s.putBoolean("Accepted",true);s.putBoolean("Active",true);s.putBoolean("Immediate",true);p.sendChatMessage(player,"mca.hangout.going");
            }
            case "endhangout" -> {finish(p,true);p.sendChatMessage(player,"mca.hangout.ended");}
            case "gotowork" -> {
                if(p.getProfession()==ProfessionsMCA.ADVENTURER||p.getProfession()==ProfessionsMCA.WANDERING_TRADER){p.sendChatMessage(player,"mca.traveler.refuse_work");break;}
                p.getBrain().getMemoryInternal(MemoryModuleType.JOB_SITE).filter(job->job.dimension().equals(p.level().dimension())).ifPresentOrElse(job->{
                    finish(p,false);p.getVillagerBrain().abandonJob();p.getVillagerBrain().setMoveState(MoveState.MOVE,player);
                    s.putLong("Work",job.pos().asLong());s.putLong("WorkUntil",p.level().getGameTime()+2400);p.sendChatMessage(player,"mca.hangout.work");
                },()->p.sendChatMessage(player,"mca.hangout.no_work"));
            }
        }
        return true;
    }
    private static void clearAppointment(CompoundTag s){for(String key:List.of("Partner","Due","Period","Recurring","Pending","Accepted","Active","Arrived","Attended","Immediate","Npc","MeetingSpot","MeetingDimension"))s.remove(key);}
    private static void schedule(VillagerEntityMCA p,UUID partner,String period,boolean recurring,boolean npc){
        var s=p.getHangoutState();clearAppointment(s);s.putString("Partner",partner.toString());s.putString("Period",period);
        s.putLong("Due",nextTime(p.level().getOverworldClockTime(),period));s.putBoolean("Accepted",true);s.putBoolean("Recurring",recurring);s.putBoolean("Npc",npc);
    }
    public static void cancelAppointment(VillagerEntityMCA p){
        clearAppointment(p.getHangoutState());p.getHangoutState().remove("Work");p.getHangoutState().remove("ReturnHome");
    }
    public static void cancelOrder(VillagerEntityMCA p){
        var s=p.getHangoutState();s.remove("Work");s.remove("ReturnHome");s.remove("Active");s.remove("Immediate");
        // Explicit Follow/Stay overrides today's appointment without penalizing the player.
        if(s.getBooleanOr("Accepted",false)){
            if(s.getBooleanOr("Recurring",false))s.putLong("Due",nextTime(p.level().getOverworldClockTime()+DAY/4,s.getStringOr("Period","morning")));
            else clearAppointment(s);
        }
    }
    private static void finish(VillagerEntityMCA p,boolean home){
        var s=p.getHangoutState();boolean recurring=s.getBooleanOr("Recurring",false);String period=s.getStringOr("Period","morning");
        s.remove("Work");s.remove("ReturnHome");s.remove("Active");s.remove("Arrived");s.remove("Attended");s.remove("Immediate");
        if(recurring)s.putLong("Due",nextTime(p.level().getOverworldClockTime()+DAY/4,period));else clearAppointment(s);
        p.getNavigation().stop();p.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        if(home)p.getResidency().getHome().filter(pos->pos.dimension().equals(p.level().dimension())).ifPresent(pos->{
            s.putLong("ReturnHome",pos.pos().asLong());s.putLong("ReturnUntil",p.level().getGameTime()+2400);p.moveTowards(pos.pos());
        });
    }
    public static int respond(ServerPlayer player,UUID sender,boolean yes){
        for(ServerLevel world:player.level().getServer().getAllLevels())if(world.getEntity(sender) instanceof VillagerEntityMCA p){
            var s=p.getHangoutState();long clock=world.getOverworldClockTime();
            if(!canAnswer(s,player.getUUID(),clock)){player.sendSystemMessage(Component.translatable("mca.hangout.expired"));return 0;}
            PlayerSaveData.get(player).finishInvitation("hangout",sender);
            s.remove("Pending");
            if(!yes){clearAppointment(s);player.sendSystemMessage(Component.translatable("mca.hangout.declined"));return 1;}
            s.putBoolean("Accepted",true);player.sendSystemMessage(Component.translatable("mca.hangout.accepted",p.getName(),Component.translatable("mca.hangout."+s.getStringOr("Period","morning")),spot(p).getX(),spot(p).getY(),spot(p).getZ()));return 1;
        }
        player.sendSystemMessage(Component.translatable("mca.hangout.expired"));return 0;
    }
    private static UUID partner(CompoundTag s){try{return UUID.fromString(s.getStringOr("Partner",""));}catch(IllegalArgumentException bad){return null;}}
    private static boolean excused(ServerPlayer player){return player==null||player.tickCount<1200||!player.isAlive()||player.isSpectator()||player.isSleeping()||player.hurtTime>0||player.isOnFire()||player.isInLava()
            ||player.getLastHurtByMob()!=null&&player.tickCount-player.getLastHurtByMobTimestamp()<1200;}
    static boolean canAnswer(CompoundTag state,UUID player,long clock){
        return state.getBooleanOr("Pending",false)&&state.getStringOr("Partner","").equals(player.toString())
                &&clock<=state.getLongOr("Due",0)+GRACE;
    }
    static boolean missedPenalty(boolean npc,boolean immediate,boolean arrived,ServerPlayer player,ServerLevel world){
        return !npc&&!immediate&&arrived&&!excused(player)&&player.level()==world;
    }
    public static void tick(VillagerEntityMCA p){
        if(!(p.level() instanceof ServerLevel world)||p.tickCount%10!=0)return;
        var s=p.getHangoutState();long now=world.getGameTime(),clock=world.getOverworldClockTime();
        long scheduled=s.getLongOr("Due",Long.MAX_VALUE);
        if(s.getBooleanOr("Accepted",false)&&clock>=scheduled&&clock<=scheduled+DURATION&&p.isSleeping())p.stopSleeping();
        if(!available(p)||p.getVillagerBrain().isPanicking()||p.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)||VillageWarfare.isFighting(p)){
            if(s.getBooleanOr("Active",false))finish(p,true);return;
        }
        if(s.contains("ReturnHome")){
            if(now>s.getLongOr("ReturnUntil",0)||p.blockPosition().distSqr(BlockPos.of(s.getLongOr("ReturnHome",0)))<=4)s.remove("ReturnHome");
            return;
        }
        if(s.contains("Work")){
            BlockPos work=BlockPos.of(s.getLongOr("Work",0));
            if(now>s.getLongOr("WorkUntil",0)||p.blockPosition().distSqr(work)<=4){s.remove("Work");return;}
            p.moveTowards(work,.6F);return;
        }
        UUID id=partner(s);long due=s.getLongOr("Due",Long.MAX_VALUE);
        if(s.getBooleanOr("Pending",false)){if(clock>due+GRACE)clearAppointment(s);return;}
        if(s.getBooleanOr("Accepted",false)&&id!=null){
            if(clock<due)return;
            if(!hasSpot(p)||!safe(p,spot(p))){finish(p,true);return;}
            if(p.getVillagerBrain().getMoveState()!=MoveState.MOVE){finish(p,false);return;}
            boolean npc=s.getBooleanOr("Npc",false);
            ServerPlayer player=npc?null:world.getServer().getPlayerList().getPlayer(id);
            var guest=npc?world.getEntity(id):player;
            if(guest!=null&&guest.level()==world&&guest.distanceToSqr(Vec3.atBottomCenterOf(spot(p)))<=64)s.putBoolean("Attended",true);
            if(clock>due+GRACE&&!s.getBooleanOr("Attended",false)){
                if(missedPenalty(npc,s.getBooleanOr("Immediate",false),s.getBooleanOr("Arrived",false),player,world)){
                    p.getVillagerBrain().getMemoriesForPlayer(player).modHearts(-5);p.sendChatMessage(player,"mca.hangout.missed");
                }
                finish(p,true);return;
            }
            if(clock>due+DURATION){finish(p,true);return;}
            if(!s.getBooleanOr("Active",false)){p.getVillagerBrain().abandonJob();s.putBoolean("Active",true);}
            if(p.distanceToSqr(Vec3.atBottomCenterOf(spot(p)))>4){
                if(p.tickCount%100==0){var path=p.getNavigation().createPath(spot(p),1);if(path==null||!path.canReach()){finish(p,true);return;}}
                p.moveTowards(spot(p),.6F);
            }else{
                s.putBoolean("Arrived",true);p.getNavigation().stop();p.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                p.getMoveControl().setWantedPosition(p.getX(),p.getY(),p.getZ(),0);p.setXxa(0);p.setZza(0);
                p.setDeltaMovement(0,p.getDeltaMovement().y,0);
                if(guest!=null&&guest.level()==world&&p.distanceToSqr(guest)<=100){
                    p.getLookControl().setLookAt(guest,30,30);
                    if(p.tickCount%200==0)p.sendChatToAllAround("mca.hangout.chat/"+(1+p.getRandom().nextInt(3)));
                }
            }
            return;
        }
        if(p.tickCount%200!=0||p.getVillagerBrain().getMoveState()!=MoveState.MOVE
                ||p.getVillagerBrain().getCurrentJob()!=com.ancientkira.mca.entity.ai.Chore.NONE)return;
        long day=Math.floorDiv(clock,DAY);if(s.getLongOr("InviteDay",Long.MIN_VALUE)==day)return;s.putLong("InviteDay",day);
        if(p.getRandom().nextFloat()>=.1F||!ensureSpot(p))return;
        String period=List.of("morning","afternoon","evening").get(p.getRandom().nextInt(3));
        for(ServerPlayer player:world.players())if(p.distanceToSqr(player)<=64*64&&p.getVillagerBrain().getMemoriesForPlayer(player).getHearts()>=15){
            long expiry=world.getServer().overworld().getGameTime()+nextTime(clock,period)-clock+GRACE;
            if(!PlayerSaveData.get(player).reserveInvitation("hangout",p.getUUID(),expiry))continue;
            schedule(p,player.getUUID(),period,false,false);s.remove("Accepted");s.putBoolean("Pending",true);
            Component yes=answer(p.getUUID(),true),no=answer(p.getUUID(),false);
            PlayerSaveData.get(player).sendLetter(Component.translatable("mca.hangout.letter",p.getName(),Component.translatable("mca.hangout."+period),spot(p).getX(),spot(p).getY(),spot(p).getZ()).append("\n\n").append(yes).append("   ").append(no));return;
        }
        var neighbors=world.getEntitiesOfClass(VillagerEntityMCA.class,p.getBoundingBox().inflate(32),other->other!=p&&available(other)&&other.getVillagerBrain().getMoveState()==MoveState.MOVE&&!other.getHangoutState().contains("Due")&&!other.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)&&other.getVillagerBrain().getCurrentJob()==com.ancientkira.mca.entity.ai.Chore.NONE);
        if(!neighbors.isEmpty()){
            var other=neighbors.get(p.getRandom().nextInt(neighbors.size()));BlockPos meeting=spot(p);
            schedule(p,other.getUUID(),period,false,true);schedule(other,p.getUUID(),period,false,true);
            for(var participant:List.of(p,other)){participant.getHangoutState().putLong("MeetingSpot",meeting.asLong());participant.getHangoutState().putString("MeetingDimension",world.dimension().identifier().toString());}
        }
    }
    private static Component answer(UUID id,boolean yes){return Component.translatable(yes?"gui.yes":"gui.no").withStyle(style->style.withUnderlined(true).withClickEvent(new ClickEvent.RunCommand("/mca hangout "+(yes?"yes ":"no ")+id)));}
}
