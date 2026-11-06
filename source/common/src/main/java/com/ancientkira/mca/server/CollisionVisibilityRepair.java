package com.ancientkira.mca.server;

import com.ancientkira.mca.util.CollisionRefreshTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;

public final class CollisionVisibilityRepair {
    private static final CollisionRefreshTracker TRACKER=new CollisionRefreshTracker();
    private CollisionVisibilityRepair() {}
    public static void tick(ServerPlayer player) {
        boolean collision=!player.isPassenger() && !player.isSpectator() && !player.getAbilities().flying
                && !player.isInWater() && !player.isInLava() && !player.onClimbable()
                && (player.onGround() || player.horizontalCollision || player.verticalCollision);
        if(!TRACKER.shouldRefresh(player,player.level().getGameTime(),player.getX(),player.getY(),player.getZ(),collision))return;
        BlockPos center=player.blockPosition();
        // Only authoritative state packets; never alter or remove a block, or load a chunk.
        for(BlockPos pos:BlockPos.betweenClosed(center.offset(-1,-1,-1),center.offset(1,2,1)))
            if(player.level().hasChunkAt(pos))player.connection.send(new ClientboundBlockUpdatePacket(player.level(),pos.immutable()));
    }
}
