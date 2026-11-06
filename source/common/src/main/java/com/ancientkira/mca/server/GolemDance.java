package com.ancientkira.mca.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.golem.IronGolem;
import com.ancientkira.mca.entity.ai.navigation.TerrainSafety;

public final class GolemDance {
    private record Order(BlockPos spot,long expires) {}
    private static final Map<IronGolem,Order> ORDERS=new WeakHashMap<>();
    private static final Map<IronGolem,Integer> DANCING=new WeakHashMap<>();
    private GolemDance() {}
    public static void event(IronGolem golem,byte event) {
        if(event==-124) DANCING.put(golem,golem.tickCount+25);
        else if(event==-125) DANCING.remove(golem);
    }
    public static boolean dancing(IronGolem golem) {return DANCING.getOrDefault(golem,0)>golem.tickCount;}
    public static boolean assigned(IronGolem golem) {return ORDERS.containsKey(golem);}
    public static boolean invite(ServerLevel world,IronGolem golem,BlockPos jukebox,long now) {
        Order order=ORDERS.get(golem);
        if(order!=null && now<=order.expires()+40) {ORDERS.put(golem,new Order(order.spot(),now+25));return false;}
        for(int attempt=0;attempt<12;attempt++) {
            int index=Math.floorMod(golem.getId()+attempt,12);
            double angle=index*Math.PI/6;
            BlockPos spot=jukebox.offset((int)Math.round(Math.cos(angle)*6),0,(int)Math.round(Math.sin(angle)*6));
            if(ORDERS.values().stream().anyMatch(other->other.spot().equals(spot)) || !TerrainSafety.solidFloor(world,spot)
                    || !TerrainSafety.clear(world,spot) || TerrainSafety.dangerousEdge(world,spot)
                    || !world.noBlockCollision(golem,golem.getBoundingBox().move(net.minecraft.world.phys.Vec3.atBottomCenterOf(spot).subtract(golem.position()))))continue;
            var path=golem.getNavigation().createPath(spot,1);
            if(path!=null && path.canReach()) ORDERS.put(golem,new Order(spot,now+25));
            return true; // At most one path search per invitation.
        }
        return false;
    }
    public static void tick(IronGolem golem) {
        if(!(golem.level() instanceof ServerLevel world))return;
        Order order=ORDERS.get(golem);
        if(order==null)return;
        if(world.getGameTime()>order.expires()) {ORDERS.remove(golem);event(golem,(byte)-125);world.broadcastEntityEvent(golem,(byte)-125);return;}
        if(VillageWarfare.isWarParticipant(golem) || golem.getTarget()!=null && golem.getTarget().isAlive() || golem.isNoAi() || golem.isVehicle()) {
            if(dancing(golem)){event(golem,(byte)-125);world.broadcastEntityEvent(golem,(byte)-125);}return;
        }
        BlockPos spot=order.spot();
        if(golem.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(spot))>2.25) {
            if(golem.getNavigation().isDone() && Math.floorMod(golem.tickCount+golem.getId(),20)==0)
                golem.getNavigation().moveTo(spot.getX()+.5,spot.getY(),spot.getZ()+.5,.8);
            return;
        }
        golem.getNavigation().stop();golem.getMoveControl().setWantedPosition(golem.getX(),golem.getY(),golem.getZ(),0);
        golem.setXxa(0);golem.setZza(0);golem.setDeltaMovement(0,golem.getDeltaMovement().y,0);
        if(golem.tickCount%20==0){event(golem,(byte)-124);world.broadcastEntityEvent(golem,(byte)-124);}
    }
}
