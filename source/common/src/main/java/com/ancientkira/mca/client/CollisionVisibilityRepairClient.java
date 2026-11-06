package com.ancientkira.mca.client;

import com.ancientkira.mca.util.CollisionRefreshTracker;
import net.minecraft.client.Minecraft;

public final class CollisionVisibilityRepairClient {
    private static final CollisionRefreshTracker TRACKER=new CollisionRefreshTracker();
    private CollisionVisibilityRepairClient() {}
    public static void tick(Minecraft client) {
        var player=client.player;
        if(player==null || client.level==null)return;
        boolean collision=!player.isPassenger() && !player.isSpectator() && !player.getAbilities().flying
                && !player.isInWater() && !player.isInLava() && !player.onClimbable()
                && (player.onGround() || player.horizontalCollision || player.verticalCollision);
        if(!TRACKER.shouldRefresh(player,client.level.getGameTime(),player.getX(),player.getY(),player.getZ(),collision))return;
        var pos=player.blockPosition();
        client.level.setSectionRangeDirty((pos.getX()-1)>>4,(pos.getY()-1)>>4,(pos.getZ()-1)>>4,
                (pos.getX()+1)>>4,(pos.getY()+2)>>4,(pos.getZ()+1)>>4);
    }
}
